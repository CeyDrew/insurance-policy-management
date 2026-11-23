package com.portfolio.insurance.premium;

import java.math.BigDecimal;

/**
 * @param grossPremium always {@code netPremium + taxAmount}
 */
public record PremiumResult(BigDecimal netPremium, BigDecimal taxAmount, BigDecimal grossPremium) {
}
