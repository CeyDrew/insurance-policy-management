package com.portfolio.insurance.db;

import com.portfolio.insurance.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs all Flyway migrations against a clean Oracle container and checks the schema and seed data.
 */
@SpringBootTest
@Transactional // roll back rows inserted by constraint tests
@Import(TestcontainersConfiguration.class)
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allMigrationsAreApplied() {
        Integer failed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = 0", Integer.class);
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL", Integer.class);

        assertThat(failed).isZero();
        assertThat(applied).isGreaterThanOrEqualTo(8);
    }

    @Test
    void referenceDataIsSeeded() {
        assertThat(count("city")).isEqualTo(81);
        assertThat(jdbc.queryForList("SELECT code FROM product ORDER BY code", String.class))
                .containsExactly("CASCO", "HEALTH", "HOME", "TRAFFIC");
        assertThat(jdbc.queryForList("SELECT DISTINCT role FROM app_user", String.class))
                .containsExactlyInAnyOrder("ADMIN", "AGENT", "CLAIM_HANDLER");
        assertThat(count("tariff_parameter")).isPositive();
    }

    @Test
    void demoDataIsSeeded() {
        assertThat(count("customer")).isEqualTo(50);
        assertThat(count("policy")).isEqualTo(200);
        assertThat(count("installment")).isPositive();
        assertThat(count("payment")).isPositive();
        assertThat(count("claim")).isPositive();
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM policy", String.class))
                .contains("QUOTE", "ACTIVE", "CANCELLED", "EXPIRED", "RENEWED");
    }

    @Test
    void seededIdentityNumbersHaveValidChecksums() {
        List<String> nationalIds = jdbc.queryForList(
                "SELECT national_id FROM customer WHERE customer_type = 'INDIVIDUAL'", String.class);
        List<String> taxNumbers = jdbc.queryForList(
                "SELECT tax_number FROM customer WHERE customer_type = 'CORPORATE'", String.class);

        assertThat(nationalIds).isNotEmpty().allMatch(DatabaseMigrationTest::isValidTckn);
        assertThat(taxNumbers).isNotEmpty().allMatch(DatabaseMigrationTest::isValidVkn);
    }

    @Test
    void seededPoliciesAreConsistent() {
        // Installments of every issued policy add up to its gross premium.
        Integer mismatched = jdbc.queryForObject("""
                SELECT COUNT(*) FROM policy p
                 WHERE p.status <> 'QUOTE'
                   AND p.gross_premium <> (SELECT SUM(i.amount) FROM installment i WHERE i.policy_id = p.id)
                """, Integer.class);
        // Vehicle products point to a vehicle, home to a property, health to neither.
        Integer wrongAsset = jdbc.queryForObject("""
                SELECT COUNT(*) FROM policy p JOIN product pr ON pr.id = p.product_id
                 WHERE (pr.asset_type = 'VEHICLE' AND p.vehicle_id IS NULL)
                    OR (pr.asset_type = 'PROPERTY' AND p.property_id IS NULL)
                    OR (pr.asset_type = 'PERSON' AND (p.vehicle_id IS NOT NULL OR p.property_id IS NOT NULL))
                """, Integer.class);

        assertThat(mismatched).isZero();
        assertThat(wrongAsset).isZero();
    }

    @Test
    void checkConstraintsRejectInvalidRows() {
        Long cityId = jdbc.queryForObject("SELECT id FROM city WHERE plate_code = 34", Long.class);

        // Individual customer without a national id.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO customer (customer_type, first_name, last_name, birth_date, city_id)
                VALUES ('INDIVIDUAL', 'Test', 'User', DATE '1990-01-01', ?)
                """, cityId)).isInstanceOf(DataIntegrityViolationException.class);

        // Unknown policy status.
        assertThatThrownBy(() -> jdbc.update("UPDATE policy SET status = 'DRAFT' WHERE ROWNUM = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static boolean isValidTckn(String value) {
        if (value == null || !value.matches("[1-9]\\d{10}")) {
            return false;
        }
        int[] d = value.chars().map(c -> c - '0').toArray();
        int odd = d[0] + d[2] + d[4] + d[6] + d[8];
        int even = d[1] + d[3] + d[5] + d[7];
        int tenth = Math.floorMod(odd * 7 - even, 10);
        int eleventh = (odd + even + d[9]) % 10;
        return d[9] == tenth && d[10] == eleventh;
    }

    private static boolean isValidVkn(String value) {
        if (value == null || !value.matches("\\d{10}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int tmp = (value.charAt(i) - '0' + 9 - i) % 10;
            int v = (int) ((tmp * Math.pow(2, 9 - i)) % 9);
            if (tmp != 0 && v == 0) {
                v = 9;
            }
            sum += v;
        }
        return (10 - sum % 10) % 10 == value.charAt(9) - '0';
    }
}
