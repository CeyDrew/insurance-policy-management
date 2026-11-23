package com.portfolio.insurance.customer;

import com.jayway.jsonpath.JsonPath;
import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Transactional // every test rolls back its changes
class CustomerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void searchReturnsPagedAndFilteredResults() throws Exception {
        mvc.perform(get("/api/customers").param("type", "CORPORATE").param("size", "5").with(as("claims", "CLAIM_HANDLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(greaterThan(0)))
                .andExpect(jsonPath("$.content[*].customerType", everyItem(is("CORPORATE"))));
    }

    @Test
    void searchByUnknownSortPropertyReturns400() throws Exception {
        mvc.perform(get("/api/customers").param("sort", "doesNotExist").with(as("agent", "AGENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agentCreatesIndividualCustomer() throws Exception {
        String tckn = TestData.tckn();

        mvc.perform(post("/api/customers").with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(individualJson(tckn, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.displayName").value("Deneme Müşteri"))
                .andExpect(jsonPath("$.nationalId").value(tckn))
                .andExpect(jsonPath("$.cityName").value("İstanbul"))
                .andExpect(jsonPath("$.createdBy").value("agent"));

        mvc.perform(get("/api/customers").param("query", tckn).with(as("agent", "AGENT")))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void invalidIdentityNumbersAreReportedPerField() throws Exception {
        mvc.perform(post("/api/customers").with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(individualJson("12345678901", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nationalId").value("Geçerli bir T.C. kimlik numarası girin."));

        mvc.perform(post("/api/customers").with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerType":"CORPORATE","taxNumber":"1111111111","cityId":%d}
                                """.formatted(istanbulId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.taxNumber").exists())
                .andExpect(jsonPath("$.errors.companyName").value("Şirket unvanı zorunludur."));
    }

    @Test
    void duplicateNationalIdIsRejected() throws Exception {
        String tckn = TestData.tckn();
        create(individualJson(tckn, null));

        mvc.perform(post("/api/customers").with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(individualJson(tckn, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CUSTOMER_DUPLICATE_IDENTITY"));
    }

    @Test
    void updateWithStaleVersionReturns409() throws Exception {
        String tckn = TestData.tckn();
        long id = create(individualJson(tckn, null));

        mvc.perform(put("/api/customers/{id}", id).with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(individualJson(tckn, 0).replace("Deneme", "Güncel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Güncel"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(put("/api/customers/{id}", id).with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(individualJson(tckn, 0)))
                .andExpect(status().isConflict());
    }

    @Test
    void customerWithPoliciesCannotBeDeleted() throws Exception {
        Long seededCustomerWithPolicy = JsonPath.parse(mvc.perform(get("/api/policies").param("size", "1")
                        .with(as("admin", "ADMIN"))).andReturn().getResponse().getContentAsString())
                .read("$.content[0].customerId", Long.class);

        mvc.perform(delete("/api/customers/{id}", seededCustomerWithPolicy).with(as("admin", "ADMIN")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CUSTOMER_HAS_POLICIES"));
    }

    @Test
    void customerWithoutPoliciesCanBeDeletedByAdminOnly() throws Exception {
        long id = create(individualJson(TestData.tckn(), null));

        mvc.perform(delete("/api/customers/{id}", id).with(as("agent", "AGENT")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/customers/{id}", id).with(as("admin", "ADMIN")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/customers/{id}", id).with(as("admin", "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimHandlerCannotCreateCustomers() throws Exception {
        mvc.perform(post("/api/customers").with(as("claims", "CLAIM_HANDLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(individualJson(TestData.tckn(), null)))
                .andExpect(status().isForbidden());
    }

    private long create(String json) throws Exception {
        String body = mvc.perform(post("/api/customers").with(as("agent", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.id", Long.class);
    }

    private long istanbulId() throws Exception {
        String body = mvc.perform(get("/api/cities").with(as("agent", "AGENT"))).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$[?(@.plateCode == 34)].id", Long[].class)[0];
    }

    private String individualJson(String tckn, Integer version) throws Exception {
        return """
                {"customerType":"INDIVIDUAL","nationalId":"%s","firstName":"Deneme","lastName":"Müşteri",
                 "birthDate":"1985-04-12","gender":"FEMALE","email":"deneme@example.com","phone":"05321234567",
                 "addressLine":"Test Sok. No: 1","cityId":%d,"version":%s}
                """.formatted(tckn, istanbulId(), version);
    }

    static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
