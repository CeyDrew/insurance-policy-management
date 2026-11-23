package com.portfolio.insurance.policy;

import com.portfolio.insurance.common.plsql.PlsqlErrors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;

/** Calls PKG_POLICY.ISSUE_POLICY / CANCEL_POLICY. */
@Component
class PlsqlPolicyLifecycle implements PolicyLifecycle {

    private final SimpleJdbcCall issuePolicy;
    private final SimpleJdbcCall cancelPolicy;

    PlsqlPolicyLifecycle(JdbcTemplate jdbcTemplate) {
        this.issuePolicy = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("PKG_POLICY")
                .withProcedureName("ISSUE_POLICY")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_policy_id", Types.NUMERIC),
                        new SqlParameter("p_issued_by", Types.VARCHAR));
        this.issuePolicy.compile();

        this.cancelPolicy = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("PKG_POLICY")
                .withProcedureName("CANCEL_POLICY")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_policy_id", Types.NUMERIC),
                        new SqlParameter("p_reason", Types.VARCHAR),
                        new SqlParameter("p_cancel_date", Types.DATE),
                        new SqlParameter("p_cancelled_by", Types.VARCHAR),
                        new SqlOutParameter("p_refund_amount", Types.NUMERIC));
        this.cancelPolicy.compile();
    }

    @Override
    public void issue(Long policyId, String issuedBy) {
        PlsqlErrors.call(() -> issuePolicy.execute(new MapSqlParameterSource()
                .addValue("p_policy_id", policyId)
                .addValue("p_issued_by", issuedBy)));
    }

    @Override
    public BigDecimal cancel(Long policyId, String reason, LocalDate cancelDate, String cancelledBy) {
        return (BigDecimal) PlsqlErrors.call(() -> cancelPolicy.execute(new MapSqlParameterSource()
                        .addValue("p_policy_id", policyId)
                        .addValue("p_reason", reason)
                        .addValue("p_cancel_date", Date.valueOf(cancelDate))
                        .addValue("p_cancelled_by", cancelledBy)))
                .get("p_refund_amount");
    }
}
