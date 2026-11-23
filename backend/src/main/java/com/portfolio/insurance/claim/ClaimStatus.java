package com.portfolio.insurance.claim;

public enum ClaimStatus {
    /** Notified (ihbar), waiting for a claim handler. */
    REPORTED,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    PAID
}
