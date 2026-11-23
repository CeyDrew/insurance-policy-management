package com.portfolio.insurance.payment;

import com.jayway.jsonpath.JsonPath;
import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.PlsqlFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Transactional
class PaymentIntegrationTest {

    private static final RequestPostProcessor AGENT = as("agent", "AGENT");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long policy;

    @BeforeEach
    void setUp() {
        PlsqlFixtures fixtures = new PlsqlFixtures(jdbc);
        long customer = fixtures.individual(LocalDate.of(1979, 9, 9), 16);
        long vehicle = fixtures.vehicle(customer, 2019, new BigDecimal("650000"), "PRIVATE");
        LocalDate start = LocalDate.now().minusDays(50);
        policy = fixtures.trafficPolicy(customer, vehicle, "ACTIVE", start, start.plusYears(1),
                new BigDecimal("3000.00"), 3, null);
        fixtures.installments(policy, 3);
        jdbc.update("UPDATE installment SET status = 'OVERDUE' WHERE policy_id = ? AND installment_no = 1", policy);
    }

    @Test
    void listsUnpaidInstallmentsWithOverdueDays() throws Exception {
        mvc.perform(get("/api/installments").param("policyId", String.valueOf(policy)).param("unpaidOnly", "true")
                        .with(AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].installmentNo").value(1))
                .andExpect(jsonPath("$.content[0].daysOverdue").value(50))
                .andExpect(jsonPath("$.content[*].status", everyItem(oneOf("PENDING", "OVERDUE"))));

        mvc.perform(get("/api/installments").param("status", "OVERDUE").param("size", "5").with(AGENT))
                .andExpect(jsonPath("$.totalElements").value(greaterThan(0)))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("OVERDUE"))));
    }

    @Test
    void recordsPayment() throws Exception {
        long first = jdbc.queryForObject(
                "SELECT id FROM installment WHERE policy_id = ? AND installment_no = 1", Long.class, policy);

        mvc.perform(post("/api/installments/{id}/payments", first).with(AGENT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CREDIT_CARD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.installmentNo").value(1))
                .andExpect(jsonPath("$.referenceNo", org.hamcrest.Matchers.startsWith("TAH-")))
                .andExpect(jsonPath("$.policyReactivated").value(false));

        String body = mvc.perform(get("/api/policies/{id}", policy).with(AGENT)).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.installments[0].status")).isEqualTo("PAID");
    }

    @Test
    void validatesRequestAndRoles() throws Exception {
        mvc.perform(post("/api/installments/{id}/payments", 1).with(AGENT).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paymentMethod").exists());

        mvc.perform(get("/api/installments").with(as("claims", "CLAIM_HANDLER"))).andExpect(status().isForbidden());

        mvc.perform(post("/api/installments/{id}/payments", -1).with(AGENT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
