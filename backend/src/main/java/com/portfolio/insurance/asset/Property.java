package com.portfolio.insurance.asset;

import com.portfolio.insurance.common.persistence.BaseEntity;
import com.portfolio.insurance.customer.Customer;
import com.portfolio.insurance.reference.City;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A residential property (konut) insured by the HOME product. */
@Getter
@Setter
@Entity
@Table(name = "property")
@NoArgsConstructor
public class Property extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "property_seq")
    @SequenceGenerator(name = "property_seq", sequenceName = "property_seq", allocationSize = 1)
    @Setter(AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(name = "address_line", nullable = false, length = 300)
    private String addressLine;

    @Column(name = "building_year", nullable = false)
    private Integer buildingYear;

    @Column(name = "gross_area_m2", nullable = false, precision = 7, scale = 2)
    private BigDecimal grossAreaM2;

    @Enumerated(EnumType.STRING)
    @Column(name = "construction_type", nullable = false, length = 30)
    private ConstructionType constructionType;

    @Column(name = "property_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal propertyValue;

    public String getDescription() {
        return "%s / %s".formatted(addressLine, city.getName());
    }
}
