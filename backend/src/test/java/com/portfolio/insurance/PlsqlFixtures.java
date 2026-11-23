package com.portfolio.insurance;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inserts rows with exactly the attributes a PL/SQL test needs, bypassing the application layer.
 * Meant for {@code @Transactional} tests so everything is rolled back.
 */
public class PlsqlFixtures {

    private final JdbcTemplate jdbc;

    public PlsqlFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long individual(LocalDate birthDate, int cityPlateCode) {
        long id = nextId("customer_seq");
        jdbc.update("""
                INSERT INTO customer (id, customer_type, national_id, first_name, last_name, birth_date, city_id)
                VALUES (?, 'INDIVIDUAL', ?, 'Test', 'Kişi', ?, (SELECT id FROM city WHERE plate_code = ?))
                """, id, TestData.tckn(), birthDate, cityPlateCode);
        return id;
    }

    public long corporate(int cityPlateCode) {
        long id = nextId("customer_seq");
        jdbc.update("""
                INSERT INTO customer (id, customer_type, tax_number, company_name, city_id)
                VALUES (?, 'CORPORATE', ?, 'Test A.Ş.', (SELECT id FROM city WHERE plate_code = ?))
                """, id, TestData.vkn(), cityPlateCode);
        return id;
    }

    public long vehicle(long customerId, int modelYear, BigDecimal value, String usageType) {
        long id = nextId("vehicle_seq");
        jdbc.update("""
                INSERT INTO vehicle (id, customer_id, plate_number, chassis_number, brand, model, model_year,
                                     usage_type, vehicle_value)
                VALUES (?, ?, ?, ?, 'Test', 'Model', ?, ?, ?)
                """, id, customerId, TestData.plate(), TestData.chassis(), modelYear, usageType, value);
        return id;
    }

    public long property(long customerId, int cityPlateCode, int buildingYear, BigDecimal value, String construction) {
        long id = nextId("property_seq");
        jdbc.update("""
                INSERT INTO property (id, customer_id, city_id, address_line, building_year, gross_area_m2,
                                      construction_type, property_value)
                VALUES (?, ?, (SELECT id FROM city WHERE plate_code = ?), 'Test Sok. 1', ?, 120, ?, ?)
                """, id, customerId, cityPlateCode, buildingYear, construction, value);
        return id;
    }

    /**
     * A TRAFFIC policy on the given vehicle. ACTIVE policies get issued_at; QUOTE policies a validity date.
     */
    public long trafficPolicy(long customerId, long vehicleId, String status, LocalDate start, LocalDate end,
                              BigDecimal grossPremium, int installmentCount, LocalDate quoteValidUntil) {
        long id = nextId("policy_seq");
        jdbc.update("""
                INSERT INTO policy (id, policy_number, product_id, customer_id, vehicle_id, agent_id, status,
                                    start_date, end_date, net_premium, tax_amount, gross_premium, installment_count,
                                    quote_valid_until, issued_at)
                VALUES (?, ?, (SELECT id FROM product WHERE code = 'TRAFFIC'), ?, ?,
                        (SELECT id FROM app_user WHERE username = 'agent'), ?, ?, ?, ?, 0, ?, ?, ?,
                        CASE WHEN ? = 'QUOTE' THEN NULL ELSE SYSTIMESTAMP END)
                """, id, "TST-" + id, customerId, vehicleId, status, start, end, grossPremium, grossPremium,
                installmentCount, quoteValidUntil, status);
        return id;
    }

    public void contact(long customerId, String email, String phone) {
        jdbc.update("UPDATE customer SET email = ?, phone = ? WHERE id = ?", email, phone, customerId);
    }

    /** Installment plan through PKG_PREMIUM (equal monthly installments from the start date, all PENDING). */
    public void installments(long policyId, int count) {
        jdbc.update("BEGIN pkg_premium.generate_installments(?, ?); END;", policyId, count);
    }

    private long nextId(String sequence) {
        return jdbc.queryForObject("SELECT " + sequence + ".NEXTVAL FROM dual", Long.class);
    }
}
