package com.portfolio.insurance.report;

import com.portfolio.insurance.claim.ClaimStatus;
import com.portfolio.insurance.reference.ProductCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the dashboard shows, in one response.
 *
 * @param productionRefreshedAt when MV_PREMIUM_PRODUCTION_MONTHLY was last recomputed
 */
public record DashboardDto(LocalDateTime generatedAt, LocalDateTime productionRefreshedAt, Kpis kpis,
                           List<MonthlyProduction> monthlyProduction, List<ProductMix> productMix,
                           List<LossRatio> lossRatios, List<AgentPerformance> agents,
                           List<ClaimStatusCount> claimStatuses) {

    /**
     * @param grossWrittenYtd          gross premium of policies issued this calendar year
     * @param grossWrittenPreviousYtd  same period of the previous year, for the change indicator
     * @param lossRatio                incurred claims / earned premium over the whole portfolio
     * @param quoteConversion          issued / created quotes over the last 12 months
     * @param renewalsDue              active policies ending within 30 days
     */
    public record Kpis(long activePolicies, long suspendedPolicies, BigDecimal grossWrittenYtd,
                       BigDecimal grossWrittenPreviousYtd, BigDecimal earnedPremium, BigDecimal incurredClaims,
                       BigDecimal lossRatio, long openClaims, BigDecimal outstandingClaims,
                       long overdueInstallments, BigDecimal overdueAmount, BigDecimal quoteConversion,
                       long renewalsDue) {
    }

    /** @param month yyyy-MM */
    public record MonthlyProduction(String month, ProductCode productCode, String productName, long policyCount,
                                    BigDecimal grossPremium) {
    }

    public record ProductMix(ProductCode productCode, String productName, long activePolicies,
                             BigDecimal activeGrossPremium) {
    }

    public record LossRatio(ProductCode productCode, String productName, BigDecimal earnedPremium,
                            BigDecimal paidClaims, BigDecimal outstandingClaims, BigDecimal incurredClaims,
                            BigDecimal lossRatio) {
    }

    public record AgentPerformance(String agentName, long quoteCount, long issuedCount, BigDecimal conversionRate,
                                   BigDecimal grossWritten, long cancelledCount, BigDecimal lossRatio) {
    }

    public record ClaimStatusCount(ClaimStatus status, long count) {
    }
}
