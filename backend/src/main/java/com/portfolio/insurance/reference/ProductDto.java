package com.portfolio.insurance.reference;

public record ProductDto(Long id, ProductCode code, String name, String description, AssetType assetType,
                         Integer maxInstallments, Integer termMonths) {
}
