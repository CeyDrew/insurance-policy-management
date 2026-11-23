package com.portfolio.insurance.report;

import com.portfolio.insurance.claim.ClaimStatus;
import com.portfolio.insurance.reference.ProductCode;
import com.portfolio.insurance.report.DashboardDto.AgentPerformance;
import com.portfolio.insurance.report.DashboardDto.ClaimStatusCount;
import com.portfolio.insurance.report.DashboardDto.Kpis;
import com.portfolio.insurance.report.DashboardDto.LossRatio;
import com.portfolio.insurance.report.DashboardDto.MonthlyProduction;
import com.portfolio.insurance.report.DashboardDto.ProductMix;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/** Reads the reporting views (R__8_reporting_views.sql). */
@Repository
@RequiredArgsConstructor
class ReportRepository {

    private final JdbcTemplate jdbc;

    Kpis kpis(LocalDate today) {
        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate previousYearStart = yearStart.minusYears(1);
        LocalDate previousYearSameDay = today.minusYears(1).plusDays(1);
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM policy WHERE status = 'ACTIVE') AS active_policies,
                       (SELECT COUNT(*) FROM policy WHERE status = 'SUSPENDED') AS suspended_policies,
                       (SELECT NVL(SUM(gross_premium), 0) FROM policy
                         WHERE issued_at >= ? AND issued_at < ?) AS gross_ytd,
                       (SELECT NVL(SUM(gross_premium), 0) FROM policy
                         WHERE issued_at >= ? AND issued_at < ?) AS gross_previous_ytd,
                       (SELECT NVL(SUM(earned_premium), 0) FROM v_loss_ratio_by_product) AS earned,
                       (SELECT NVL(SUM(incurred_claims), 0) FROM v_loss_ratio_by_product) AS incurred,
                       (SELECT COUNT(*) FROM claim WHERE status IN ('REPORTED', 'IN_REVIEW', 'APPROVED')) AS open_claims,
                       (SELECT NVL(SUM(outstanding_claims), 0) FROM v_policy_incurred) AS outstanding,
                       (SELECT COUNT(*) FROM installment WHERE status = 'OVERDUE') AS overdue_count,
                       (SELECT NVL(SUM(amount), 0) FROM installment WHERE status = 'OVERDUE') AS overdue_amount,
                       (SELECT ROUND(COUNT(issued_at) / NULLIF(COUNT(*), 0), 4) FROM policy
                         WHERE created_at >= ADD_MONTHS(?, -12)) AS conversion,
                       (SELECT COUNT(*) FROM policy WHERE status = 'ACTIVE' AND end_date BETWEEN ? AND ?) AS renewals_due
                  FROM dual
                """, (rs, rowNum) -> {
            BigDecimal earned = rs.getBigDecimal("earned");
            BigDecimal incurred = rs.getBigDecimal("incurred");
            return new Kpis(rs.getLong("active_policies"), rs.getLong("suspended_policies"),
                    rs.getBigDecimal("gross_ytd"), rs.getBigDecimal("gross_previous_ytd"), earned, incurred,
                    earned.signum() == 0 ? null : incurred.divide(earned, 4, java.math.RoundingMode.HALF_UP),
                    rs.getLong("open_claims"), rs.getBigDecimal("outstanding"), rs.getLong("overdue_count"),
                    rs.getBigDecimal("overdue_amount"), rs.getBigDecimal("conversion"), rs.getLong("renewals_due"));
        }, Date.valueOf(yearStart), Date.valueOf(today.plusDays(1)), Date.valueOf(previousYearStart),
                Date.valueOf(previousYearSameDay), Date.valueOf(today), Date.valueOf(today),
                Date.valueOf(today.plusDays(30)));
    }

    List<MonthlyProduction> monthlyProduction(YearMonth from) {
        return jdbc.query("""
                SELECT TO_CHAR(production_month, 'YYYY-MM') AS month, product_code, product_name,
                       policy_count, gross_premium
                  FROM mv_premium_production_monthly
                 WHERE production_month >= ?
                 ORDER BY production_month, product_code
                """, (rs, rowNum) -> new MonthlyProduction(rs.getString("month"),
                ProductCode.valueOf(rs.getString("product_code")), rs.getString("product_name"),
                rs.getLong("policy_count"), rs.getBigDecimal("gross_premium")), Date.valueOf(from.atDay(1)));
    }

    List<ProductMix> productMix() {
        return jdbc.query("""
                SELECT pr.code, pr.name, COUNT(p.id) AS active_policies, NVL(SUM(p.gross_premium), 0) AS gross
                  FROM product pr
                  LEFT JOIN policy p ON p.product_id = pr.id AND p.status = 'ACTIVE'
                 GROUP BY pr.id, pr.code, pr.name
                 ORDER BY pr.id
                """, (rs, rowNum) -> new ProductMix(ProductCode.valueOf(rs.getString("code")), rs.getString("name"),
                rs.getLong("active_policies"), rs.getBigDecimal("gross")));
    }

    List<LossRatio> lossRatios() {
        return jdbc.query("""
                SELECT product_code, product_name, earned_premium, paid_claims, outstanding_claims,
                       incurred_claims, loss_ratio
                  FROM v_loss_ratio_by_product
                 ORDER BY product_id
                """, (rs, rowNum) -> new LossRatio(ProductCode.valueOf(rs.getString("product_code")),
                rs.getString("product_name"), rs.getBigDecimal("earned_premium"), rs.getBigDecimal("paid_claims"),
                rs.getBigDecimal("outstanding_claims"), rs.getBigDecimal("incurred_claims"),
                rs.getBigDecimal("loss_ratio")));
    }

    List<AgentPerformance> agents() {
        return jdbc.query("""
                SELECT agent_name, quote_count, issued_count, conversion_rate, gross_written, cancelled_count,
                       loss_ratio
                  FROM v_agent_performance
                 ORDER BY gross_written DESC
                """, (rs, rowNum) -> new AgentPerformance(rs.getString("agent_name"), rs.getLong("quote_count"),
                rs.getLong("issued_count"), rs.getBigDecimal("conversion_rate"), rs.getBigDecimal("gross_written"),
                rs.getLong("cancelled_count"), rs.getBigDecimal("loss_ratio")));
    }

    List<ClaimStatusCount> claimStatuses() {
        return jdbc.query("SELECT status, COUNT(*) AS cnt FROM claim GROUP BY status",
                (rs, rowNum) -> new ClaimStatusCount(ClaimStatus.valueOf(rs.getString("status")), rs.getLong("cnt")));
    }

    LocalDateTime productionRefreshedAt() {
        return jdbc.queryForObject("SELECT pkg_reporting.last_refreshed FROM dual", LocalDateTime.class);
    }

    void refresh() {
        jdbc.update("BEGIN pkg_reporting.refresh_dashboard; END;");
    }
}
