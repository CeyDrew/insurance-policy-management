package com.portfolio.insurance.user;

public enum Role {
    ADMIN,
    /** Insurance agent (acente): manages customers, quotes and policies. */
    AGENT,
    /** Claim handler (hasar uzmanı): reviews and decides claims. */
    CLAIM_HANDLER;

    public String authority() {
        return "ROLE_" + name();
    }
}
