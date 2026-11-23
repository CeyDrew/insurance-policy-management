package com.portfolio.insurance.payment;

import com.portfolio.insurance.common.plsql.PlsqlErrors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.util.Map;

/** Calls PKG_PAYMENT.PAY_INSTALLMENT. */
@Component
class PlsqlPaymentProcessor implements PaymentProcessor {

    private final SimpleJdbcCall payInstallment;

    PlsqlPaymentProcessor(JdbcTemplate jdbc) {
        this.payInstallment = new SimpleJdbcCall(jdbc)
                .withCatalogName("PKG_PAYMENT")
                .withProcedureName("PAY_INSTALLMENT")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_installment_id", Types.NUMERIC),
                        new SqlParameter("p_payment_method", Types.VARCHAR),
                        new SqlParameter("p_paid_by", Types.VARCHAR),
                        new SqlOutParameter("p_payment_id", Types.NUMERIC),
                        new SqlOutParameter("p_reactivated", Types.NUMERIC));
        this.payInstallment.compile();
    }

    @Override
    public Outcome payInstallment(Long installmentId, PaymentMethod method, String paidBy) {
        Map<String, Object> out = PlsqlErrors.call(() -> payInstallment.execute(new MapSqlParameterSource()
                .addValue("p_installment_id", installmentId)
                .addValue("p_payment_method", method.name())
                .addValue("p_paid_by", paidBy)));
        return new Outcome(((Number) out.get("p_payment_id")).longValue(),
                ((Number) out.get("p_reactivated")).intValue() == 1);
    }
}
