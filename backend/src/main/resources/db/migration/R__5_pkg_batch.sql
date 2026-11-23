-- Set-based PL/SQL versions of batch work, used for large volumes and compared with the
-- chunk-oriented Spring Batch jobs in docs/batch-jobs.md.

CREATE OR REPLACE PACKAGE pkg_batch AS

    /**
     * Marks PENDING installments due before p_run_date as OVERDUE and creates INSTALLMENT_OVERDUE notifications,
     * then suspends ACTIVE policies with at least p_suspend_threshold overdue installments.
     * Works in slices of p_limit rows with BULK COLLECT ... LIMIT and FORALL, committing after each slice so
     * undo stays small on large tables.
     */
    PROCEDURE mark_overdue_installments(
        p_run_date          IN  DATE,
        p_changed_by        IN  VARCHAR2,
        p_suspend_threshold IN  PLS_INTEGER DEFAULT 2,
        p_limit             IN  PLS_INTEGER DEFAULT 5000,
        p_marked            OUT NUMBER,
        p_suspended         OUT NUMBER
    );

END pkg_batch;
/

CREATE OR REPLACE PACKAGE BODY pkg_batch AS

    TYPE t_ids     IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
    TYPE t_strings IS TABLE OF VARCHAR2(4000) INDEX BY PLS_INTEGER;

    PROCEDURE mark_overdue_installments(
        p_run_date          IN  DATE,
        p_changed_by        IN  VARCHAR2,
        p_suspend_threshold IN  PLS_INTEGER DEFAULT 2,
        p_limit             IN  PLS_INTEGER DEFAULT 5000,
        p_marked            OUT NUMBER,
        p_suspended         OUT NUMBER
    ) IS
        CURSOR c_overdue IS
            SELECT i.id, p.id, p.customer_id,
                   NVL(c.email, c.phone),
                   CASE WHEN c.email IS NOT NULL THEN 'EMAIL' ELSE 'SMS' END,
                   p.policy_number || ' numaralı poliçenizin ' || i.installment_no || '. taksidi ('
                       || TO_CHAR(i.amount, 'FM999G999G990D00', 'NLS_NUMERIC_CHARACTERS='',.''') || ' TL, vade '
                       || TO_CHAR(i.due_date, 'DD.MM.YYYY') || ') ödenmemiştir.'
              FROM installment i
              JOIN policy p ON p.id = i.policy_id
              JOIN customer c ON c.id = p.customer_id
             WHERE i.status = 'PENDING'
               AND i.due_date < TRUNC(p_run_date)
               AND p.status IN ('ACTIVE', 'SUSPENDED');

        CURSOR c_to_suspend IS
            SELECT p.id, p.customer_id, NVL(c.email, c.phone),
                   CASE WHEN c.email IS NOT NULL THEN 'EMAIL' ELSE 'SMS' END,
                   p.policy_number || ' numaralı poliçeniz ödenmemiş taksitler nedeniyle askıya alınmıştır.'
              FROM policy p
              JOIN customer c ON c.id = p.customer_id
             WHERE p.status = 'ACTIVE'
               AND (SELECT COUNT(*) FROM installment i
                     WHERE i.policy_id = p.id AND i.status = 'OVERDUE') >= p_suspend_threshold;

        l_installment_ids t_ids;
        l_policy_ids      t_ids;
        l_customer_ids    t_ids;
        l_recipients      t_strings;
        l_channels        t_strings;
        l_bodies          t_strings;
    BEGIN
        p_marked := 0;
        p_suspended := 0;

        OPEN c_overdue;
        LOOP
            FETCH c_overdue BULK COLLECT
                INTO l_installment_ids, l_policy_ids, l_customer_ids, l_recipients, l_channels, l_bodies
                LIMIT p_limit;
            EXIT WHEN l_installment_ids.COUNT = 0;

            FORALL i IN 1 .. l_installment_ids.COUNT
                UPDATE installment
                   SET status = 'OVERDUE',
                       updated_at = SYSTIMESTAMP,
                       updated_by = p_changed_by,
                       version = version + 1
                 WHERE id = l_installment_ids(i)
                   AND status = 'PENDING';

            -- Customers without e-mail or phone cannot be notified; their installments are still marked.
            FORALL i IN 1 .. l_installment_ids.COUNT
                INSERT INTO notification (customer_id, policy_id, notification_type, channel, recipient,
                                          subject, body, created_by)
                SELECT l_customer_ids(i), l_policy_ids(i), 'INSTALLMENT_OVERDUE', l_channels(i), l_recipients(i),
                       'Gecikmiş taksit', l_bodies(i), p_changed_by
                  FROM dual
                 WHERE l_recipients(i) IS NOT NULL;

            p_marked := p_marked + l_installment_ids.COUNT;
            COMMIT;
        END LOOP;
        CLOSE c_overdue;

        OPEN c_to_suspend;
        LOOP
            FETCH c_to_suspend BULK COLLECT
                INTO l_policy_ids, l_customer_ids, l_recipients, l_channels, l_bodies
                LIMIT p_limit;
            EXIT WHEN l_policy_ids.COUNT = 0;

            -- ACTIVE -> SUSPENDED is an allowed transition (pkg_policy.is_allowed_transition).
            FORALL i IN 1 .. l_policy_ids.COUNT
                UPDATE policy
                   SET status = 'SUSPENDED',
                       updated_at = SYSTIMESTAMP,
                       updated_by = p_changed_by,
                       version = version + 1
                 WHERE id = l_policy_ids(i)
                   AND status = 'ACTIVE';

            FORALL i IN 1 .. l_policy_ids.COUNT
                INSERT INTO notification (customer_id, policy_id, notification_type, channel, recipient,
                                          subject, body, created_by)
                SELECT l_customer_ids(i), l_policy_ids(i), 'POLICY_SUSPENDED', l_channels(i), l_recipients(i),
                       'Poliçeniz askıya alındı', l_bodies(i), p_changed_by
                  FROM dual
                 WHERE l_recipients(i) IS NOT NULL;

            p_suspended := p_suspended + l_policy_ids.COUNT;
            COMMIT;
        END LOOP;
        CLOSE c_to_suspend;
    END mark_overdue_installments;

END pkg_batch;
/
