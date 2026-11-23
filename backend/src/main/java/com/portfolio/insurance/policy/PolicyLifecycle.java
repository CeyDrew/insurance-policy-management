package com.portfolio.insurance.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Status changes of a policy. The rules (allowed transitions, quote validity, installment plan, refund)
 * are implemented in the database (PKG_POLICY); violations surface as {@code BusinessException}s.
 */
public interface PolicyLifecycle {

    /** QUOTE -> ACTIVE and creation of the installment plan. */
    void issue(Long policyId, String issuedBy);

    /**
     * ACTIVE/SUSPENDED -> CANCELLED; unpaid installments are cancelled.
     *
     * @return the day-based refund amount
     */
    BigDecimal cancel(Long policyId, String reason, LocalDate cancelDate, String cancelledBy);
}
