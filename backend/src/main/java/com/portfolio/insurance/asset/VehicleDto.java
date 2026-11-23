package com.portfolio.insurance.asset;

import java.math.BigDecimal;

public record VehicleDto(Long id, Long customerId, String plateNumber, String chassisNumber, String brand, String model,
                         Integer modelYear, UsageType usageType, BigDecimal vehicleValue, String description) {
}
