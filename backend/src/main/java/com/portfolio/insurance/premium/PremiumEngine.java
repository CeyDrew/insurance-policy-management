package com.portfolio.insurance.premium;

/**
 * Prices a quote. The tariff rules live in the database (PKG_PREMIUM); callers only depend on this interface.
 */
public interface PremiumEngine {

    PremiumResult calculatePremium(PremiumRequest request);
}
