package com.portfolio.insurance.policy;

public enum PolicyStatus {
    /** Offer (teklif) with a calculated premium, not yet issued. */
    QUOTE,
    ACTIVE,
    /** Coverage paused because of overdue installments. */
    SUSPENDED,
    CANCELLED,
    EXPIRED,
    /** Term ended and a follow-up policy was issued. */
    RENEWED
}
