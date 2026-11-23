-- Policy lifecycle: status transitions, issuing quotes and cancellation with day-based refund.
--
-- Allowed transitions:
--   QUOTE     -> ACTIVE                                   (issue_policy)
--   ACTIVE    -> SUSPENDED | CANCELLED | EXPIRED | RENEWED
--   SUSPENDED -> ACTIVE | CANCELLED | EXPIRED
-- CANCELLED, EXPIRED and RENEWED are final.
-- Procedures lock the policy row (SELECT ... FOR UPDATE) so concurrent changes are serialized.

CREATE OR REPLACE PACKAGE pkg_policy AS

    /** 1 if the status change is allowed, 0 otherwise (NUMBER so it can be used from SQL). */
    FUNCTION is_allowed_transition(p_from IN VARCHAR2, p_to IN VARCHAR2) RETURN NUMBER;

    /** Generic status change for batch jobs (suspend, expire, renew, reactivate). */
    PROCEDURE change_status(
        p_policy_id  IN policy.id%TYPE,
        p_new_status IN policy.status%TYPE,
        p_changed_by IN VARCHAR2
    );

    /** QUOTE -> ACTIVE: validates the quote, sets issued_at and creates the installment plan. */
    PROCEDURE issue_policy(p_policy_id IN policy.id%TYPE, p_issued_by IN VARCHAR2);

    /**
     * Day-based (gün esaslı) refund: gross premium x unused days / term days.
     * Cancelling before the start date refunds everything, after the end date nothing.
     */
    FUNCTION calculate_refund(p_policy_id IN policy.id%TYPE, p_cancel_date IN DATE DEFAULT TRUNC(SYSDATE))
        RETURN NUMBER;

    /** ACTIVE/SUSPENDED -> CANCELLED, stores the refund and cancels unpaid installments. */
    PROCEDURE cancel_policy(
        p_policy_id     IN  policy.id%TYPE,
        p_reason        IN  VARCHAR2,
        p_cancel_date   IN  DATE,
        p_cancelled_by  IN  VARCHAR2,
        p_refund_amount OUT NUMBER
    );

END pkg_policy;
/

