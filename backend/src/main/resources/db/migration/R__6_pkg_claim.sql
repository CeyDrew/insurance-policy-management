-- Claim (hasar) lifecycle and its effect on the no-claims discount.
--
--   REPORTED --assign--> IN_REVIEW --approve--> APPROVED --pay--> PAID
--                                  \--reject--> REJECTED
--
-- Only the handler assigned to a claim (or an ADMIN) may approve, reject or pay it. Every status change creates a
-- CLAIM_STATUS_CHANGED notification for the customer; the audit trigger records the change itself.
-- Paying a vehicle claim lowers the policy's no-claims step (hasarsızlık basamağı) and re-prices open renewal quotes.

CREATE OR REPLACE PACKAGE pkg_claim AS

    /** 1 if the status change is allowed, 0 otherwise. */
    FUNCTION is_allowed_transition(p_from IN VARCHAR2, p_to IN VARCHAR2) RETURN NUMBER;

    /** REPORTED -> IN_REVIEW (or re-assignment while IN_REVIEW) to a CLAIM_HANDLER/ADMIN user. */
    PROCEDURE assign_claim(p_claim_id IN claim.id%TYPE, p_handler_id IN app_user.id%TYPE, p_changed_by IN VARCHAR2);

    /** IN_REVIEW -> APPROVED with the amount to be paid. */
    PROCEDURE approve_claim(
        p_claim_id        IN claim.id%TYPE,
        p_approved_amount IN NUMBER,
        p_note            IN VARCHAR2,
        p_changed_by      IN VARCHAR2
    );

    /** IN_REVIEW -> REJECTED; a reason is mandatory. */
    PROCEDURE reject_claim(p_claim_id IN claim.id%TYPE, p_note IN VARCHAR2, p_changed_by IN VARCHAR2);

    /** APPROVED -> PAID: pays the approved amount and applies the claim to the no-claims step. */
    PROCEDURE pay_claim(p_claim_id IN claim.id%TYPE, p_changed_by IN VARCHAR2, p_paid_amount OUT NUMBER);

    /**
     * Lowers the no-claims step of a TRAFFIC/CASCO policy by one (minimum 1) after a paid claim, and re-prices
     * renewal quotes of that policy that are still open, so the next term reflects the claim.
     */
    PROCEDURE apply_paid_claim_to_ncd(p_policy_id IN policy.id%TYPE, p_changed_by IN VARCHAR2);

END pkg_claim;
/

