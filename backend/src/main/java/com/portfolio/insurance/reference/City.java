package com.portfolio.insurance.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/** Reference data (81 provinces), maintained by migrations only. */
@Getter
@Entity
@Immutable
@Table(name = "city")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class City {

    @Id
    private Long id;

    @Column(name = "plate_code", nullable = false)
    private Integer plateCode;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "risk_factor", nullable = false, precision = 5, scale = 3)
    private BigDecimal riskFactor;
}
