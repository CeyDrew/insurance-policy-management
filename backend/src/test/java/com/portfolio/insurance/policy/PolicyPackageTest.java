package com.portfolio.insurance.policy;

import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.PlsqlFixtures;
import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * PKG_POLICY (status transitions, issue, refund, cancel), PKG_PREMIUM.GENERATE_INSTALLMENTS and the audit trigger.
 */
@IntegrationTest
@Transactional
class PolicyPackageTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private PolicyLifecycle lifecycle;

    @Autowired
    private JdbcTemplate jdbc;

    private PlsqlFixtures fixtures;
    private long customer;
    private long vehicle;

    @BeforeEach
    void setUp() {
        fixtures = new PlsqlFixtures(jdbc);
        customer = fixtures.individual(LocalDate.of(1985, 5, 5), 34);
        vehicle = fixtures.vehicle(customer, 2021, new BigDecimal("900000"), "PRIVATE");
    }

    @ParameterizedTest
    @CsvSource({
            "QUOTE, ACTIVE, 1", "ACTIVE, SUSPENDED, 1", "ACTIVE, CANCELLED, 1", "ACTIVE, EXPIRED, 1",
            "ACTIVE, RENEWED, 1", "SUSPENDED, ACTIVE, 1", "SUSPENDED, CANCELLED, 1", "SUSPENDED, EXPIRED, 1",
            "QUOTE, CANCELLED, 0", "ACTIVE, QUOTE, 0", "CANCELLED, ACTIVE, 0", "EXPIRED, ACTIVE, 0",
            "RENEWED, ACTIVE, 0", "SUSPENDED, RENEWED, 0"})
    void transitionMatrix(String from, String to, int allowed) {
        assertThat(jdbc.queryForObject("SELECT pkg_policy.is_allowed_transition(?, ?) FROM dual", Integer.class, from, to))
                .isEqualTo(allowed);
    }

    @Test
    void issueActivatesQuoteAndCreatesInstallmentPlan() {
        long policy = quote(TODAY.plusDays(1), new BigDecimal("1000.00"), 3, TODAY.plusDays(10));

        lifecycle.issue(policy, "agent");

        Map<String, Object> row = jdbc.queryForMap("SELECT status, issued_at, updated_by, version FROM policy WHERE id = ?", policy);
        assertThat(row.get("STATUS")).isEqualTo("ACTIVE");
        assertThat(row.get("ISSUED_AT")).isNotNull();
        assertThat(row.get("UPDATED_BY")).isEqualTo("agent");
        assertThat(((Number) row.get("VERSION")).intValue()).isEqualTo(1); // optimistic lock version bumped

        List<Map<String, Object>> installments = jdbc.queryForList(
                "SELECT installment_no, due_date, amount, status FROM installment WHERE policy_id = ? ORDER BY installment_no",
                policy);
        assertThat(installments).extracting(r -> ((Number) r.get("INSTALLMENT_NO")).intValue(),
                        r -> ((BigDecimal) r.get("AMOUNT")).toPlainString(), r -> r.get("STATUS"))
                .containsExactly(
                        tuple(1, "333.33", "PENDING"),
                        tuple(2, "333.33", "PENDING"),
                        tuple(3, "333.34", "PENDING")); // last one absorbs the rounding difference
        assertThat(jdbc.queryForObject("SELECT due_date FROM installment WHERE policy_id = ? AND installment_no = 3",
                LocalDate.class, policy)).isEqualTo(TODAY.plusDays(1).plusMonths(2));
    }

    @Test
    void issueRejectsInvalidQuotes() {
        long expired = quote(TODAY, new BigDecimal("1000"), 1, TODAY.minusDays(1));
        assertCode(() -> lifecycle.issue(expired, "agent"), "QUOTE_EXPIRED");

        long startPassed = quote(TODAY.minusDays(2), new BigDecimal("1000"), 1, TODAY.plusDays(5));
        assertCode(() -> lifecycle.issue(startPassed, "agent"), "QUOTE_START_DATE_PASSED");

        long first = quote(TODAY, new BigDecimal("1000"), 1, TODAY.plusDays(5));
        long second = quote(TODAY.plusDays(3), new BigDecimal("1000"), 1, TODAY.plusDays(5));
        lifecycle.issue(first, "agent");
        assertCode(() -> lifecycle.issue(first, "agent"), "POLICY_NOT_QUOTE");
        assertCode(() -> lifecycle.issue(second, "agent"), "POLICY_ALREADY_IN_FORCE");

        assertThatThrownBy(() -> lifecycle.issue(-1L, "agent")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void installmentPlanCannotBeGeneratedTwiceOrExceedProductLimit() {
        long policy = quote(TODAY, new BigDecimal("1000"), 2, TODAY.plusDays(5));

        assertThatThrownBy(() -> jdbc.update("BEGIN pkg_premium.generate_installments(?, 5); END;", policy))
                .isInstanceOf(UncategorizedSQLException.class).hasMessageContaining("TOO_MANY_INSTALLMENTS");

        jdbc.update("BEGIN pkg_premium.generate_installments(?); END;", policy);
        assertThatThrownBy(() -> jdbc.update("BEGIN pkg_premium.generate_installments(?); END;", policy))
                .hasMessageContaining("INSTALLMENTS_EXIST");
    }

    @Test
    void refundIsDayBased() {
        // 365-day term, cancelled after 73 days -> 292 unused days
        long policy = active(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), new BigDecimal("3650.00"));

        assertThat(refund(policy, LocalDate.of(2026, 3, 15))).isEqualByComparingTo("2920.00");
        assertThat(refund(policy, LocalDate.of(2025, 12, 1))).isEqualByComparingTo("3650.00"); // before start: all
        assertThat(refund(policy, LocalDate.of(2027, 1, 1))).isEqualByComparingTo("0.00");     // at the end: nothing
    }

    @Test
    void cancelStoresRefundAndCancelsOnlyUnpaidInstallments() {
        long policy = active(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), new BigDecimal("3650.00"));
        jdbc.update("BEGIN pkg_premium.generate_installments(?, 4); END;", policy);
        jdbc.update("UPDATE installment SET status = 'PAID', paid_at = SYSTIMESTAMP WHERE policy_id = ? AND installment_no = 1", policy);

        BigDecimal refund = lifecycle.cancel(policy, "  Araç satışı  ", LocalDate.of(2026, 3, 15), "agent");

        assertThat(refund).isEqualByComparingTo("2920.00");
        Map<String, Object> row = jdbc.queryForMap("SELECT status, cancel_reason, refund_amount, cancelled_at FROM policy WHERE id = ?", policy);
        assertThat(row.get("STATUS")).isEqualTo("CANCELLED");
        assertThat(row.get("CANCEL_REASON")).isEqualTo("Araç satışı");
        assertThat(row.get("CANCELLED_AT")).isNotNull();
        assertThat(jdbc.queryForList("SELECT status FROM installment WHERE policy_id = ? ORDER BY installment_no",
                String.class, policy)).containsExactly("PAID", "CANCELLED", "CANCELLED", "CANCELLED");
    }

    @Test
    void cancelRejectsInvalidRequests() {
        long quote = quote(TODAY, new BigDecimal("1000"), 1, TODAY.plusDays(5));
        assertCode(() -> lifecycle.cancel(quote, "Müşteri talebi", TODAY, "agent"), "POLICY_NOT_CANCELLABLE");

        long policy = active(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), new BigDecimal("1000"));
        assertCode(() -> lifecycle.cancel(policy, "   ", LocalDate.of(2026, 2, 1), "agent"), "CANCEL_REASON_REQUIRED");
        assertCode(() -> lifecycle.cancel(policy, "Müşteri talebi", LocalDate.of(2027, 2, 1), "agent"), "INVALID_CANCEL_DATE");
    }

    @Test
    void changeStatusEnforcesTransitionsWithReadableMessage() {
        long policy = active(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), new BigDecimal("1000"));

        jdbc.update("BEGIN pkg_policy.change_status(?, 'SUSPENDED', 'batch'); END;", policy);
        jdbc.update("BEGIN pkg_policy.change_status(?, 'ACTIVE', 'batch'); END;", policy);
        jdbc.update("BEGIN pkg_policy.change_status(?, 'EXPIRED', 'batch'); END;", policy);

        assertThatThrownBy(() -> jdbc.update("BEGIN pkg_policy.change_status(?, 'ACTIVE', 'batch'); END;", policy))
                .hasMessageContaining("INVALID_STATUS_TRANSITION|TST-" + policy
                        + " numaralı poliçenin durumu Süresi doldu iken Yürürlükte yapılamaz.");
    }

    @Test
    void auditTriggerRecordsPolicyChangesAsJson() {
        long policy = active(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), new BigDecimal("1000"));
        jdbc.update("BEGIN pkg_policy.change_status(?, 'SUSPENDED', 'overdue-job'); END;", policy);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT operation, changed_by,
                       JSON_VALUE(old_values, '$.status') AS old_status,
                       JSON_VALUE(new_values, '$.status') AS new_status,
                       JSON_VALUE(new_values, '$.grossPremium') AS gross
                  FROM audit_log
                 WHERE table_name = 'POLICY' AND record_id = ?
                 ORDER BY id
                """, policy);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("OPERATION", "INSERT").containsEntry("NEW_STATUS", "ACTIVE");
        assertThat(rows.get(0).get("OLD_STATUS")).isNull();
        assertThat(rows.get(1))
                .containsEntry("OPERATION", "UPDATE")
                .containsEntry("CHANGED_BY", "overdue-job")
                .containsEntry("OLD_STATUS", "ACTIVE")
                .containsEntry("NEW_STATUS", "SUSPENDED")
                .containsEntry("GROSS", "1000");
    }

    private long quote(LocalDate start, BigDecimal gross, int installments, LocalDate validUntil) {
        return fixtures.trafficPolicy(customer, vehicle, "QUOTE", start, start.plusYears(1), gross, installments, validUntil);
    }

    private long active(LocalDate start, LocalDate end, BigDecimal gross) {
        return fixtures.trafficPolicy(customer, vehicle, "ACTIVE", start, end, gross, 1, null);
    }

    private BigDecimal refund(long policy, LocalDate cancelDate) {
        return jdbc.queryForObject("SELECT pkg_policy.calculate_refund(?, ?) FROM dual", BigDecimal.class, policy, cancelDate);
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).extracting("code").isEqualTo(code);
    }
}
