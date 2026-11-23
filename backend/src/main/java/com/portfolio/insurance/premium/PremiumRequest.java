package com.portfolio.insurance.premium;

import com.portfolio.insurance.reference.ProductCode;

import java.time.LocalDate;

/**
 * Input of a premium calculation.
 *
 * @param vehicleId  required for TRAFFIC and CASCO
 * @param propertyId required for HOME
 * @param ncdStep    no-claims discount step (hasarsızlık basamağı), 1-8
 */
public record PremiumRequest(ProductCode productCode, Long customerId, Long vehicleId, Long propertyId,
                             int ncdStep, LocalDate startDate) {
}
