package com.portfolio.insurance.policy;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * @param vehicleId  required for vehicle products (TRAFFIC, CASCO)
 * @param propertyId required for HOME
 */
public record QuoteRequest(
        @NotNull(message = "Ürün zorunludur.") Long productId,
        @NotNull(message = "Müşteri zorunludur.") Long customerId,
        Long vehicleId,
        Long propertyId,
        @NotNull(message = "Başlangıç tarihi zorunludur.")
        @FutureOrPresent(message = "Başlangıç tarihi bugün veya sonrası olmalıdır.")
        LocalDate startDate,
        @NotNull(message = "Taksit sayısı zorunludur.")
        @Min(value = 1, message = "Taksit sayısı en az 1 olmalıdır.")
        @Max(value = 12, message = "Taksit sayısı en fazla 12 olabilir.")
        Integer installmentCount) {
}
