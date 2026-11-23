package com.portfolio.insurance.batch.claimimport;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A validated claim notification, ready to be inserted with status REPORTED. */
record ClaimImportItem(ClaimCsvLine source, long policyId, LocalDate incidentDate, String description,
                       BigDecimal estimatedAmount, Long incidentCityId) {
}
