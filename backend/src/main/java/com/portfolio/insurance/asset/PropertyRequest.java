package com.portfolio.insurance.asset;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PropertyRequest(
        @NotNull(message = "İl zorunludur.") Long cityId,
        @NotBlank(message = "Adres zorunludur.") @Size(max = 300, message = "Adres en fazla 300 karakter olabilir.")
        String addressLine,
        @NotNull(message = "Yapım yılı zorunludur.")
        @Min(value = 1800, message = "Yapım yılı geçersiz.")
        @Max(value = 2100, message = "Yapım yılı geçersiz.")
        Integer buildingYear,
        @NotNull(message = "Brüt alan zorunludur.")
        @Positive(message = "Brüt alan pozitif olmalıdır.")
        @DecimalMax(value = "10000", message = "Brüt alan en fazla 10.000 m² olabilir.")
        BigDecimal grossAreaM2,
        @NotNull(message = "Yapı tipi zorunludur.") ConstructionType constructionType,
        @NotNull(message = "Konut değeri zorunludur.")
        @Positive(message = "Konut değeri pozitif olmalıdır.")
        @DecimalMax(value = "1000000000", message = "Konut değeri çok yüksek.")
        BigDecimal propertyValue) {
}
