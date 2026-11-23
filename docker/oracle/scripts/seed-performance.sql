-- Optional performance data set for the batch job comparison (docs/batch-jobs.md). NOT a Flyway migration.
--
-- Creates &1 ACTIVE traffic policies (default 50000) on the seeded vehicles, each with 4 monthly installments
-- left PENDING. Start dates are spread over the last 365 days, so most policies have overdue installments.
-- All rows are marked created_by = 'perf-seed'; remove them with perf-cleanup.sql.
--
-- Usage:
--   docker cp docker/oracle/scripts insurance-oracle:/tmp/
--   docker exec -it insurance-oracle sqlplus insurance/<APP_USER_PASSWORD>@FREEPDB1 @/tmp/scripts/seed-performance.sql 50000
--
-- The data deliberately ignores the "one policy per vehicle" business rule; it only exists to give the jobs volume.

SET SERVEROUTPUT ON
SET VERIFY OFF
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

DECLARE
    c_policy_count CONSTANT PLS_INTEGER := NVL(TO_NUMBER('&1'), 50000);
    c_slice        CONSTANT PLS_INTEGER := 5000;
    c_installments CONSTANT PLS_INTEGER := 4;

    TYPE t_numbers IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
    TYPE t_dates   IS TABLE OF DATE INDEX BY PLS_INTEGER;
    TYPE t_strings IS TABLE OF VARCHAR2(30) INDEX BY PLS_INTEGER;

    l_vehicle_ids  t_numbers;
    l_owner_ids    t_numbers;
    l_product_id   NUMBER;
    l_agent_id     NUMBER;

    l_ids          t_numbers;
    l_numbers      t_strings;
    l_vehicles     t_numbers;
    l_customers    t_numbers;
    l_starts       t_dates;
    l_gross        t_numbers;

    l_inst_policy  t_numbers;
    l_inst_no      t_numbers;
    l_inst_due     t_dates;
    l_inst_amount  t_numbers;

    l_done         PLS_INTEGER := 0;
    l_size         PLS_INTEGER;
    l_pick         PLS_INTEGER;
    l_regular      NUMBER;
    l_started      TIMESTAMP := SYSTIMESTAMP;
BEGIN
    DBMS_RANDOM.SEED(42);
    SELECT id, customer_id BULK COLLECT INTO l_vehicle_ids, l_owner_ids FROM vehicle ORDER BY id;
    SELECT id INTO l_product_id FROM product WHERE code = 'TRAFFIC';
    SELECT id INTO l_agent_id FROM app_user WHERE username = 'agent';

    WHILE l_done < c_policy_count LOOP
        l_size := LEAST(c_slice, c_policy_count - l_done);
        l_ids.DELETE; l_numbers.DELETE; l_vehicles.DELETE; l_customers.DELETE; l_starts.DELETE; l_gross.DELETE;
        l_inst_policy.DELETE; l_inst_no.DELETE; l_inst_due.DELETE; l_inst_amount.DELETE;

        FOR i IN 1 .. l_size LOOP
            l_ids(i) := policy_seq.NEXTVAL;
            l_pick := TRUNC(DBMS_RANDOM.VALUE(1, l_vehicle_ids.COUNT + 1));
            l_vehicles(i) := l_vehicle_ids(l_pick);
            l_customers(i) := l_owner_ids(l_pick);
            l_starts(i) := TRUNC(SYSDATE) - TRUNC(DBMS_RANDOM.VALUE(0, 365));
            l_gross(i) := ROUND(DBMS_RANDOM.VALUE(3000, 12000), 2);
            l_numbers(i) := 'PRF-' || TO_CHAR(l_starts(i), 'YYYY') || '-' || LPAD(l_ids(i), 9, '0');

            l_regular := TRUNC(l_gross(i) / c_installments, 2);
            FOR n IN 1 .. c_installments LOOP
                l_inst_policy(l_inst_policy.COUNT + 1) := l_ids(i);
                l_inst_no(l_inst_no.COUNT + 1) := n;
                l_inst_due(l_inst_due.COUNT + 1) := ADD_MONTHS(l_starts(i), n - 1);
                l_inst_amount(l_inst_amount.COUNT + 1) :=
                    CASE WHEN n = c_installments THEN l_gross(i) - l_regular * (c_installments - 1) ELSE l_regular END;
            END LOOP;
        END LOOP;

        FORALL i IN 1 .. l_size
            INSERT INTO policy (id, policy_number, product_id, customer_id, vehicle_id, agent_id, status,
                                start_date, end_date, ncd_step, net_premium, tax_amount, gross_premium,
                                installment_count, issued_at, created_by)
            VALUES (l_ids(i), l_numbers(i), l_product_id, l_customers(i), l_vehicles(i), l_agent_id, 'ACTIVE',
                    l_starts(i), ADD_MONTHS(l_starts(i), 12), 4, l_gross(i), 0, l_gross(i),
                    c_installments, CAST(l_starts(i) AS TIMESTAMP), 'perf-seed');

        FORALL j IN 1 .. l_inst_policy.COUNT
            INSERT INTO installment (policy_id, installment_no, due_date, amount, status, created_by)
            VALUES (l_inst_policy(j), l_inst_no(j), l_inst_due(j), l_inst_amount(j), 'PENDING', 'perf-seed');

        COMMIT;
        l_done := l_done + l_size;
        DBMS_OUTPUT.PUT_LINE(l_done || ' policies inserted');
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('Done in '
        || ROUND(EXTRACT(SECOND FROM (SYSTIMESTAMP - l_started)) + 60 * EXTRACT(MINUTE FROM (SYSTIMESTAMP - l_started)), 1)
        || ' s');
END;
/

SELECT COUNT(*) AS perf_policies FROM policy WHERE created_by = 'perf-seed';
SELECT COUNT(*) AS overdue_candidates
  FROM installment
 WHERE created_by = 'perf-seed' AND status = 'PENDING' AND due_date < TRUNC(SYSDATE);
EXIT
