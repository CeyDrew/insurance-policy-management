package com.portfolio.insurance.policy;

import com.portfolio.insurance.reference.ProductCode;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PolicySummaryDto(Long id, String policyNumber, ProductCode productCode, String productName,
                               Long customerId, String customerName, String insuredObject, PolicyStatus status,
                               LocalDate startDate, LocalDate endDate, BigDecimal grossPremium) {
}
