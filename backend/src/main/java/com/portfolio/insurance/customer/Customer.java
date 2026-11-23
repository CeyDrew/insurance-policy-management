package com.portfolio.insurance.customer;

import com.portfolio.insurance.common.persistence.BaseEntity;
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

import java.time.LocalDate;
import java.time.Period;

@Getter
@Setter
@Entity
@Table(name = "customer")
@NoArgsConstructor
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "customer_seq", allocationSize = 1)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;

    /** TCKN, individuals only. */
    @Column(name = "national_id", unique = true, length = 11)
    private String nationalId;

    /** VKN, corporate customers only. */
    @Column(name = "tax_number", unique = true, length = 10)
    private String taxNumber;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    @Column(name = "company_name", length = 150)
    private String companyName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    public boolean isIndividual() {
        return customerType == CustomerType.INDIVIDUAL;
    }

    public String getDisplayName() {
        return isIndividual() ? firstName + " " + lastName : companyName;
    }

    public String getIdentityNumber() {
        return isIndividual() ? nationalId : taxNumber;
    }

    /** Age in full years at the given date; null for corporate customers. */
    public Integer ageAt(LocalDate date) {
        return birthDate == null ? null : Period.between(birthDate, date).getYears();
    }

    /** Clears fields that do not belong to the customer type (the DB check constraint requires it). */
    void clearFieldsOfOtherType() {
        if (isIndividual()) {
            taxNumber = null;
            companyName = null;
        } else {
            nationalId = null;
            firstName = null;
            lastName = null;
            birthDate = null;
            gender = null;
        }
    }
}
