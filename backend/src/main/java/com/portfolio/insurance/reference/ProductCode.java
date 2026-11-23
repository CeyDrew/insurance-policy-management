package com.portfolio.insurance.reference;

public enum ProductCode {
    TRAFFIC("TRF"),
    CASCO("KSK"),
    HOME("KNT"),
    HEALTH("SGL");

    /** Prefix of policy numbers, e.g. TRF-2026-000123. */
    private final String policyPrefix;

    ProductCode(String policyPrefix) {
        this.policyPrefix = policyPrefix;
    }

    public String policyPrefix() {
        return policyPrefix;
    }
}
