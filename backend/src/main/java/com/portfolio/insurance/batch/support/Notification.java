package com.portfolio.insurance.batch.support;

/**
 * A notification row produced by a batch job. Sending is simulated: rows stay PENDING in the NOTIFICATION table.
 *
 * @param type notification_type, e.g. RENEWAL_OFFER
 */
public record Notification(Long customerId, Long policyId, String type, String email, String phone, String subject,
                           String body) {

    /** E-mail if the customer has one, SMS otherwise, nothing if neither is known. */
    String channel() {
        return email != null ? "EMAIL" : "SMS";
    }

    String recipient() {
        return email != null ? email : phone;
    }

    boolean deliverable() {
        return recipient() != null;
    }
}
