package com.portfolio.insurance.customer;

public record CustomerSummaryDto(Long id, CustomerType customerType, String displayName, String identityNumber,
                                 String email, String phone, String cityName) {
}
