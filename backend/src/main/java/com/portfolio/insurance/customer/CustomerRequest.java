package com.portfolio.insurance.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Create/update payload. Fields that do not apply to the customer type are ignored.
 *
 * @param version current version for updates (optimistic locking); omitted on create
 */
@ValidCustomerIdentity
public record CustomerRequest(
        @NotNull(message = "Müşteri tipi zorunludur.") CustomerType customerType,
        String nationalId,
        String taxNumber,
        @Size(max = 50, message = "Ad en fazla 50 karakter olabilir.") String firstName,
        @Size(max = 50, message = "Soyad en fazla 50 karakter olabilir.") String lastName,
        @Size(max = 150, message = "Unvan en fazla 150 karakter olabilir.") String companyName,
        @Past(message = "Doğum tarihi geçmişte olmalıdır.") LocalDate birthDate,
        Gender gender,
        @Email(message = "Geçerli bir e-posta adresi girin.")
        @Size(max = 150, message = "E-posta en fazla 150 karakter olabilir.") String email,
        @Pattern(regexp = "^0\\d{10}$", message = "Telefon 0 ile başlayan 11 haneli olmalıdır.") String phone,
        @Size(max = 300, message = "Adres en fazla 300 karakter olabilir.") String addressLine,
        @NotNull(message = "İl zorunludur.") Long cityId,
        Integer version) {
}
