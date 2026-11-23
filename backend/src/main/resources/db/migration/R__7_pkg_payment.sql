-- Premium collection (tahsilat), simulated: recording a payment marks the installment PAID.
-- When the last overdue installment of a SUSPENDED policy is paid, the policy becomes ACTIVE again.

CREATE OR REPLACE PACKAGE pkg_payment AS

    /**
     * Pays one installment in full at the counter (agent). Installments must be paid in order (oldest unpaid first).
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

    /**
     * Records an amount the bank has already debited (otomatik ödeme talimatı). Unlike a counter payment the order
     * of installments is not enforced: the money is in, it must be applied. The caller (PKG_COLLECTION) has
     * checked that the installment is still open and locked it.
     */
    PROCEDURE collect_direct_debit(
        p_installment_id IN  installment.id%TYPE,
        p_reference      IN  VARCHAR2,
        p_paid_by        IN  VARCHAR2,
        p_payment_id     OUT NUMBER
    );

END pkg_payment;
/

CREATE OR REPLACE PACKAGE BODY pkg_payment AS

    PROCEDURE lock_installment(
        p_installment_id IN  installment.id%TYPE,
        p_installment    OUT installment%ROWTYPE,
        p_policy         OUT policy%ROWTYPE
    ) IS
    BEGIN
        BEGIN
            SELECT * INTO p_installment FROM installment WHERE id = p_installment_id FOR UPDATE;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                pkg_errors.raise_error(pkg_errors.c_not_found, 'INSTALLMENT_NOT_FOUND',
                    'Taksit bulunamadı: ' || p_installment_id);
        END;
        SELECT * INTO p_policy FROM policy WHERE id = p_installment.policy_id FOR UPDATE;

        IF p_installment.status = 'PAID' THEN
            pkg_errors.raise_error(pkg_errors.c_invalid_state, 'INSTALLMENT_ALREADY_PAID', 'Bu taksit zaten ödenmiş.');
        ELSIF p_installment.status = 'CANCELLED' THEN
            pkg_errors.raise_error(pkg_errors.c_invalid_state, 'INSTALLMENT_CANCELLED',
                'İptal edilmiş poliçenin taksiti tahsil edilemez.');
        END IF;
    END lock_installment;

    /** Marks the installment paid, records the payment, notifies the customer, lifts a suspension. */
    PROCEDURE apply_payment(
        p_installment    IN  installment%ROWTYPE,
        p_policy         IN  policy%ROWTYPE,
        p_payment_method IN  VARCHAR2,
        p_reference      IN  VARCHAR2,
        p_paid_by        IN  VARCHAR2,
        p_payment_id     OUT NUMBER,
        p_reactivated    OUT NUMBER
    ) IS
        l_overdue_left PLS_INTEGER;
    BEGIN
        p_reactivated := 0;
        UPDATE installment
           SET status = 'PAID',
               paid_at = SYSTIMESTAMP,
               collection_reject_code = NULL,
               collection_rejected_at = NULL,
               updated_at = SYSTIMESTAMP,
               updated_by = p_paid_by,
               version = version + 1
         WHERE id = p_installment.id;

        p_payment_id := payment_seq.NEXTVAL;
        INSERT INTO payment (id, installment_id, amount, payment_date, payment_method, reference_no, created_by)
        VALUES (p_payment_id, p_installment.id, p_installment.amount, SYSTIMESTAMP, p_payment_method,
                -- Own prefix: the seed data uses PAY-<installment id> references.
                NVL(p_reference, 'TAH-' || TO_CHAR(SYSDATE, 'YYYYMMDD') || '-' || LPAD(p_payment_id, 8, '0')),
                p_paid_by);

        INSERT INTO notification (customer_id, policy_id, notification_type, channel, recipient, subject, body, created_by)
        SELECT c.id, p_policy.id, 'PAYMENT_RECEIVED',
               CASE WHEN c.email IS NOT NULL THEN 'EMAIL' ELSE 'SMS' END, NVL(c.email, c.phone),
               'Ödemeniz alındı',
               p_policy.policy_number || ' numaralı poliçenizin ' || p_installment.installment_no || '. taksidi ('
                   || TO_CHAR(p_installment.amount, 'FM999G999G990D00', 'NLS_NUMERIC_CHARACTERS='',.''')
                   || ' TL) tahsil edilmiştir.',
               p_paid_by
          FROM customer c
         WHERE c.id = p_policy.customer_id
           AND NVL(c.email, c.phone) IS NOT NULL;

        IF p_policy.status = 'SUSPENDED' THEN
            SELECT COUNT(*) INTO l_overdue_left
              FROM installment
             WHERE policy_id = p_policy.id AND status = 'OVERDUE';
            IF l_overdue_left = 0 THEN
                pkg_policy.change_status(p_policy.id, 'ACTIVE', p_paid_by);
                p_reactivated := 1;
            END IF;
        END IF;
    END apply_payment;

    PROCEDURE pay_installment(
        p_installment_id IN  installment.id%TYPE,
        p_payment_method IN  VARCHAR2,
        p_paid_by        IN  VARCHAR2,
        p_payment_id     OUT NUMBER,
        p_reactivated    OUT NUMBER
    ) IS
        l_installment installment%ROWTYPE;
        l_policy      policy%ROWTYPE;
        l_earlier     PLS_INTEGER;
    BEGIN
        lock_installment(p_installment_id, l_installment, l_policy);

        -- DIRECT_DEBIT only arrives through bank result files (collect_direct_debit).
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

        apply_payment(l_installment, l_policy, p_payment_method, NULL, p_paid_by, p_payment_id, p_reactivated);
    END pay_installment;

    PROCEDURE collect_direct_debit(
        p_installment_id IN  installment.id%TYPE,
        p_reference      IN  VARCHAR2,
        p_paid_by        IN  VARCHAR2,
        p_payment_id     OUT NUMBER
    ) IS
        l_installment installment%ROWTYPE;
        l_policy      policy%ROWTYPE;
        l_reactivated NUMBER;
    BEGIN
        lock_installment(p_installment_id, l_installment, l_policy);
        apply_payment(l_installment, l_policy, 'DIRECT_DEBIT', p_reference, p_paid_by, p_payment_id, l_reactivated);
    END collect_direct_debit;

END pkg_payment;
/
