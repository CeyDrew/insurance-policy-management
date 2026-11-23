package com.portfolio.insurance.policy;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quote -> policy -> cancellation flow through the REST API against Oracle.
 */
@IntegrationTest
@Transactional
class PolicyIntegrationTest {

    private static final RequestPostProcessor AGENT = as("agent", "AGENT");

    @Autowired
    private MockMvc mvc;

    private long customerId;
    private long vehicleId;

    @BeforeEach
    void createCustomerWithVehicle() throws Exception {
        customerId = postForId("/api/customers", """
                {"customerType":"INDIVIDUAL","nationalId":"%s","firstName":"Poliçe","lastName":"Testi",
                 "birthDate":"1980-01-01","cityId":%d}
                """.formatted(TestData.tckn(), cityId(34)));
        vehicleId = postForId("/api/customers/%d/vehicles".formatted(customerId), """
                {"plateNumber":"%s","chassisNumber":"%s","brand":"Fiat","model":"Egea","modelYear":2022,
                 "usageType":"PRIVATE","vehicleValue":1100000}
                """.formatted(TestData.plate(), TestData.chassis()));
    }

    @Test
    void quoteIssueAndCancelFlow() throws Exception {
        // 1. Quote
        DocumentContext quote = json(quote("TRAFFIC", vehicleId, null, LocalDate.now(), 4)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUOTE"))
                .andExpect(jsonPath("$.policyNumber", matchesPattern("TRF-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.ncdStep").value(4))
                .andExpect(jsonPath("$.agentName").value("Ayşe Yılmaz"))
                .andExpect(jsonPath("$.installments").isEmpty()));
        long policyId = quote.read("$.id", Long.class);
        BigDecimal net = quote.read("$.netPremium", BigDecimal.class);
        BigDecimal tax = quote.read("$.taxAmount", BigDecimal.class);
        BigDecimal gross = quote.read("$.grossPremium", BigDecimal.class);
        assertThat(net).isPositive();
        assertThat(net.add(tax)).isEqualByComparingTo(gross);

        // 2. Issue: installment plan adds up to the gross premium
        DocumentContext issued = json(mvc.perform(post("/api/policies/{id}/issue", policyId).with(AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.issuedAt").isNotEmpty())
                .andExpect(jsonPath("$.installments.length()").value(4))
                .andExpect(jsonPath("$.installments[*].status", everyItem(is("PENDING")))));
        List<Number> amounts = issued.read("$.installments[*].amount");
        BigDecimal total = amounts.stream().map(n -> new BigDecimal(n.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(gross);

        // Issuing twice is not allowed
        mvc.perform(post("/api/policies/{id}/issue", policyId).with(AGENT))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_QUOTE"));

        // 3. Cancel: day-based refund, open installments cancelled
        DocumentContext cancelled = json(mvc.perform(post("/api/policies/{id}/cancel", policyId).with(AGENT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Araç satışı\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Araç satışı"))
                .andExpect(jsonPath("$.installments[*].status", everyItem(is("CANCELLED")))));
        BigDecimal refund = cancelled.read("$.refundAmount", BigDecimal.class);
        assertThat(refund).isPositive().isLessThanOrEqualTo(gross);
    }

    @Test
    void secondPolicyOnSameVehicleIsRejectedWhileFirstIsInForce() throws Exception {
        long first = json(quote("TRAFFIC", vehicleId, null, LocalDate.now(), 1)).read("$.id", Long.class);
        mvc.perform(post("/api/policies/{id}/issue", first).with(AGENT)).andExpect(status().isOk());

        quote("TRAFFIC", vehicleId, null, LocalDate.now().plusDays(10), 1)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("POLICY_ALREADY_IN_FORCE"));

        // A different product on the same vehicle is fine
        quote("CASCO", vehicleId, null, LocalDate.now(), 1).andExpect(status().isCreated());
    }

    @Test
    void businessRulesAreValidated() throws Exception {
        quote("TRAFFIC", null, null, LocalDate.now(), 1)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VEHICLE_REQUIRED"));

        quote("TRAFFIC", vehicleId, null, LocalDate.now(), 9)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TOO_MANY_INSTALLMENTS"));

        quote("TRAFFIC", vehicleId, null, LocalDate.now().plusDays(90), 1)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("START_DATE_TOO_FAR"));

        quote("TRAFFIC", vehicleId, null, LocalDate.now().minusDays(1), 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").exists());

        long otherCustomersVehicle = json(mvc.perform(get("/api/policies").param("productCode", "TRAFFIC")
                .param("size", "1").with(AGENT))).read("$.content[0].id", Long.class);
        long seededVehicle = json(mvc.perform(get("/api/policies/{id}", otherCustomersVehicle).with(AGENT)))
                .read("$.vehicleId", Long.class);
        quote("CASCO", seededVehicle, null, LocalDate.now(), 1)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ASSET_OWNER_MISMATCH"));
    }

    @Test
    void healthQuoteNeedsNoAssetAndUsesBasePremium() throws Exception {
        quote("HEALTH", null, null, LocalDate.now().plusDays(1), 12)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyNumber", matchesPattern("SGL-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.insuredObject").value("Poliçe Testi"));
    }

    @Test
    void searchFindsPolicyByPlateWithoutSpaces() throws Exception {
        String plate = json(mvc.perform(get("/api/customers/{id}/vehicles", customerId).with(AGENT)))
                .read("$[0].plateNumber", String.class);
        long id = json(quote("TRAFFIC", vehicleId, null, LocalDate.now(), 1)).read("$.id", Long.class);

        mvc.perform(get("/api/policies").param("query", plate.replace(" ", "").toLowerCase()).with(AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].insuredObject").value(plate));
    }

    @Test
    void claimHandlerCanReadButNotQuote() throws Exception {
        RequestPostProcessor claims = as("claims", "CLAIM_HANDLER");
        mvc.perform(get("/api/policies").with(claims)).andExpect(status().isOk());
        mvc.perform(post("/api/policies/quotes").with(claims).contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson("TRAFFIC", vehicleId, null, LocalDate.now(), 1)))
                .andExpect(status().isForbidden());
    }

    private ResultActions quote(String productCode, Long vehicle, Long property, LocalDate start, int installments)
            throws Exception {
        return mvc.perform(post("/api/policies/quotes").with(AGENT).contentType(MediaType.APPLICATION_JSON)
                .content(quoteJson(productCode, vehicle, property, start, installments)));
    }

    private String quoteJson(String productCode, Long vehicle, Long property, LocalDate start, int installments)
            throws Exception {
        return """
                {"productId":%d,"customerId":%d,"vehicleId":%s,"propertyId":%s,"startDate":"%s","installmentCount":%d}
                """.formatted(productId(productCode), customerId, vehicle, property, start, installments);
    }

    private long productId(String code) throws Exception {
        return json(mvc.perform(get("/api/products").with(AGENT)))
                .read("$[?(@.code == '%s')].id".formatted(code), Long[].class)[0];
    }

    private long cityId(int plateCode) throws Exception {
        return json(mvc.perform(get("/api/cities").with(AGENT)))
                .read("$[?(@.plateCode == %d)].id".formatted(plateCode), Long[].class)[0];
    }

    private long postForId(String url, String body) throws Exception {
        return json(mvc.perform(post(url).with(AGENT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())).read("$.id", Long.class);
    }

    private static DocumentContext json(ResultActions result) throws Exception {
        return JsonPath.parse(result.andReturn().getResponse().getContentAsString());
    }

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
