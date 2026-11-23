-- Applies bank collection results (docs/bank-file-format.md). collectionResultImportJob hands over one chunk of
-- detail lines per call as a JSON array; everything a chunk changes commits or rolls back together with the job's
-- progress.

CREATE OR REPLACE PACKAGE pkg_collection AS

    c_collected CONSTANT VARCHAR2(2) := '00';

    /**
     * Applies result lines of an inbound collection file:
     *   00          -> installment paid by DIRECT_DEBIT (PKG_PAYMENT), outcome COLLECTED
     *   51/54/57/91 -> reject code noted on the installment, customer notified, outcome REJECTED;
     *                  the installment goes into the next request file again
     *   anything else, unknown installment, mismatching policy/installment no/amount, already paid or
     *   cancelled installment -> outcome ERROR, nothing changes on the installment
     * A line already applied for this file (restart) is ignored, so a line never pays twice.
     *
     * @param p_lines JSON array: [{lineNo, installmentId, policyNumber, installmentNo, amount, resultCode}, ...]
     */
    PROCEDURE apply_results(
        p_file_id    IN collection_file.id%TYPE,
        p_lines      IN CLOB,
        p_applied_by IN VARCHAR2
    );

    /** Counts the applied lines into the file's totals and marks it COMPLETED. */
    PROCEDURE complete_file(p_file_id IN collection_file.id%TYPE);

    /** Turkish description of a bank result code; NULL for an unknown code. */
    FUNCTION result_description(p_code IN VARCHAR2) RETURN VARCHAR2 DETERMINISTIC;

END pkg_collection;
/

