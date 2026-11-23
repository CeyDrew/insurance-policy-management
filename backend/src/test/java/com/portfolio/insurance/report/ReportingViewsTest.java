package com.portfolio.insurance.report;

import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.PlsqlFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Earned premium and incurred claims as computed by the reporting views. */
@IntegrationTest
@Transactional
class ReportingViewsTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private JdbcTemplate jdbc;

    private PlsqlFixtures fixtures;
    private long customer;
    private long vehicle;

    @BeforeEach
    void setUp() {
        fixtures = new PlsqlFixtures(jdbc);
        customer = fixtures.individual(LocalDate.of(1982, 2, 2), 6);
        vehicle = fixtures.vehicle(customer, 2021, new BigDecimal("700000"), "PRIVATE");
    }

    @Test
    void premiumIsEarnedProRataOverTheTerm() {
        // 100 of 365 days elapsed -> 3650 x 100 / 365 = 1000.00
        LocalDate start = TODAY.minusDays(100);
        long running = fixtures.trafficPolicy(customer, vehicle, "ACTIVE", start, start.plusDays(365),
                new BigDecimal("3650.00"), 1, null);
        // Fully elapsed term is fully earned; a future start earns nothing yet.
        long ended = fixtures.trafficPolicy(customer, vehicle, "EXPIRED", TODAY.minusDays(400), TODAY.minusDays(35),
                new BigDecimal("1200.00"), 1, null);
        long future = fixtures.trafficPolicy(customer, vehicle, "ACTIVE", TODAY.plusDays(5), TODAY.plusDays(370),
                new BigDecimal("900.00"), 1, null);

        assertThat(earned(running)).isEqualByComparingTo("1000.00");
        assertThat(earned(ended)).isEqualByComparingTo("1200.00");
        assertThat(earned(future)).isEqualByComparingTo("0.00");
    }

    @Test
    void cancelledPolicyEarnsWhatWasNotRefunded() {
        long policy = fixtures.trafficPolicy(customer, vehicle, "ACTIVE", TODAY.minusDays(50), TODAY.plusDays(315),
                new BigDecimal("1000.00"), 1, null);
        jdbc.update("""
                UPDATE policy SET status = 'CANCELLED', cancelled_at = SYSTIMESTAMP, cancel_reason = 'Test',
                       refund_amount = 300 WHERE id = ?
                """, policy);

        assertThat(earned(policy)).isEqualByComparingTo("700.00");
    }

    @Test
    void incurredClaimsAreThePaidAmountsPlusTheOutstandingReserve() {
        long policy = fixtures.trafficPolicy(customer, vehicle, "ACTIVE", TODAY.minusDays(50), TODAY.plusDays(315),
                new BigDecimal("1000.00"), 1, null);
        long paid = fixtures.claim(policy, TODAY.minusDays(40), new BigDecimal("600"));
        long approved = fixtures.claim(policy, TODAY.minusDays(30), new BigDecimal("400"));
        fixtures.claim(policy, TODAY.minusDays(20), new BigDecimal("100")); // reported: reserve = estimate
        long rejected = fixtures.claim(policy, TODAY.minusDays(10), new BigDecimal("9999"));
        long handler = fixtures.userId("claims");
        jdbc.update("""
                UPDATE claim SET status = 'PAID', handler_id = ?, approved_amount = 500, paid_amount = 500,
                       paid_at = SYSTIMESTAMP WHERE id = ?
                """, handler, paid);
        jdbc.update("UPDATE claim SET status = 'APPROVED', handler_id = ?, approved_amount = 200 WHERE id = ?",
                handler, approved);
        jdbc.update("UPDATE claim SET status = 'REJECTED', handler_id = ?, decision_note = 'x' WHERE id = ?",
                handler, rejected);

        Map<String, Object> incurred = jdbc.queryForMap(
                "SELECT paid_claims, outstanding_claims, claim_count FROM v_policy_incurred WHERE policy_id = ?", policy);

        assertThat((BigDecimal) incurred.get("PAID_CLAIMS")).isEqualByComparingTo("500");
        assertThat((BigDecimal) incurred.get("OUTSTANDING_CLAIMS")).isEqualByComparingTo("300"); // 200 + 100
        assertThat(((Number) incurred.get("CLAIM_COUNT")).intValue()).isEqualTo(4);
    }

    @Test
    void lossRatioIsIncurredOverEarnedPerProduct() {
        Map<String, Object> traffic = jdbc.queryForMap("""
                SELECT earned_premium, incurred_claims, loss_ratio FROM v_loss_ratio_by_product
                 WHERE product_code = 'TRAFFIC'
                """);
        BigDecimal earned = (BigDecimal) traffic.get("EARNED_PREMIUM");
        BigDecimal incurred = (BigDecimal) traffic.get("INCURRED_CLAIMS");

        assertThat(earned).isPositive();
        assertThat((BigDecimal) traffic.get("LOSS_RATIO"))
                .isEqualByComparingTo(incurred.divide(earned, 4, java.math.RoundingMode.HALF_UP));
    }

    private BigDecimal earned(long policyId) {
        return jdbc.queryForObject("SELECT earned_premium FROM v_policy_earned WHERE policy_id = ?",
                BigDecimal.class, policyId);
    }
}
