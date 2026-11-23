package com.portfolio.insurance.reference;

import com.portfolio.insurance.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.type.NumericBooleanConverter;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private ProductCode code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;

    @Column(name = "base_premium", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePremium;

    @Column(name = "min_premium", nullable = false, precision = 15, scale = 2)
    private BigDecimal minPremium;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "max_installments", nullable = false)
    private Integer maxInstallments;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Convert(converter = NumericBooleanConverter.class)
    @Column(nullable = false)
    private boolean active;
}
