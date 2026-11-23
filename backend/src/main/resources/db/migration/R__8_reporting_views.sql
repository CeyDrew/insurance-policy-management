-- Reporting layer of the dashboard.
--
--   V_POLICY_EARNED              earned premium per issued policy (pro rata of the elapsed term)
--   V_POLICY_INCURRED            paid and outstanding claim amounts per policy
--   V_LOSS_RATIO_BY_PRODUCT      hasar/prim oranı per product: incurred claims / earned premium
--   V_AGENT_PERFORMANCE          quotes, issued policies, conversion, written premium and loss ratio per agent
--   MV_PREMIUM_PRODUCTION_MONTHLY premium production per month and product; aggregated once, refreshed on demand
--                                (PKG_REPORTING.REFRESH_DASHBOARD, nightly scheduler or the dashboard's refresh button)

CREATE OR REPLACE VIEW v_policy_earned AS
SELECT p.id AS policy_id,
       p.product_id,
       p.agent_id,
       p.status,
       p.issued_at,
       p.gross_premium,
       CASE
           -- A cancelled policy keeps what was not refunded.
           WHEN p.status = 'CANCELLED' THEN p.gross_premium - NVL(p.refund_amount, 0)
           -- Otherwise premium is earned day by day over the term.
           ELSE ROUND(p.gross_premium
                      * LEAST(GREATEST(TRUNC(SYSDATE) - p.start_date, 0), p.end_date - p.start_date)
                      / (p.end_date - p.start_date), 2)
       END AS earned_premium
  FROM policy p
 WHERE p.status <> 'QUOTE';

CREATE OR REPLACE VIEW v_policy_incurred AS
SELECT c.policy_id,
       SUM(CASE WHEN c.status = 'PAID' THEN c.paid_amount ELSE 0 END) AS paid_claims,
       -- Outstanding reserve: approved amount once decided, the estimate while still under review.
       SUM(CASE
               WHEN c.status = 'APPROVED' THEN c.approved_amount
               WHEN c.status IN ('REPORTED', 'IN_REVIEW') THEN NVL(c.estimated_amount, 0)
               ELSE 0
           END) AS outstanding_claims,
       COUNT(*) AS claim_count
  FROM claim c
 GROUP BY c.policy_id;

CREATE OR REPLACE VIEW v_loss_ratio_by_product AS
SELECT pr.id AS product_id,
       pr.code AS product_code,
       pr.name AS product_name,
       NVL(SUM(e.earned_premium), 0) AS earned_premium,
       NVL(SUM(i.paid_claims), 0) AS paid_claims,
       NVL(SUM(i.outstanding_claims), 0) AS outstanding_claims,
       NVL(SUM(i.paid_claims), 0) + NVL(SUM(i.outstanding_claims), 0) AS incurred_claims,
       ROUND((NVL(SUM(i.paid_claims), 0) + NVL(SUM(i.outstanding_claims), 0))
             / NULLIF(SUM(e.earned_premium), 0), 4) AS loss_ratio
  FROM product pr
  LEFT JOIN v_policy_earned e ON e.product_id = pr.id
  LEFT JOIN v_policy_incurred i ON i.policy_id = e.policy_id
 GROUP BY pr.id, pr.code, pr.name;

CREATE OR REPLACE VIEW v_agent_performance AS
SELECT u.id AS agent_id,
       u.full_name AS agent_name,
       COUNT(p.id) AS quote_count,
       COUNT(p.issued_at) AS issued_count,
       ROUND(COUNT(p.issued_at) / NULLIF(COUNT(p.id), 0), 4) AS conversion_rate,
       NVL(SUM(CASE WHEN p.issued_at IS NOT NULL THEN p.gross_premium END), 0) AS gross_written,
       SUM(CASE WHEN p.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count,
       NVL(SUM(e.earned_premium), 0) AS earned_premium,
       NVL(SUM(i.paid_claims), 0) + NVL(SUM(i.outstanding_claims), 0) AS incurred_claims,
       ROUND((NVL(SUM(i.paid_claims), 0) + NVL(SUM(i.outstanding_claims), 0))
             / NULLIF(SUM(e.earned_premium), 0), 4) AS loss_ratio
  FROM app_user u
  JOIN policy p ON p.agent_id = u.id
  LEFT JOIN v_policy_earned e ON e.policy_id = p.id
  LEFT JOIN v_policy_incurred i ON i.policy_id = p.id
 GROUP BY u.id, u.full_name;

-- CREATE OR REPLACE does not exist for materialized views; recreate it when this file changes.
BEGIN
    EXECUTE IMMEDIATE 'DROP MATERIALIZED VIEW mv_premium_production_monthly';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE <> -12003 THEN -- ORA-12003: materialized view does not exist
            RAISE;
        END IF;
END;
/

CREATE MATERIALIZED VIEW mv_premium_production_monthly
    BUILD IMMEDIATE
    REFRESH COMPLETE ON DEMAND
AS
SELECT TRUNC(p.issued_at, 'MM') AS production_month,
       pr.code AS product_code,
       pr.name AS product_name,
       COUNT(*) AS policy_count,
       SUM(p.net_premium) AS net_premium,
       SUM(p.gross_premium) AS gross_premium,
       SUM(CASE WHEN p.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count
  FROM policy p
  JOIN product pr ON pr.id = p.product_id
 WHERE p.issued_at IS NOT NULL
 GROUP BY TRUNC(p.issued_at, 'MM'), pr.code, pr.name;

CREATE OR REPLACE PACKAGE pkg_reporting AS

    /** Recomputes the dashboard's materialized views. */
    PROCEDURE refresh_dashboard;

    /** When the dashboard data was last recomputed. */
    FUNCTION last_refreshed RETURN DATE;

END pkg_reporting;
/

CREATE OR REPLACE PACKAGE BODY pkg_reporting AS

    PROCEDURE refresh_dashboard IS
    BEGIN
        DBMS_MVIEW.REFRESH(list => 'MV_PREMIUM_PRODUCTION_MONTHLY', method => 'C', atomic_refresh => TRUE);
    END refresh_dashboard;

    FUNCTION last_refreshed RETURN DATE IS
        l_refreshed DATE;
    BEGIN
        SELECT last_refresh_date INTO l_refreshed
          FROM user_mviews
         WHERE mview_name = 'MV_PREMIUM_PRODUCTION_MONTHLY';
        RETURN l_refreshed;
    END last_refreshed;

END pkg_reporting;
/
