package com.portfolio.insurance.policy;

import com.portfolio.insurance.reference.ProductCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Policy detail including its installment plan.
 *
 * @param insuredObject human-readable vehicle / property description, or the customer for health products
 */
public record PolicyDto(Long id, String policyNumber, PolicyStatus status,
                        Long productId, ProductCode productCode, String productName,
                        Long customerId, String customerName, String customerIdentityNumber,
                        Long vehicleId, Long propertyId, String insuredObject,
                        String agentName, LocalDate startDate, LocalDate endDate, Integer ncdStep,
                        BigDecimal netPremium, BigDecimal taxAmount, BigDecimal grossPremium, Integer installmentCount,
                        LocalDate quoteValidUntil, LocalDateTime issuedAt, LocalDateTime cancelledAt,
                        String cancelReason, BigDecimal refundAmount,
                        Long renewedFromPolicyId, String renewedFromPolicyNumber,
                        LocalDateTime createdAt, List<InstallmentDto> installments) {
}
