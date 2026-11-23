package com.portfolio.insurance.claim;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Whether a claim with a given incident date can be reported on a policy. Shared by the claim screen and the
 * CSV import job so both apply the same rule.
 */
public final class ClaimCoverage {

    private ClaimCoverage() {
    }

    /**
     * @param cancelledAt cancellation time of a cancelled policy: coverage ends on that day
     * @return a user-facing reason why the claim is not covered, or empty if it is
     */
    public static Optional<String> check(String policyStatus, LocalDate startDate, LocalDate endDate,
                                         LocalDateTime cancelledAt, LocalDate incidentDate, LocalDate today) {
        if ("QUOTE".equals(policyStatus)) {
            return Optional.of("Teklif aşamasındaki kayıt için hasar ihbarı yapılamaz.");
        }
        if (incidentDate.isAfter(today)) {
            return Optional.of("Hasar tarihi ileri bir tarih olamaz.");
        }
        LocalDate coverageEnd = cancelledAt != null ? cancelledAt.toLocalDate() : endDate;
        if (incidentDate.isBefore(startDate) || !incidentDate.isBefore(coverageEnd)) {
            return Optional.of("Hasar tarihi poliçe teminat süresi dışında (" + startDate + " - " + coverageEnd + ").");
        }
        return Optional.empty();
    }
}
