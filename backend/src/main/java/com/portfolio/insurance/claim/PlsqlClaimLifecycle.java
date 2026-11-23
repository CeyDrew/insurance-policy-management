package com.portfolio.insurance.claim;

import com.portfolio.insurance.common.plsql.PlsqlErrors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Types;

/** Calls PKG_CLAIM procedures. */
@Component
class PlsqlClaimLifecycle implements ClaimLifecycle {

    private final SimpleJdbcCall assign;
    private final SimpleJdbcCall approve;
    private final SimpleJdbcCall reject;
    private final SimpleJdbcCall pay;

    PlsqlClaimLifecycle(JdbcTemplate jdbc) {
        this.assign = procedure(jdbc, "ASSIGN_CLAIM",
                new SqlParameter("p_claim_id", Types.NUMERIC),
                new SqlParameter("p_handler_id", Types.NUMERIC),
                new SqlParameter("p_changed_by", Types.VARCHAR));
        this.approve = procedure(jdbc, "APPROVE_CLAIM",
                new SqlParameter("p_claim_id", Types.NUMERIC),
                new SqlParameter("p_approved_amount", Types.NUMERIC),
                new SqlParameter("p_note", Types.VARCHAR),
                new SqlParameter("p_changed_by", Types.VARCHAR));
        this.reject = procedure(jdbc, "REJECT_CLAIM",
                new SqlParameter("p_claim_id", Types.NUMERIC),
                new SqlParameter("p_note", Types.VARCHAR),
                new SqlParameter("p_changed_by", Types.VARCHAR));
        this.pay = procedure(jdbc, "PAY_CLAIM",
                new SqlParameter("p_claim_id", Types.NUMERIC),
                new SqlParameter("p_changed_by", Types.VARCHAR),
                new SqlOutParameter("p_paid_amount", Types.NUMERIC));
    }

    @Override
    public void assign(Long claimId, Long handlerId, String changedBy) {
        PlsqlErrors.call(() -> assign.execute(new MapSqlParameterSource()
                .addValue("p_claim_id", claimId)
                .addValue("p_handler_id", handlerId)
                .addValue("p_changed_by", changedBy)));
    }

    @Override
    public void approve(Long claimId, BigDecimal approvedAmount, String note, String changedBy) {
        PlsqlErrors.call(() -> approve.execute(new MapSqlParameterSource()
                .addValue("p_claim_id", claimId)
                .addValue("p_approved_amount", approvedAmount)
                .addValue("p_note", note)
                .addValue("p_changed_by", changedBy)));
    }

    @Override
    public void reject(Long claimId, String note, String changedBy) {
        PlsqlErrors.call(() -> reject.execute(new MapSqlParameterSource()
                .addValue("p_claim_id", claimId)
                .addValue("p_note", note)
                .addValue("p_changed_by", changedBy)));
    }

    @Override
    public BigDecimal pay(Long claimId, String changedBy) {
        return (BigDecimal) PlsqlErrors.call(() -> pay.execute(new MapSqlParameterSource()
                        .addValue("p_claim_id", claimId)
                        .addValue("p_changed_by", changedBy)))
                .get("p_paid_amount");
    }

    private static SimpleJdbcCall procedure(JdbcTemplate jdbc, String name, SqlParameter... parameters) {
        SimpleJdbcCall call = new SimpleJdbcCall(jdbc)
                .withCatalogName("PKG_CLAIM")
                .withProcedureName(name)
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(parameters);
        call.compile();
        return call;
    }
}
