package com.portfolio.insurance.batch.report;

import java.math.BigDecimal;

/** Premium production of one product/agent pair in the report month. */
record ProductionRow(String productName, String agentName, long policyCount, long cancelledCount,
                     BigDecimal netPremium, BigDecimal taxAmount, BigDecimal grossPremium, BigDecimal refunds) {
}
