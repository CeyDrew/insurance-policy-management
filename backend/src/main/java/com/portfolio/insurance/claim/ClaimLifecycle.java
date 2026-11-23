package com.portfolio.insurance.claim;

import java.math.BigDecimal;

/**
 * Claim status changes, implemented in PKG_CLAIM. Rule violations (wrong status, not the assigned handler,
 * missing rejection reason...) surface as {@code BusinessException}s with the PL/SQL error code.
 */
public interface ClaimLifecycle {

    void assign(Long claimId, Long handlerId, String changedBy);

    void approve(Long claimId, BigDecimal approvedAmount, String note, String changedBy);

    void reject(Long claimId, String note, String changedBy);

    /** Pays the approved amount; for vehicle policies the no-claims step drops by one. */
    BigDecimal pay(Long claimId, String changedBy);
}