CREATE OR REPLACE PACKAGE BODY pkg_claim AS

    c_max_amount CONSTANT NUMBER := 100000000;

    FUNCTION status_label(p_status IN VARCHAR2) RETURN VARCHAR2 IS
    BEGIN
        RETURN CASE p_status
                   WHEN 'REPORTED'  THEN 'İhbar edildi'
                   WHEN 'IN_REVIEW' THEN 'İncelemede'
                   WHEN 'APPROVED'  THEN 'Onaylandı'
                   WHEN 'REJECTED'  THEN 'Reddedildi'
                   WHEN 'PAID'      THEN 'Ödendi'
                   ELSE p_status
               END;
    END status_label;

    FUNCTION is_allowed_transition(p_from IN VARCHAR2, p_to IN VARCHAR2) RETURN NUMBER IS
    BEGIN
        RETURN CASE p_from || '>' || p_to
                   WHEN 'REPORTED>IN_REVIEW'  THEN 1
                   WHEN 'IN_REVIEW>IN_REVIEW' THEN 1  -- re-assignment
                   WHEN 'IN_REVIEW>APPROVED'  THEN 1
                   WHEN 'IN_REVIEW>REJECTED'  THEN 1
                   WHEN 'APPROVED>PAID'       THEN 1
                   ELSE 0
               END;
    END is_allowed_transition;

    FUNCTION lock_claim(p_claim_id IN claim.id%TYPE) RETURN claim%ROWTYPE IS
        l_claim claim%ROWTYPE;
    BEGIN
        SELECT * INTO l_claim FROM claim WHERE id = p_claim_id FOR UPDATE;
        RETURN l_claim;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            pkg_errors.raise_error(pkg_errors.c_not_found, 'CLAIM_NOT_FOUND', 'Hasar bulunamadı: ' || p_claim_id);
    END lock_claim;

    PROCEDURE assert_transition(p_claim IN claim%ROWTYPE, p_to IN VARCHAR2) IS
    BEGIN
        IF is_allowed_transition(p_claim.status, p_to) = 0 THEN
            pkg_errors.raise_error(pkg_errors.c_invalid_state, 'INVALID_CLAIM_TRANSITION',
                p_claim.claim_number || ' numaralı hasar ' || status_label(p_claim.status) || ' durumundayken '
                || status_label(p_to) || ' yapılamaz.');
        END IF;
    END assert_transition;

    /** The assigned handler decides; an ADMIN may act on any claim. */
    PROCEDURE assert_decision_maker(p_claim IN claim%ROWTYPE, p_username IN VARCHAR2) IS
        l_role     app_user.role%TYPE;
        l_assigned app_user.username%TYPE;
    BEGIN
        BEGIN
            SELECT role INTO l_role FROM app_user WHERE username = p_username AND active = 1;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                pkg_errors.raise_error(pkg_errors.c_validation, 'INVALID_HANDLER', 'Geçersiz kullanıcı: ' || p_username);
        END;
        IF l_role = 'ADMIN' THEN
            RETURN;
        END IF;
        SELECT MAX(username) INTO l_assigned FROM app_user WHERE id = p_claim.handler_id;
        IF l_assigned IS NULL OR l_assigned <> p_username THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'NOT_ASSIGNED_HANDLER',
                'Bu hasar size atanmamış. Önce dosyayı üstlenin.');
        END IF;
    END assert_decision_maker;

    PROCEDURE notify_customer(p_claim IN claim%ROWTYPE, p_new_status IN VARCHAR2, p_changed_by IN VARCHAR2) IS
        -- Package-private functions cannot be called from SQL, so the texts are built here.
        l_subject notification.subject%TYPE := 'Hasar dosyanız: ' || status_label(p_new_status);
        l_body    notification.body%TYPE := p_claim.claim_number || ' numaralı hasar dosyanızın durumu "'
                                            || status_label(p_new_status) || '" olarak güncellendi.';
    BEGIN
        INSERT INTO notification (customer_id, policy_id, notification_type, channel, recipient, subject, body, created_by)
        SELECT c.id, p.id, 'CLAIM_STATUS_CHANGED',
               CASE WHEN c.email IS NOT NULL THEN 'EMAIL' ELSE 'SMS' END,
               NVL(c.email, c.phone),
               l_subject,
               l_body,
               p_changed_by
          FROM policy p
          JOIN customer c ON c.id = p.customer_id
         WHERE p.id = p_claim.policy_id
           AND NVL(c.email, c.phone) IS NOT NULL;
    END notify_customer;

    PROCEDURE assign_claim(p_claim_id IN claim.id%TYPE, p_handler_id IN app_user.id%TYPE, p_changed_by IN VARCHAR2) IS
        l_claim claim%ROWTYPE := lock_claim(p_claim_id);
        l_valid PLS_INTEGER;
    BEGIN
        assert_transition(l_claim, 'IN_REVIEW');
        SELECT COUNT(*) INTO l_valid
          FROM app_user
         WHERE id = p_handler_id AND active = 1 AND role IN ('CLAIM_HANDLER', 'ADMIN');
        IF l_valid = 0 THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'INVALID_HANDLER',
                'Hasar dosyası sadece aktif bir hasar uzmanına atanabilir.');
        END IF;

        UPDATE claim
           SET status = 'IN_REVIEW',
               handler_id = p_handler_id,
               updated_at = SYSTIMESTAMP,
               updated_by = p_changed_by,
               version = version + 1
         WHERE id = p_claim_id;

        IF l_claim.status = 'REPORTED' THEN
            notify_customer(l_claim, 'IN_REVIEW', p_changed_by);
        END IF;
    END assign_claim;

    PROCEDURE approve_claim(
        p_claim_id        IN claim.id%TYPE,
        p_approved_amount IN NUMBER,
        p_note            IN VARCHAR2,
        p_changed_by      IN VARCHAR2
    ) IS
        l_claim claim%ROWTYPE := lock_claim(p_claim_id);
    BEGIN
        assert_transition(l_claim, 'APPROVED');
        assert_decision_maker(l_claim, p_changed_by);
        IF p_approved_amount IS NULL OR p_approved_amount <= 0 OR p_approved_amount > c_max_amount THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'INVALID_APPROVED_AMOUNT',
                'Onaylanan tutar 0''dan büyük ve en fazla 100.000.000 olmalıdır.');
        END IF;

        UPDATE claim
           SET status = 'APPROVED',
               approved_amount = ROUND(p_approved_amount, 2),
               decision_note = TRIM(p_note),
               decided_at = SYSTIMESTAMP,
               updated_at = SYSTIMESTAMP,
               updated_by = p_changed_by,
               version = version + 1
         WHERE id = p_claim_id;

        notify_customer(l_claim, 'APPROVED', p_changed_by);
    END approve_claim;

    PROCEDURE reject_claim(p_claim_id IN claim.id%TYPE, p_note IN VARCHAR2, p_changed_by IN VARCHAR2) IS
        l_claim claim%ROWTYPE := lock_claim(p_claim_id);
    BEGIN
        assert_transition(l_claim, 'REJECTED');
        assert_decision_maker(l_claim, p_changed_by);
        IF TRIM(p_note) IS NULL THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'REJECTION_REASON_REQUIRED', 'Red gerekçesi zorunludur.');
        END IF;

        UPDATE claim
           SET status = 'REJECTED',
               decision_note = TRIM(p_note),
               decided_at = SYSTIMESTAMP,
               updated_at = SYSTIMESTAMP,
               updated_by = p_changed_by,
               version = version + 1
         WHERE id = p_claim_id;

        notify_customer(l_claim, 'REJECTED', p_changed_by);
    END reject_claim;

    PROCEDURE apply_paid_claim_to_ncd(p_policy_id IN policy.id%TYPE, p_changed_by IN VARCHAR2) IS
        l_product_code product.code%TYPE;
        l_next_step    NUMBER;
        l_net          NUMBER;
        l_tax          NUMBER;
        l_gross        NUMBER;
    BEGIN
        SELECT pr.code INTO l_product_code
          FROM policy p JOIN product pr ON pr.id = p.product_id
         WHERE p.id = p_policy_id;
        IF l_product_code NOT IN ('TRAFFIC', 'CASCO') THEN
            RETURN;
        END IF;

        UPDATE policy
           SET ncd_step = GREATEST(ncd_step - 1, 1),
               updated_at = SYSTIMESTAMP,
               updated_by = p_changed_by,
               version = version + 1
         WHERE id = p_policy_id;

        -- Renewal quotes priced before the claim are re-priced with the new step.
        l_next_step := pkg_policy.next_ncd_step(p_policy_id);
        FOR q IN (SELECT r.id, r.customer_id, r.vehicle_id, r.property_id, r.start_date
                    FROM policy r
                   WHERE r.renewed_from_policy_id = p_policy_id
                     AND r.status = 'QUOTE'
                     FOR UPDATE) LOOP
            pkg_premium.calculate(l_product_code, q.customer_id, q.vehicle_id, q.property_id, l_next_step,
                                  q.start_date, l_net, l_tax, l_gross);
            UPDATE policy
               SET ncd_step = l_next_step,
                   net_premium = l_net,
                   tax_amount = l_tax,
                   gross_premium = l_gross,
                   updated_at = SYSTIMESTAMP,
                   updated_by = p_changed_by,
                   version = version + 1
             WHERE id = q.id;
        END LOOP;
    END apply_paid_claim_to_ncd;

    PROCEDURE pay_claim(p_claim_id IN claim.id%TYPE, p_changed_by IN VARCHAR2, p_paid_amount OUT NUMBER) IS
        l_claim claim%ROWTYPE := lock_claim(p_claim_id);
    BEGIN
        assert_transition(l_claim, 'PAID');
        assert_decision_maker(l_claim, p_changed_by);

        p_paid_amount := l_claim.approved_amount;
        UPDATE claim
           SET status = 'PAID',
               paid_amount = p_paid_amount,
               paid_at = SYSTIMESTAMP,
               updated_at = SYSTIMESTAMP,
               updated_by = p_changed_by,
               version = version + 1
         WHERE id = p_claim_id;

        apply_paid_claim_to_ncd(l_claim.policy_id, p_changed_by);
        notify_customer(l_claim, 'PAID', p_changed_by);
    END pay_claim;

END pkg_claim;
/
