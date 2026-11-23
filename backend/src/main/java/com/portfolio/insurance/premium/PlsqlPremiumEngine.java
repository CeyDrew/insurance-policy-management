package com.portfolio.insurance.premium;

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
import java.util.Map;

/**
 * Calls PKG_PREMIUM.CALCULATE. Parameters are declared explicitly (no metadata lookup), so the call is compiled
 * once at startup and stays independent of database dictionary access.
 */
@Component
class PlsqlPremiumEngine implements PremiumEngine {

    private final SimpleJdbcCall calculate;

    PlsqlPremiumEngine(JdbcTemplate jdbcTemplate) {
        this.calculate = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("PKG_PREMIUM")
                .withProcedureName("CALCULATE")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_product_code", Types.VARCHAR),
                        new SqlParameter("p_customer_id", Types.NUMERIC),
                        new SqlParameter("p_vehicle_id", Types.NUMERIC),
                        new SqlParameter("p_property_id", Types.NUMERIC),
                        new SqlParameter("p_ncd_step", Types.NUMERIC),
                        new SqlParameter("p_start_date", Types.DATE),
                        new SqlOutParameter("p_net_premium", Types.NUMERIC),
                        new SqlOutParameter("p_tax_amount", Types.NUMERIC),
                        new SqlOutParameter("p_gross_premium", Types.NUMERIC));
        this.calculate.compile();
    }

    @Override
    public PremiumResult calculatePremium(PremiumRequest request) {
        MapSqlParameterSource in = new MapSqlParameterSource()
                .addValue("p_product_code", request.productCode().name())
                .addValue("p_customer_id", request.customerId())
                .addValue("p_vehicle_id", request.vehicleId())
                .addValue("p_property_id", request.propertyId())
                .addValue("p_ncd_step", request.ncdStep())
                .addValue("p_start_date", Date.valueOf(request.startDate()));

        Map<String, Object> out = PlsqlErrors.call(() -> calculate.execute(in));
        return new PremiumResult((BigDecimal) out.get("p_net_premium"), (BigDecimal) out.get("p_tax_amount"),
                (BigDecimal) out.get("p_gross_premium"));
    }
}
