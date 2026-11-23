package com.portfolio.insurance.asset;

import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Transactional
class AssetIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long customerId;
    private long cityId;

    @BeforeEach
    void setUp() {
        customerId = jdbc.queryForObject("SELECT MIN(id) FROM customer", Long.class);
        cityId = jdbc.queryForObject("SELECT MIN(id) FROM city", Long.class);
    }

    @Test
    void agentAddsVehicleWithNormalizedPlateAndItIsListed() throws Exception {
        String plate = TestData.plate();
        String compactPlate = plate.replace(" ", "").toLowerCase();

        mvc.perform(post("/api/customers/{id}/vehicles", customerId).with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(compactPlate, TestData.chassis().toLowerCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plateNumber").value(plate));

        mvc.perform(get("/api/customers/{id}/vehicles", customerId).with(as("claims", "CLAIM_HANDLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].plateNumber", hasItem(plate)));
    }

    @Test
    void duplicatePlateOrChassisIsRejected() throws Exception {
        String plate = TestData.plate();
        String chassis = TestData.chassis();
        mvc.perform(post("/api/customers/{id}/vehicles", customerId).with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(vehicleJson(plate, chassis)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/customers/{id}/vehicles", customerId).with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(vehicleJson(plate, TestData.chassis())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VEHICLE_DUPLICATE_PLATE"));

        mvc.perform(post("/api/customers/{id}/vehicles", customerId).with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(vehicleJson(TestData.plate(), chassis)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VEHICLE_DUPLICATE_CHASSIS"));
    }

    @Test
    void agentAddsPropertyAndItIsListed() throws Exception {
        mvc.perform(post("/api/customers/{id}/properties", customerId).with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cityId": %d, "addressLine": "Test Mah. Deneme Sok. No: 7", "buildingYear": 2015,
                                 "grossAreaM2": 120, "constructionType": "REINFORCED_CONCRETE", "propertyValue": 4500000}
                                """.formatted(cityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cityId").value(cityId))
                .andExpect(jsonPath("$.cityName").isNotEmpty());

        mvc.perform(get("/api/customers/{id}/properties", customerId).with(as("agent", "AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].addressLine", hasItem("Test Mah. Deneme Sok. No: 7")));
    }

    @Test
    void assetsOfUnknownCustomerAre404AndClaimHandlerCannotAdd() throws Exception {
        mvc.perform(get("/api/customers/{id}/vehicles", 999_999_999).with(as("agent", "AGENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/customers/{id}/properties", 999_999_999).with(as("agent", "AGENT")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/customers/{id}/vehicles", customerId).with(as("claims", "CLAIM_HANDLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(TestData.plate(), TestData.chassis())))
                .andExpect(status().isForbidden());
    }

    private static String vehicleJson(String plate, String chassis) {
        return """
                {"plateNumber": "%s", "chassisNumber": "%s", "brand": "Fiat", "model": "Egea", "modelYear": 2022,
                 "usageType": "PRIVATE", "vehicleValue": 850000}
                """.formatted(plate, chassis);
    }

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
