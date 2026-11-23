-- Puts the performance data set back into its initial state so the overdue jobs can be measured again:
-- installments PENDING, policies ACTIVE, job notifications removed.
SET VERIFY OFF
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

DELETE FROM notification
 WHERE policy_id IN (SELECT id FROM policy WHERE created_by = 'perf-seed');
UPDATE installment
   SET status = 'PENDING', updated_at = NULL, updated_by = NULL
 WHERE created_by = 'perf-seed' AND status <> 'PENDING';
UPDATE policy
   SET status = 'ACTIVE', updated_at = NULL, updated_by = NULL
 WHERE created_by = 'perf-seed' AND status <> 'ACTIVE';
COMMIT;
EXIT
