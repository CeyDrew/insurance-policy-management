package com.portfolio.insurance.policy;

import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.premium.PremiumResult;
import com.portfolio.insurance.reference.Product;
import com.portfolio.insurance.reference.ProductCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** State transitions of {@link Policy}, without Spring or a database. */
class PolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 14, 30);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Test
    void quoteRunsForTheProductTermAndStartsAsQuote() {
        Policy policy = quote(TODAY.plusDays(5), TODAY.plusDays(15));

        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.QUOTE);
        assertThat(policy.getEndDate()).isEqualTo(TODAY.plusDays(5).plusMonths(12));
        assertThat(policy.getGrossPremium()).isEqualByComparingTo("1050.00");
    }

    @Test
    void issueActivatesQuote() {
        Policy policy = quote(TODAY, TODAY.plusDays(15));

        policy.issue(NOW);

        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
        assertThat(policy.getIssuedAt()).isEqualTo(NOW);
    }

    @Test
    void expiredQuoteCannotBeIssued() {
        Policy policy = quote(TODAY, TODAY.minusDays(1));

        assertThatThrownBy(() -> policy.issue(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("QUOTE_EXPIRED");
    }

    @Test
    void quoteWhoseStartDatePassedCannotBeIssued() {
        Policy policy = quote(TODAY.minusDays(1), TODAY.plusDays(5));

        assertThatThrownBy(() -> policy.issue(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("QUOTE_START_DATE_PASSED");
    }

    @Test
    void onlyActiveOrSuspendedPoliciesCanBeCancelled() {
        Policy policy = quote(TODAY, TODAY.plusDays(15));

        assertThatThrownBy(() -> policy.cancel("Müşteri talebi", NOW, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("POLICY_NOT_CANCELLABLE");

        policy.issue(NOW);
        policy.cancel("Müşteri talebi", NOW, new BigDecimal("500.00"));

        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.CANCELLED);
        assertThat(policy.getCancelReason()).isEqualTo("Müşteri talebi");
        assertThat(policy.getRefundAmount()).isEqualByComparingTo("500.00");
    }

    private static Policy quote(LocalDate startDate, LocalDate validUntil) {
        Product product = Mockito.mock(Product.class);
        when(product.getTermMonths()).thenReturn(12);
        when(product.getCode()).thenReturn(ProductCode.TRAFFIC);
        PremiumResult premium = new PremiumResult(new BigDecimal("1000.00"), new BigDecimal("50.00"),
                new BigDecimal("1050.00"));
        return Policy.quote(123L, product, null, null, null, null, startDate, 4, 2, premium, validUntil);
    }
}
