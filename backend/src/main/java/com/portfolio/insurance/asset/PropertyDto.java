package com.portfolio.insurance.asset;

import java.math.BigDecimal;

public record PropertyDto(Long id, Long customerId, Long cityId, String cityName, String addressLine,
                          Integer buildingYear, BigDecimal grossAreaM2, ConstructionType constructionType,
                          BigDecimal propertyValue, String description) {
}
