-- Removes the performance data set created by seed-performance.sql.
SET VERIFY OFF
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

DELETE FROM notification WHERE policy_id IN (SELECT id FROM policy WHERE created_by = 'perf-seed');
DELETE FROM installment WHERE policy_id IN (SELECT id FROM policy WHERE created_by = 'perf-seed');
DELETE FROM audit_log
 WHERE table_name = 'POLICY' AND record_id IN (SELECT id FROM policy WHERE created_by = 'perf-seed');
DELETE FROM policy WHERE created_by = 'perf-seed';
COMMIT;
EXIT
