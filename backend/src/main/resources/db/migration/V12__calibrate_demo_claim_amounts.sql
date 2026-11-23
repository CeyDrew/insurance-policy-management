-- Demo data fix: V7 generated claim amounts independently of the policy (15.000 - 350.000 TL), so a traffic policy
-- with a 5.000 TL premium could carry a 100.000 TL claim and loss ratios on the dashboard came out at several hundred
-- percent. Seeded claims are re-scaled relative to their policy's gross premium (deterministic: 1.2x - 2.8x),
-- which puts portfolio loss ratios in a realistic band. Only rows created by the seed are touched.

UPDATE claim c
   SET estimated_amount = (SELECT ROUND(p.gross_premium * (1.2 + MOD(c.id, 5) * 0.4), -2)
                             FROM policy p
                            WHERE p.id = c.policy_id),
       updated_at = SYSTIMESTAMP,
       updated_by = 'seed-calibration',
       version = version + 1
 WHERE c.created_by = 'seed';

UPDATE claim c
   SET approved_amount = ROUND(c.estimated_amount * 0.85, -2),
       paid_amount = CASE WHEN c.status = 'PAID' THEN ROUND(c.estimated_amount * 0.85, -2) END
 WHERE c.created_by = 'seed'
   AND c.status IN ('APPROVED', 'PAID');
