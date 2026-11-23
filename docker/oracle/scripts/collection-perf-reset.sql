-- Puts the performance data set back before the next bank collection measurement (docs/batch-jobs.md):
-- payments and result lines of the perf installments removed, installments PENDING and not requested.
-- The next request file then lists the same installments again under a new file number.
SET VERIFY OFF
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

DELETE FROM notification WHERE policy_id IN (SELECT id FROM policy WHERE created_by = 'perf-seed');
DELETE FROM collection_result
 WHERE installment_id IN (SELECT id FROM installment WHERE created_by = 'perf-seed');
DELETE FROM payment WHERE installment_id IN (SELECT id FROM installment WHERE created_by = 'perf-seed');
UPDATE installment
   SET status = 'PENDING', paid_at = NULL, collection_request_file_id = NULL,
       collection_reject_code = NULL, collection_rejected_at = NULL, updated_at = NULL, updated_by = NULL
 WHERE created_by = 'perf-seed';
UPDATE policy
   SET status = 'ACTIVE', updated_at = NULL, updated_by = NULL
 WHERE created_by = 'perf-seed' AND status <> 'ACTIVE';
COMMIT;
EXIT
