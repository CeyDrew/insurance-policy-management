package com.portfolio.insurance.payment;

import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.PlsqlFixtures;
import com.portfolio.insurance.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PKG_PAYMENT through {@link PaymentProcessor}. */
@IntegrationTest
@Transactional
class PaymentPackageTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private PaymentProcessor processor;

    @Autowired
    private JdbcTemplate jdbc;

    private PlsqlFixtures fixtures;
    private long policy;

    @BeforeEach
    void setUp() {
        fixtures = new PlsqlFixtures(jdbc);
        long customer = fixtures.individual(LocalDate.of(1990, 1, 1), 6);
        fixtures.contact(customer, null, "05551112233");
        long vehicle = fixtures.vehicle(customer, 2020, new BigDecimal("700000"), "PRIVATE");
        LocalDate start = TODAY.minusDays(75);
        policy = fixtures.trafficPolicy(customer, vehicle, "ACTIVE", start, start.plusYears(1),
                new BigDecimal("4000.00"), 4, null);
        fixtures.installments(policy, 4);
        // Two overdue installments suspend the policy, as the overdue job would.
        jdbc.update("UPDATE installment SET status = 'OVERDUE' WHERE policy_id = ? AND installment_no IN (1, 2)", policy);
        jdbc.update("UPDATE policy SET status = 'SUSPENDED' WHERE id = ?", policy);
    }

    @Test
    void payingTheLastOverdueInstallmentReactivatesThePolicy() {
        PaymentProcessor.Outcome first = processor.payInstallment(installment(1), PaymentMethod.CREDIT_CARD, "agent");
        assertThat(first.reactivated()).isFalse();
        assertThat(policyStatus()).isEqualTo("SUSPENDED");

        PaymentProcessor.Outcome second = processor.payInstallment(installment(2), PaymentMethod.CASH, "agent");
        assertThat(second.reactivated()).isTrue();
        assertThat(policyStatus()).isEqualTo("ACTIVE");

        Map<String, Object> payment = jdbc.queryForMap(
                "SELECT amount, payment_method, reference_no, created_by FROM payment WHERE id = ?", second.paymentId());
        assertThat((BigDecimal) payment.get("AMOUNT")).isEqualByComparingTo("1000.00");
        assertThat(payment.get("PAYMENT_METHOD")).isEqualTo("CASH");
        assertThat((String) payment.get("REFERENCE_NO")).startsWith("TAH-");
        assertThat(payment.get("CREATED_BY")).isEqualTo("agent");
        assertThat(jdbc.queryForObject("SELECT status FROM installment WHERE id = ?", String.class, installment(2)))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification WHERE policy_id = ? AND notification_type = 'PAYMENT_RECEIVED'
                   AND channel = 'SMS'
                """, Integer.class, policy)).isEqualTo(2);
    }

    @Test
    void installmentsMustBePaidInOrderAndOnlyOnce() {
        assertCode(() -> processor.payInstallment(installment(2), PaymentMethod.CASH, "agent"), "EARLIER_INSTALLMENT_UNPAID");

        processor.payInstallment(installment(1), PaymentMethod.BANK_TRANSFER, "agent");
        assertCode(() -> processor.payInstallment(installment(1), PaymentMethod.CASH, "agent"), "INSTALLMENT_ALREADY_PAID");
    }

    @Test
    void cancelledInstallmentsCannotBeCollected() {
        jdbc.update("UPDATE installment SET status = 'CANCELLED' WHERE policy_id = ?", policy);

        assertCode(() -> processor.payInstallment(installment(1), PaymentMethod.CASH, "agent"), "INSTALLMENT_CANCELLED");
    }

    private long installment(int no) {
        return jdbc.queryForObject("SELECT id FROM installment WHERE policy_id = ? AND installment_no = ?",
                Long.class, policy, no);
    }

    private String policyStatus() {
        return jdbc.queryForObject("SELECT status FROM policy WHERE id = ?", String.class, policy);
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).extracting("code").isEqualTo(code);
    }
}
