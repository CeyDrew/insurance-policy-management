package com.portfolio.insurance.batch.renewal;

import com.portfolio.insurance.reference.ProductCode;

import java.time.LocalDate;

/**
 * An active policy that ends within the renewal window and has no renewal quote yet.
 *
 * @param nextNcdStep no-claims step of the next term, from PKG_POLICY.NEXT_NCD_STEP
 */
record RenewalCandidate(long policyId, String policyNumber, long productId, ProductCode productCode, int termMonths,
                        long customerId, Long vehicleId, Long propertyId, long agentId, LocalDate endDate,
                        int installmentCount, int nextNcdStep, String customerName, String email, String phone) {
}
