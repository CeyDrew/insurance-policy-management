package com.portfolio.insurance.premium;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pricing rules of the insurer: premium calculation, installment plans and cancellation refunds.
 * The policy service only talks to this interface, so the implementation can live in the database
 * (PL/SQL packages) without the rest of the application knowing about it.
 */
public interface PremiumEngine {

    PremiumResult calculatePremium(PremiumRequest request);

    /**
     * Creates the installment plan of an issued policy from its gross premium, installment count and start date.
     * The policy row must already be flushed to the database.
     */
    void generateInstallments(Long policyId);

    /** Day-based (gün esaslı) refund of the unused period when the policy is cancelled on {@code cancelDate}. */
    BigDecimal calculateRefund(Long policyId, LocalDate cancelDate);
}
