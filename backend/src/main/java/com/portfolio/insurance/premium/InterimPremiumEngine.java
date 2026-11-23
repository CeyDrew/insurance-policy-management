package com.portfolio.insurance.premium;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simplified Java pricing used until the PL/SQL engine (PKG_PREMIUM / PKG_POLICY) lands in phase 4.
 * Only base premium/insured value and the city risk factor are applied; the full tariff is ignored.
 */
@Component
@RequiredArgsConstructor
class InterimPremiumEngine implements PremiumEngine {

    private static final BigDecimal CASCO_RATE = new BigDecimal("0.022");
    private static final BigDecimal HOME_RATE = new BigDecimal("0.0015");

    private final JdbcTemplate jdbc;

    @Override
    public PremiumResult calculatePremium(PremiumRequest request) {
        Map<String, Object> product = jdbc.queryForMap(
                "SELECT base_premium, min_premium, tax_rate FROM product WHERE code = ?", request.productCode().name());
        BigDecimal base = (BigDecimal) product.get("BASE_PREMIUM");
        BigDecimal minPremium = (BigDecimal) product.get("MIN_PREMIUM");
        BigDecimal taxRate = (BigDecimal) product.get("TAX_RATE");

        BigDecimal net = switch (request.productCode()) {
            case TRAFFIC -> base.multiply(customerCityRisk(request.customerId()));
            case CASCO -> vehicleValue(request.vehicleId()).multiply(CASCO_RATE)
                    .multiply(customerCityRisk(request.customerId()));
            case HOME -> propertyValueTimesRisk(request.propertyId()).multiply(HOME_RATE);
            case HEALTH -> base;
        };
        net = net.max(minPremium).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = net.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        return new PremiumResult(net, tax, net.add(tax));
    }

    @Override
    public void generateInstallments(Long policyId) {
        PolicyTerms policy = loadTerms(policyId);
        int count = policy.installmentCount();
        BigDecimal regular = policy.grossPremium().divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        List<Object[]> rows = new ArrayList<>();
        for (int n = 1; n <= count; n++) {
            // The last installment absorbs the rounding difference.
            BigDecimal amount = n == count
                    ? policy.grossPremium().subtract(regular.multiply(BigDecimal.valueOf(count - 1)))
                    : regular;
            rows.add(new Object[]{policyId, n, Date.valueOf(policy.startDate().plusMonths(n - 1)), amount});
        }
        jdbc.batchUpdate("INSERT INTO installment (policy_id, installment_no, due_date, amount, status) "
                + "VALUES (?, ?, ?, ?, 'PENDING')", rows);
    }

    @Override
    public BigDecimal calculateRefund(Long policyId, LocalDate cancelDate) {
        PolicyTerms policy = loadTerms(policyId);
        long totalDays = ChronoUnit.DAYS.between(policy.startDate(), policy.endDate());
        long unusedDays = Math.min(totalDays, Math.max(0, ChronoUnit.DAYS.between(cancelDate, policy.endDate())));
        return policy.grossPremium().multiply(BigDecimal.valueOf(unusedDays))
                .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
    }

    private PolicyTerms loadTerms(Long policyId) {
        return jdbc.queryForObject(
                "SELECT gross_premium, installment_count, start_date, end_date FROM policy WHERE id = ?",
                (rs, rowNum) -> new PolicyTerms(rs.getBigDecimal("gross_premium"), rs.getInt("installment_count"),
                        rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class)),
                policyId);
    }

    private BigDecimal customerCityRisk(Long customerId) {
        return jdbc.queryForObject("SELECT ci.risk_factor FROM customer c JOIN city ci ON ci.id = c.city_id "
                + "WHERE c.id = ?", BigDecimal.class, customerId);
    }

    private BigDecimal vehicleValue(Long vehicleId) {
        return jdbc.queryForObject("SELECT vehicle_value FROM vehicle WHERE id = ?", BigDecimal.class, vehicleId);
    }

    private BigDecimal propertyValueTimesRisk(Long propertyId) {
        return jdbc.queryForObject("SELECT p.property_value * ci.risk_factor FROM property p "
                + "JOIN city ci ON ci.id = p.city_id WHERE p.id = ?", BigDecimal.class, propertyId);
    }

    private record PolicyTerms(BigDecimal grossPremium, int installmentCount, LocalDate startDate, LocalDate endDate) {
    }
}