CREATE OR REPLACE PACKAGE BODY pkg_collection AS

    FUNCTION result_description(p_code IN VARCHAR2) RETURN VARCHAR2 DETERMINISTIC IS
    BEGIN
        RETURN CASE p_code
                   WHEN '00' THEN 'Tahsil edildi'
                   WHEN '51' THEN 'Yetersiz bakiye'
                   WHEN '54' THEN 'Hesap kapalı'
                   WHEN '57' THEN 'Talimat bulunamadı'
                   WHEN '91' THEN 'Banka sistem hatası'
               END;
    END result_description;

    PROCEDURE notify_rejection(p_installment IN installment%ROWTYPE, p_policy IN policy%ROWTYPE,
                               p_code IN VARCHAR2, p_by IN VARCHAR2) IS
    BEGIN
        INSERT INTO notification (customer_id, policy_id, notification_type, channel, recipient, subject, body, created_by)
        SELECT c.id, p_policy.id, 'COLLECTION_REJECTED',
               CASE WHEN c.email IS NOT NULL THEN 'EMAIL' ELSE 'SMS' END, NVL(c.email, c.phone),
               'Otomatik ödeme gerçekleşmedi',
               p_policy.policy_number || ' numaralı poliçenizin ' || p_installment.installment_no || '. taksidi ('
                   || TO_CHAR(p_installment.amount, 'FM999G999G990D00', 'NLS_NUMERIC_CHARACTERS='',.''')
                   || ' TL) hesabınızdan tahsil edilemedi: ' || result_description(p_code)
                   || CASE WHEN p_code = '91' THEN '. Tahsilat tekrar denenecek.'
                           ELSE '. Lütfen hesabınızı kontrol edin veya acentenizle görüşün.' END,
               p_by
          FROM customer c
         WHERE c.id = p_policy.customer_id
           AND NVL(c.email, c.phone) IS NOT NULL;
    END notify_rejection;

    PROCEDURE apply_results(
        p_file_id    IN collection_file.id%TYPE,
        p_lines      IN CLOB,
        p_applied_by IN VARCHAR2
    ) IS
        l_lines       t_bank_result_tab;
        l_line        t_bank_result;
        l_installment installment%ROWTYPE;
        l_policy      policy%ROWTYPE;
        l_outcome     collection_result.outcome%TYPE;
        l_message     collection_result.message%TYPE;
        l_payment_id  payment.id%TYPE;
        l_exists      PLS_INTEGER;
        l_found       PLS_INTEGER;
    BEGIN
        SELECT t_bank_result(line_no, installment_id, policy_number, installment_no, amount, result_code)
          BULK COLLECT INTO l_lines
          FROM JSON_TABLE(p_lines, '$[*]' COLUMNS (
                   line_no        NUMBER(10)   PATH '$.lineNo',
                   installment_id NUMBER(19)   PATH '$.installmentId',
                   policy_number  VARCHAR2(30) PATH '$.policyNumber',
                   installment_no NUMBER(2)    PATH '$.installmentNo',
                   amount         NUMBER(15, 2) PATH '$.amount',
                   result_code    VARCHAR2(2)  PATH '$.resultCode'))
         ORDER BY line_no;

        FOR i IN 1 .. l_lines.COUNT LOOP
            l_line := l_lines(i);
            l_outcome := NULL;
            l_message := NULL;
            l_payment_id := NULL;
            l_found := 1;

            SELECT COUNT(*) INTO l_exists
              FROM collection_result
             WHERE collection_file_id = p_file_id AND line_no = l_line.line_no;
            CONTINUE WHEN l_exists > 0;

            BEGIN
                SELECT * INTO l_installment FROM installment WHERE id = l_line.installment_id FOR UPDATE;
                SELECT * INTO l_policy FROM policy WHERE id = l_installment.policy_id;
            EXCEPTION
                WHEN NO_DATA_FOUND THEN
                    l_found := 0;
                    l_outcome := 'ERROR';
                    l_message := 'Taksit bulunamadı.';
            END;

            IF l_outcome IS NULL THEN
                IF l_policy.policy_number <> l_line.policy_number
                   OR l_installment.installment_no <> l_line.installment_no THEN
                    l_outcome := 'ERROR';
                    l_message := 'Poliçe veya taksit numarası taksit referansıyla uyuşmuyor.';
                ELSIF l_installment.amount <> l_line.amount THEN
                    l_outcome := 'ERROR';
                    l_message := 'Tutar uyuşmuyor: beklenen '
                        || TO_CHAR(l_installment.amount, 'FM999G999G990D00', 'NLS_NUMERIC_CHARACTERS='',.''') || ' TL.';
                ELSIF result_description(l_line.result_code) IS NULL THEN
                    l_outcome := 'ERROR';
                    l_message := 'Geçersiz sonuç kodu: ' || NVL(l_line.result_code, '(boş)');
                ELSIF l_line.result_code = c_collected THEN
                    IF l_installment.status = 'PAID' THEN
                        l_outcome := 'ERROR';
                        l_message := 'Taksit daha önce ödenmiş; banka tahsilatı iade edilmeli.';
                    ELSIF l_installment.status = 'CANCELLED' THEN
                        l_outcome := 'ERROR';
                        l_message := 'Poliçe iptal edilmiş; banka tahsilatı iade edilmeli.';
                    ELSE
                        pkg_payment.collect_direct_debit(l_installment.id,
                            'OTO-' || LPAD(p_file_id, 6, '0') || '-' || LPAD(l_line.line_no, 8, '0'),
                            p_applied_by, l_payment_id);
                        l_outcome := 'COLLECTED';
                    END IF;
                ELSE
                    l_outcome := 'REJECTED';
                    l_message := result_description(l_line.result_code);
                    IF l_installment.status IN ('PENDING', 'OVERDUE') THEN
                        UPDATE installment
                           SET collection_reject_code = l_line.result_code,
                               collection_rejected_at = SYSTIMESTAMP,
                               -- requested again in the next file
                               collection_request_file_id = NULL,
                               updated_at = SYSTIMESTAMP,
                               updated_by = p_applied_by,
                               version = version + 1
                         WHERE id = l_installment.id;
                        notify_rejection(l_installment, l_policy, l_line.result_code, p_applied_by);
                    END IF;
                END IF;
            END IF;

            INSERT INTO collection_result (collection_file_id, line_no, installment_id, policy_number, installment_no,
                                           amount, result_code, outcome, payment_id, message)
            VALUES (p_file_id, l_line.line_no, CASE WHEN l_found = 1 THEN l_line.installment_id END,
                    l_line.policy_number, l_line.installment_no, l_line.amount, l_line.result_code, l_outcome,
                    l_payment_id, l_message);
        END LOOP;
    END apply_results;

    PROCEDURE complete_file(p_file_id IN collection_file.id%TYPE) IS
    BEGIN
        UPDATE collection_file f
           SET (collected_count, collected_amount, rejected_count, error_count) = (
                   SELECT COUNT(CASE WHEN outcome = 'COLLECTED' THEN 1 END),
                          NVL(SUM(CASE WHEN outcome = 'COLLECTED' THEN amount END), 0),
                          COUNT(CASE WHEN outcome = 'REJECTED' THEN 1 END),
                          COUNT(CASE WHEN outcome = 'ERROR' THEN 1 END)
                     FROM collection_result r
                    WHERE r.collection_file_id = f.id),
               status = 'COMPLETED',
               completed_at = SYSTIMESTAMP
         WHERE id = p_file_id;
    END complete_file;

END pkg_collection;
/
