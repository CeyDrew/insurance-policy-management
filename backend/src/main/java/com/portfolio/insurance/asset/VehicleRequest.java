package com.portfolio.insurance.asset;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * @param plateNumber Turkish plate, e.g. "34 ABC 123" (normalized to upper case with single spaces)
 */
public record VehicleRequest(
        @NotBlank(message = "Plaka zorunludur.")
        @Pattern(regexp = "^(0[1-9]|[1-7]\\d|8[01])\\s*[A-Za-z]{1,3}\\s*\\d{2,4}$",
                message = "Geçerli bir plaka girin (örn. 34 ABC 123).")
        String plateNumber,
        @NotBlank(message = "Şasi numarası zorunludur.")
        @Pattern(regexp = "^[A-HJ-NPR-Za-hj-npr-z0-9]{17}$", message = "Şasi numarası 17 karakter olmalıdır (I, O, Q hariç).")
        String chassisNumber,
        @NotBlank(message = "Marka zorunludur.") @Size(max = 50) String brand,
        @NotBlank(message = "Model zorunludur.") @Size(max = 50) String model,
        @NotNull(message = "Model yılı zorunludur.")
        @Min(value = 1950, message = "Model yılı 1950 veya sonrası olmalıdır.")
        @Max(value = 2100, message = "Model yılı geçersiz.")
        Integer modelYear,
        @NotNull(message = "Kullanım tipi zorunludur.") UsageType usageType,
        @NotNull(message = "Araç değeri zorunludur.")
        @Positive(message = "Araç değeri pozitif olmalıdır.")
        @DecimalMax(value = "100000000", message = "Araç değeri çok yüksek.")
        BigDecimal vehicleValue) {
}
