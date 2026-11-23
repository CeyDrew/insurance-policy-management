package com.portfolio.insurance.customer;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomerDto(Long id, CustomerType customerType, String displayName, String nationalId, String taxNumber,
                          String firstName, String lastName, String companyName, LocalDate birthDate, Gender gender,
                          String email, String phone, String addressLine, Long cityId, String cityName,
                          LocalDateTime createdAt, String createdBy, Integer version) {
}