CREATE OR REPLACE PACKAGE BODY pkg_policy AS

    FUNCTION status_label(p_status IN VARCHAR2) RETURN VARCHAR2 IS
    BEGIN
        RETURN CASE p_status
                   WHEN 'QUOTE'     THEN 'Teklif'
                   WHEN 'ACTIVE'    THEN 'Yürürlükte'
                   WHEN 'SUSPENDED' THEN 'Askıda'
                   WHEN 'CANCELLED' THEN 'İptal'
                   WHEN 'EXPIRED'   THEN 'Süresi doldu'
                   WHEN 'RENEWED'   THEN 'Yenilendi'
                   ELSE p_status
               END;
    END status_label;

    FUNCTION lock_policy(p_policy_id IN policy.id%TYPE) RETURN policy%ROWTYPE IS
        l_policy policy%ROWTYPE;
    BEGIN
        SELECT * INTO l_policy FROM policy WHERE id = p_policy_id FOR UPDATE;
        RETURN l_policy;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            pkg_errors.raise_error(pkg_errors.c_not_found, 'POLICY_NOT_FOUND', 'Poliçe bulunamadı: ' || p_policy_id);
    END lock_policy;

    FUNCTION is_allowed_transition(p_from IN VARCHAR2, p_to IN VARCHAR2) RETURN NUMBER IS
    BEGIN
        RETURN CASE p_from || '>' || p_to
                   WHEN 'QUOTE>ACTIVE'        THEN 1
                   WHEN 'ACTIVE>SUSPENDED'    THEN 1
                   WHEN 'ACTIVE>CANCELLED'    THEN 1
                   WHEN 'ACTIVE>EXPIRED'      THEN 1
                   WHEN 'ACTIVE>RENEWED'      THEN 1
                   WHEN 'SUSPENDED>ACTIVE'    THEN 1
                   WHEN 'SUSPENDED>CANCELLED' THEN 1
                   WHEN 'SUSPENDED>EXPIRED'   THEN 1
                   ELSE 0
               END;
    END is_allowed_transition;

    PROCEDURE assert_transition(p_policy IN policy%ROWTYPE, p_to IN VARCHAR2) IS
    BEGIN
        IF is_allowed_transition(p_policy.status, p_to) = 0 THEN
            pkg_errors.raise_error(pkg_errors.c_invalid_state, 'INVALID_STATUS_TRANSITION',
                p_policy.policy_number || ' numaralı poliçenin durumu ' || status_label(p_policy.status)
                || ' iken ' || status_label(p_to) || ' yapılamaz.');
        END IF;
    END assert_transition;

    PROCEDURE set_status(p_policy_id IN policy.id%TYPE, p_status IN VARCHAR2, p_changed_by IN VARCHAR2) IS
    BEGIN
        UPDATE policy
           SET status = p_status,
               updated_at = SYSTIMESTAMP,
               updated_by = p_changed_by,
               version = version + 1
         WHERE id = p_policy_id;
    END set_status;

    PROCEDURE change_status(
        p_policy_id  IN policy.id%TYPE,
        p_new_status IN policy.status%TYPE,
        p_changed_by IN VARCHAR2
    ) IS
        l_policy policy%ROWTYPE := lock_policy(p_policy_id);
    BEGIN
        assert_transition(l_policy, p_new_status);
        set_status(p_policy_id, p_new_status, p_changed_by);
    END change_status;

    PROCEDURE issue_policy(p_policy_id IN policy.id%TYPE, p_issued_by IN VARCHAR2) IS
        l_policy policy%ROWTYPE := lock_policy(p_policy_id);
        l_today  DATE := TRUNC(SYSDATE);
        l_in_force PLS_INTEGER;
    BEGIN
        IF l_policy.status <> 'QUOTE' THEN
            pkg_errors.raise_error(pkg_errors.c_invalid_state, 'POLICY_NOT_QUOTE',
                'Sadece teklif durumundaki kayıtlar poliçeleştirilebilir.');
        END IF;
        IF l_policy.quote_valid_until < l_today THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'QUOTE_EXPIRED',
                'Teklifin geçerlilik süresi dolmuş. Yeni teklif alın.');
        END IF;
        IF l_policy.start_date < l_today THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'QUOTE_START_DATE_PASSED',
                'Teklifin başlangıç tarihi geçmiş. Yeni teklif alın.');
        END IF;

        -- Another quote on the same insured object may have been issued since this one was priced.
        SELECT COUNT(*)
          INTO l_in_force
          FROM policy p
         WHERE p.id <> l_policy.id
           AND p.product_id = l_policy.product_id
           AND p.status IN ('ACTIVE', 'SUSPENDED')
           AND p.end_date > l_policy.start_date
           AND ((l_policy.vehicle_id IS NOT NULL AND p.vehicle_id = l_policy.vehicle_id)
             OR (l_policy.property_id IS NOT NULL AND p.property_id = l_policy.property_id)
             OR (l_policy.vehicle_id IS NULL AND l_policy.property_id IS NULL AND p.customer_id = l_policy.customer_id));
        IF l_in_force > 0 THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'POLICY_ALREADY_IN_FORCE',
                'Bu sigortalı için başlangıç tarihinde yürürlükte bir poliçe var.');
        END IF;

        UPDATE policy
           SET status = 'ACTIVE',
               issued_at = SYSTIMESTAMP,
               updated_at = SYSTIMESTAMP,
               updated_by = p_issued_by,
               version = version + 1
         WHERE id = p_policy_id;

        pkg_premium.generate_installments(p_policy_id, NULL, p_issued_by);
    END issue_policy;

    FUNCTION calculate_refund(p_policy_id IN policy.id%TYPE, p_cancel_date IN DATE DEFAULT TRUNC(SYSDATE))
        RETURN NUMBER IS
        l_gross  policy.gross_premium%TYPE;
        l_start  DATE;
        l_end    DATE;
        l_unused NUMBER;
    BEGIN
        BEGIN
            SELECT gross_premium, start_date, end_date
              INTO l_gross, l_start, l_end
              FROM policy
             WHERE id = p_policy_id;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                pkg_errors.raise_error(pkg_errors.c_not_found, 'POLICY_NOT_FOUND', 'Poliçe bulunamadı: ' || p_policy_id);
        END;
        l_unused := GREATEST(l_end - GREATEST(TRUNC(p_cancel_date), l_start), 0);
        RETURN ROUND(l_gross * l_unused / (l_end - l_start), 2);
    END calculate_refund;

    PROCEDURE cancel_policy(
        p_policy_id     IN  policy.id%TYPE,
        p_reason        IN  VARCHAR2,
        p_cancel_date   IN  DATE,
        p_cancelled_by  IN  VARCHAR2,
        p_refund_amount OUT NUMBER
    ) IS
        l_policy policy%ROWTYPE := lock_policy(p_policy_id);
        l_date   DATE := TRUNC(NVL(p_cancel_date, SYSDATE));
    BEGIN
        IF l_policy.status NOT IN ('ACTIVE', 'SUSPENDED') THEN
            pkg_errors.raise_error(pkg_errors.c_invalid_state, 'POLICY_NOT_CANCELLABLE',
                'Sadece yürürlükteki veya askıdaki poliçeler iptal edilebilir.');
        END IF;
        IF TRIM(p_reason) IS NULL THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'CANCEL_REASON_REQUIRED', 'İptal nedeni zorunludur.');
        END IF;
        IF l_date > l_policy.end_date THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'INVALID_CANCEL_DATE',
                'İptal tarihi poliçe bitiş tarihinden sonra olamaz.');
        END IF;

        p_refund_amount := calculate_refund(p_policy_id, l_date);

        UPDATE policy
           SET status = 'CANCELLED',
               cancelled_at = CASE WHEN l_date = TRUNC(SYSDATE) THEN SYSTIMESTAMP ELSE CAST(l_date AS TIMESTAMP) END,
               cancel_reason = TRIM(p_reason),
               refund_amount = p_refund_amount,
               updated_at = SYSTIMESTAMP,
               updated_by = p_cancelled_by,
               version = version + 1
         WHERE id = p_policy_id;

        UPDATE installment
           SET status = 'CANCELLED',
               updated_at = SYSTIMESTAMP,
               updated_by = p_cancelled_by,
               version = version + 1
         WHERE policy_id = p_policy_id
           AND status IN ('PENDING', 'OVERDUE');
    END cancel_policy;

END pkg_policy;
/
