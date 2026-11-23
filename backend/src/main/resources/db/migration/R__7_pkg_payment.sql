-- Premium collection (tahsilat), simulated: recording a payment marks the installment PAID.
-- When the last overdue installment of a SUSPENDED policy is paid, the policy becomes ACTIVE again.

CREATE OR REPLACE PACKAGE pkg_payment AS

    /**
     * Pays one installment in full. Installments must be paid in order (oldest unpaid first).
     *
     * @param p_reactivated 1 if the payment lifted the policy's suspension, otherwise 0
     */
    PROCEDURE pay_installment(
        p_installment_id IN  installment.id%TYPE,
        p_payment_method IN  VARCHAR2,
        p_paid_by        IN  VARCHAR2,
        p_payment_id     OUT NUMBER,
        p_reactivated    OUT NUMBER
    );

END pkg_payment;
/

CREATE OR REPLACE PACKAGE BODY pkg_payment AS

    PROCEDURE pay_installment(
        p_installment_id IN  installment.id%TYPE,
        p_payment_method IN  VARCHAR2,
        p_paid_by        IN  VARCHAR2,
        p_payment_id     OUT NUMBER,
        p_reactivated    OUT NUMBER
    ) IS
        l_installment  installment%ROWTYPE;
        l_policy       policy%ROWTYPE;
        l_earlier      PLS_INTEGER;
        l_overdue_left PLS_INTEGER;
    BEGIN
        p_reactivated := 0;
        BEGIN
            SELECT * INTO l_installment FROM installment WHERE id = p_installment_id FOR UPDATE;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                pkg_errors.raise_error(pkg_errors.c_not_found, 'INSTALLMENT_NOT_FOUND',
                    'Taksit bulunamadı: ' || p_installment_id);
        END;
        SELECT * INTO l_policy FROM policy WHERE id = l_installment.policy_id FOR UPDATE;

        IF l_installment.status = 'PAID' THEN
            pkg_errors.raise_error(pkg_errors.c_invalid_state, 'INSTALLMENT_ALREADY_PAID', 'Bu taksit zaten ödenmiş.');
        ELSIF l_installment.status = 'CANCELLED' THEN
            pkg_errors.raise_error(pkg_errors.c_invalid_state, 'INSTALLMENT_CANCELLED',
                'İptal edilmiş poliçenin taksiti tahsil edilemez.');
        END IF;
        IF p_payment_method NOT IN ('CREDIT_CARD', 'BANK_TRANSFER', 'CASH') OR p_payment_method IS NULL THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'INVALID_PAYMENT_METHOD', 'Geçersiz ödeme yöntemi.');
        END IF;

        SELECT COUNT(*) INTO l_earlier
          FROM installment
         WHERE policy_id = l_installment.policy_id
           AND installment_no < l_installment.installment_no
           AND status IN ('PENDING', 'OVERDUE');
        IF l_earlier > 0 THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'EARLIER_INSTALLMENT_UNPAID',
                'Önce daha eski vadeli ödenmemiş taksitler tahsil edilmelidir.');
        END IF;

        UPDATE installment
           SET status = 'PAID',
               paid_at = SYSTIMESTAMP,
               updated_at = SYSTIMESTAMP,
               updated_by = p_paid_by,
               version = version + 1
         WHERE id = p_installment_id;

        p_payment_id := payment_seq.NEXTVAL;
        INSERT INTO payment (id, installment_id, amount, payment_date, payment_method, reference_no, created_by)
        VALUES (p_payment_id, p_installment_id, l_installment.amount, SYSTIMESTAMP, p_payment_method,
                -- Own prefix: the seed data uses PAY-<installment id> references.
                'TAH-' || TO_CHAR(SYSDATE, 'YYYYMMDD') || '-' || LPAD(p_payment_id, 8, '0'), p_paid_by);

        INSERT INTO notification (customer_id, policy_id, notification_type, channel, recipient, subject, body, created_by)
        SELECT c.id, l_policy.id, 'PAYMENT_RECEIVED',
               CASE WHEN c.email IS NOT NULL THEN 'EMAIL' ELSE 'SMS' END, NVL(c.email, c.phone),
               'Ödemeniz alındı',
               l_policy.policy_number || ' numaralı poliçenizin ' || l_installment.installment_no || '. taksidi ('
                   || TO_CHAR(l_installment.amount, 'FM999G999G990D00', 'NLS_NUMERIC_CHARACTERS='',.''')
                   || ' TL) tahsil edilmiştir.',
               p_paid_by
          FROM customer c
         WHERE c.id = l_policy.customer_id
           AND NVL(c.email, c.phone) IS NOT NULL;

        IF l_policy.status = 'SUSPENDED' THEN
            SELECT COUNT(*) INTO l_overdue_left
              FROM installment
             WHERE policy_id = l_policy.id AND status = 'OVERDUE';
            IF l_overdue_left = 0 THEN
                pkg_policy.change_status(l_policy.id, 'ACTIVE', p_paid_by);
                p_reactivated := 1;
            END IF;
        END IF;
    END pay_installment;

END pkg_payment;
/
