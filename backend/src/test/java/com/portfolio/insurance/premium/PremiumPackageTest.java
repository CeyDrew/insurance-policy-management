package com.portfolio.insurance.premium;

import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.PlsqlFixtures;
import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.reference.ProductCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PKG_PREMIUM against the seeded tariff, through {@link PremiumEngine} (PL/SQL + SimpleJdbcCall).
 * Expected values are calculated by hand from V6__seed_reference_data; see docs/plsql.md for the formula.
 */
@IntegrationTest
@Transactional
class PremiumPackageTest {

    /** Fixed start date so ages and vehicle/building ages do not drift with the calendar. */
    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final LocalDate BORN_1989 = LocalDate.of(1989, 1, 1); // 37 years old on START
    private static final int ISTANBUL = 34;                              // risk factor 1.350
    private static final int AGRI = 4;                                   // risk factor 0.900

    @Autowired
    private PremiumEngine premiumEngine;

    @Autowired
    private JdbcTemplate jdbc;

    private PlsqlFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new PlsqlFixtures(jdbc);
    }

    @Test
    void trafficPremium() {
        long customer = fixtures.individual(BORN_1989, ISTANBUL);
        long vehicle = fixtures.vehicle(customer, 2020, new BigDecimal("1000000"), "PRIVATE");

        // 4500 base x 1.35 city x 1.00 driver(37) x 1.05 vehicle age(6) x 1.00 private x 1.00 step 4 = 6378.75
        PremiumResult result = calculate(ProductCode.TRAFFIC, customer, vehicle, null, 4);

        assertThat(result.netPremium()).isEqualByComparingTo("6378.75");
        assertThat(result.taxAmount()).isEqualByComparingTo("318.94");      // 5% gider vergisi, rounded half up
        assertThat(result.grossPremium()).isEqualByComparingTo("6697.69");
    }

    @Test
    void youngDriverTaxiAndLowNoClaimsStepIncreaseTrafficPremium() {
        long customer = fixtures.individual(LocalDate.of(2004, 3, 1), ISTANBUL); // 22 years old
        long vehicle = fixtures.vehicle(customer, 2020, new BigDecimal("1000000"), "TAXI");

        // 4500 x 1.35 x 1.40 driver(22) x 1.05 x 1.60 taxi x 1.35 step 1 = 19289.34
        assertThat(calculate(ProductCode.TRAFFIC, customer, vehicle, null, 1).netPremium())
                .isEqualByComparingTo("19289.34");
    }

    @Test
    void cascoPremiumIsBasedOnVehicleValue() {
        long customer = fixtures.individual(BORN_1989, ISTANBUL);
        long vehicle = fixtures.vehicle(customer, 2020, new BigDecimal("1000000"), "PRIVATE");

        // 1,000,000 x 0.022 x 1.35 x 1.00 driver x 1.10 vehicle age(6) x 1.00 x 0.75 step 6 = 24502.50
        PremiumResult result = calculate(ProductCode.CASCO, customer, vehicle, null, 6);

        assertThat(result.netPremium()).isEqualByComparingTo("24502.50");
        assertThat(result.taxAmount()).isEqualByComparingTo("1225.13");
        assertThat(result.grossPremium()).isEqualByComparingTo("25727.63");
    }

    @Test
    void homePremiumUsesPropertyCityBuildingAgeAndConstruction() {
        long customer = fixtures.individual(BORN_1989, AGRI); // customer's own city must not matter
        long property = fixtures.property(customer, ISTANBUL, 2010, new BigDecimal("5000000"), "MASONRY");

        // 5,000,000 x 0.0015 x 1.35 x 1.15 building age(16) x 1.30 masonry = 15136.875 -> 15136.88
        PremiumResult result = calculate(ProductCode.HOME, customer, null, property, 4);

        assertThat(result.netPremium()).isEqualByComparingTo("15136.88");
        assertThat(result.grossPremium()).isEqualByComparingTo("15893.72");
    }

    @Test
    void healthPremiumDependsOnInsuredAgeAndIsTaxFree() {
        long adult = fixtures.individual(BORN_1989, ISTANBUL);
        long senior = fixtures.individual(LocalDate.of(1961, 1, 1), ISTANBUL); // 65

        assertThat(calculate(ProductCode.HEALTH, adult, null, null, 4).grossPremium()).isEqualByComparingTo("9000.00");
        assertThat(calculate(ProductCode.HEALTH, senior, null, null, 4).grossPremium()).isEqualByComparingTo("18900.00");
    }

    @Test
    void minimumPremiumApplies() {
        long customer = fixtures.individual(BORN_1989, AGRI);
        long vehicle = fixtures.vehicle(customer, 2024, new BigDecimal("900000"), "PRIVATE");

        // 4500 x 0.90 x 1.00 x 1.00 x 1.00 x 0.60 step 8 = 2430 < min premium 2500
        assertThat(calculate(ProductCode.TRAFFIC, customer, vehicle, null, 8).netPremium()).isEqualByComparingTo("2500.00");
    }

    @Test
    void corporateCustomersSkipTheDriverAgeFactor() {
        long company = fixtures.corporate(ISTANBUL);
        long vehicle = fixtures.vehicle(company, 2020, new BigDecimal("1000000"), "COMMERCIAL");

        // 4500 x 1.35 x 1.05 x 1.30 commercial x 1.00 = 8292.38
        assertThat(calculate(ProductCode.TRAFFIC, company, vehicle, null, 4).netPremium()).isEqualByComparingTo("8292.38");
    }

    @Test
    void functionAndProcedureAgree() {
        long customer = fixtures.individual(BORN_1989, ISTANBUL);
        long vehicle = fixtures.vehicle(customer, 2020, new BigDecimal("1000000"), "PRIVATE");

        BigDecimal fromFunction = jdbc.queryForObject(
                "SELECT pkg_premium.calculate_premium('CASCO', ?, ?, NULL, 6, DATE '2026-06-01') FROM dual",
                BigDecimal.class, customer, vehicle);

        assertThat(fromFunction).isEqualByComparingTo(calculate(ProductCode.CASCO, customer, vehicle, null, 6).netPremium());
    }

    @Test
    void driverOutsideTariffIsRejected() {
        long customer = fixtures.individual(LocalDate.of(2009, 1, 1), ISTANBUL); // 17
        long vehicle = fixtures.vehicle(customer, 2020, new BigDecimal("1000000"), "PRIVATE");

        assertThatThrownBy(() -> calculate(ProductCode.TRAFFIC, customer, vehicle, null, 4))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Sürücü yaşı (17) için tarife bulunmuyor.")
                .extracting("code").isEqualTo("TARIFF_NOT_FOUND");
    }

    @Test
    void invalidInputsAreRejectedWithCodes() {
        long person = fixtures.individual(BORN_1989, ISTANBUL);
        long company = fixtures.corporate(ISTANBUL);
        long otherVehicle = fixtures.vehicle(company, 2020, new BigDecimal("1000000"), "PRIVATE");

        assertCode(() -> calculate(ProductCode.TRAFFIC, person, null, null, 4), "VEHICLE_REQUIRED");
        assertCode(() -> calculate(ProductCode.TRAFFIC, person, otherVehicle, null, 4), "ASSET_OWNER_MISMATCH");
        assertCode(() -> calculate(ProductCode.HOME, person, null, null, 4), "PROPERTY_REQUIRED");
        assertCode(() -> calculate(ProductCode.HEALTH, company, null, null, 4), "INDIVIDUAL_REQUIRED");
        assertCode(() -> calculate(ProductCode.TRAFFIC, person, otherVehicle, null, 9), "INVALID_NCD_STEP");
    }

    private PremiumResult calculate(ProductCode product, long customer, Long vehicle, Long property, int ncdStep) {
        return premiumEngine.calculatePremium(new PremiumRequest(product, customer, vehicle, property, ncdStep, START));
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).extracting("code").isEqualTo(code);
    }
}
