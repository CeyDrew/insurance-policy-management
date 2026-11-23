package com.portfolio.insurance.claim;

import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.PlsqlFixtures;
import com.portfolio.insurance.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PKG_CLAIM through {@link ClaimLifecycle}: transitions, handler rules and the no-claims step update. */
@IntegrationTest
@Transactional
class ClaimPackageTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private ClaimLifecycle lifecycle;

    @Autowired
    private JdbcTemplate jdbc;

    private PlsqlFixtures fixtures;
    private long customer;
    private long vehicle;
    private long handlerId;

    @BeforeEach
    void setUp() {
        fixtures = new PlsqlFixtures(jdbc);
        customer = fixtures.individual(LocalDate.of(1984, 4, 4), 34);
        fixtures.contact(customer, "hasar.test@example.com", null);
        vehicle = fixtures.vehicle(customer, 2021, new BigDecimal("900000"), "PRIVATE");
        handlerId = fixtures.userId("claims");
    }

    @ParameterizedTest
    @CsvSource({
            "REPORTED, IN_REVIEW, 1", "IN_REVIEW, IN_REVIEW, 1", "IN_REVIEW, APPROVED, 1", "IN_REVIEW, REJECTED, 1",
            "APPROVED, PAID, 1", "REPORTED, APPROVED, 0", "REPORTED, PAID, 0", "REJECTED, IN_REVIEW, 0",
            "PAID, APPROVED, 0", "APPROVED, REJECTED, 0"})
    void transitionMatrix(String from, String to, int allowed) {
        assertThat(jdbc.queryForObject("SELECT pkg_claim.is_allowed_transition(?, ?) FROM dual", Integer.class, from, to))
                .isEqualTo(allowed);
    }

    @Test
    void fullFlowLowersNoClaimsStepAndRepricesRenewalQuote() {
        long policy = activePolicy(5);
        long renewal = fixtures.trafficPolicy(customer, vehicle, "QUOTE", TODAY.plusMonths(6), TODAY.plusMonths(18),
                new BigDecimal("1000"), 1, TODAY.plusMonths(6));
        jdbc.update("UPDATE policy SET renewed_from_policy_id = ?, ncd_step = 6 WHERE id = ?", policy, renewal);
        long claim = fixtures.claim(policy, TODAY.minusDays(10), new BigDecimal("40000"));

        lifecycle.assign(claim, handlerId, "claims");
        lifecycle.approve(claim, new BigDecimal("35000.456"), "Eksper raporu uygun", "claims");
        BigDecimal paid = lifecycle.pay(claim, "claims");

        assertThat(paid).isEqualByComparingTo("35000.46");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, handler_id, approved_amount, paid_amount, decided_at, paid_at FROM claim WHERE id = ?", claim);
        assertThat(row.get("STATUS")).isEqualTo("PAID");
        assertThat(((Number) row.get("HANDLER_ID")).longValue()).isEqualTo(handlerId);
        assertThat(row.get("DECIDED_AT")).isNotNull();
        assertThat(row.get("PAID_AT")).isNotNull();

        // Step 5 -> 4 on payment; the open renewal quote keeps step 4 (no claim-free bonus) and is re-priced.
        assertThat(ncdStep(policy)).isEqualTo(4);
        assertThat(ncdStep(renewal)).isEqualTo(4);
        BigDecimal expectedNet = jdbc.queryForObject(
                "SELECT pkg_premium.calculate_premium('TRAFFIC', ?, ?, NULL, 4, ?) FROM dual",
                BigDecimal.class, customer, vehicle, TODAY.plusMonths(6));
        assertThat(jdbc.queryForObject("SELECT net_premium FROM policy WHERE id = ?", BigDecimal.class, renewal))
                .isEqualByComparingTo(expectedNet);
        assertThat(jdbc.queryForObject("SELECT pkg_policy.next_ncd_step(?) FROM dual", Integer.class, policy))
                .isEqualTo(4);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification
                 WHERE policy_id = ? AND notification_type = 'CLAIM_STATUS_CHANGED'
                """, Integer.class, policy)).isEqualTo(3); // in review, approved, paid
    }

    @Test
    void noClaimsStepNeverDropsBelowOne() {
        long policy = activePolicy(1);
        long claim = fixtures.claim(policy, TODAY.minusDays(3), new BigDecimal("1000"));
        lifecycle.assign(claim, handlerId, "claims");
        lifecycle.approve(claim, new BigDecimal("1000"), null, "claims");
        lifecycle.pay(claim, "claims");

        assertThat(ncdStep(policy)).isEqualTo(1);
    }

    @Test
    void onlyTheAssignedHandlerOrAnAdminDecides() {
        long claim = fixtures.claim(activePolicy(4), TODAY.minusDays(2), new BigDecimal("5000"));

        assertCode(() -> lifecycle.approve(claim, new BigDecimal("100"), null, "claims"), "INVALID_CLAIM_TRANSITION");
        lifecycle.assign(claim, handlerId, "claims");
        assertCode(() -> lifecycle.approve(claim, new BigDecimal("100"), null, "agent"), "NOT_ASSIGNED_HANDLER");

        lifecycle.approve(claim, new BigDecimal("100"), null, "admin"); // admin override
        assertThat(jdbc.queryForObject("SELECT status FROM claim WHERE id = ?", String.class, claim)).isEqualTo("APPROVED");
    }

    @Test
    void validatesHandlerAmountAndReason() {
        long claim = fixtures.claim(activePolicy(4), TODAY.minusDays(2), new BigDecimal("5000"));

        assertCode(() -> lifecycle.assign(claim, fixtures.userId("agent"), "agent"), "INVALID_HANDLER");
        lifecycle.assign(claim, handlerId, "claims");
        assertCode(() -> lifecycle.approve(claim, BigDecimal.ZERO, null, "claims"), "INVALID_APPROVED_AMOUNT");
        assertCode(() -> lifecycle.reject(claim, "   ", "claims"), "REJECTION_REASON_REQUIRED");

        lifecycle.reject(claim, "Teminat dışı", "claims");
        assertCode(() -> lifecycle.pay(claim, "claims"), "INVALID_CLAIM_TRANSITION");
        assertThatThrownBy(() -> lifecycle.assign(-1L, handlerId, "claims"))
                .isInstanceOf(com.portfolio.insurance.common.error.ResourceNotFoundException.class);
    }

    private long activePolicy(int ncdStep) {
        long policy = fixtures.trafficPolicy(customer, vehicle, "ACTIVE", TODAY.minusMonths(6), TODAY.plusMonths(6),
                new BigDecimal("6000"), 1, null);
        jdbc.update("UPDATE policy SET ncd_step = ? WHERE id = ?", ncdStep, policy);
        return policy;
    }

    private int ncdStep(long policyId) {
        return jdbc.queryForObject("SELECT ncd_step FROM policy WHERE id = ?", Integer.class, policyId);
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).extracting("code").isEqualTo(code);
    }
}
