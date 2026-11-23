package com.portfolio.insurance.claim;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.PlsqlFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Claim from notification to payment through the REST API. */
@IntegrationTest
@Transactional
class ClaimIntegrationTest {

    private static final RequestPostProcessor AGENT = as("agent", "AGENT");
    private static final RequestPostProcessor HANDLER = as("claims", "CLAIM_HANDLER");
    private static final LocalDate TODAY = LocalDate.now();
    private static final byte[] PDF = "%PDF-1.4\n% test document\n".getBytes();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long policy;

    @BeforeEach
    void setUp() {
        PlsqlFixtures fixtures = new PlsqlFixtures(jdbc);
        long customer = fixtures.individual(LocalDate.of(1983, 3, 3), 35);
        long vehicle = fixtures.vehicle(customer, 2022, new BigDecimal("1200000"), "PRIVATE");
        policy = fixtures.trafficPolicy(customer, vehicle, "ACTIVE", TODAY.minusMonths(4), TODAY.plusMonths(8),
                new BigDecimal("6000"), 1, null);
    }

    @Test
    void claimGoesFromNotificationToPayment() throws Exception {
        long id = json(report(TODAY.minusDays(5), "Kavşakta yandan çarpma", "48000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.claimNumber", containsString("HSR-")))
                .andExpect(jsonPath("$.ncdStep").value(4))
                .andExpect(jsonPath("$.history[0].operation").value("INSERT")))
                .read("$.id", Long.class);

        // Agent attaches the accident report, the handler takes the file over.
        mvc.perform(multipart("/api/claims/{id}/documents", id)
                        .file(new MockMultipartFile("file", "tutanak.pdf", "application/pdf", PDF))
                        .param("documentType", "ACCIDENT_REPORT").with(AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("tutanak.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"));

        mvc.perform(post("/api/claims/{id}/assign", id).with(HANDLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.handlerUsername").value("claims"));

        mvc.perform(post("/api/claims/{id}/approve", id).with(HANDLER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedAmount\":45000,\"note\":\"Eksper raporu doğrultusunda\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAmount").value(45000));

        mvc.perform(post("/api/claims/{id}/pay", id).with(HANDLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAmount").value(45000))
                .andExpect(jsonPath("$.ncdStep").value(3)) // no-claims step lowered by PKG_CLAIM
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.history[*].toStatus", contains("REPORTED", "IN_REVIEW", "APPROVED", "PAID")));

        mvc.perform(get("/api/claims").param("mine", "true").param("status", "PAID").with(HANDLER))
                .andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    void documentsAreValidatedByContentAndDownloadedSafely() throws Exception {
        long id = json(report(TODAY.minusDays(2), "Cam kırılması", "3000")).read("$.id", Long.class);

        mvc.perform(multipart("/api/claims/{id}/documents", id)
                        .file(new MockMultipartFile("file", "virus.pdf", "application/pdf", "MZ not a pdf".getBytes()))
                        .param("documentType", "OTHER").with(AGENT))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_TYPE"));

        long documentId = json(mvc.perform(multipart("/api/claims/{id}/documents", id)
                        .file(new MockMultipartFile("file", "..\\..\\fotoğraf.pdf", "application/pdf", PDF))
                        .param("documentType", "PHOTO").with(AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("fotoğraf.pdf")))
                .read("$.id", Long.class);

        mvc.perform(get("/api/claims/{id}/documents/{doc}", id, documentId).with(HANDLER))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void reportingRulesAreEnforced() throws Exception {
        report(TODAY.minusMonths(6), "Teminat öncesi", "1000")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_COVERED"));

        report(TODAY.minusDays(1), "Aynı hasar", "1000").andExpect(status().isCreated());
        report(TODAY.minusDays(1), "aynı hasar", "1000")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLAIM_DUPLICATE"));

        report(TODAY.plusDays(1), "Gelecek", "1000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.incidentDate").exists());
    }

    @Test
    void rolesAndHandlerAssignmentAreEnforced() throws Exception {
        long id = json(report(TODAY.minusDays(3), "Park hasarı", "8000")).read("$.id", Long.class);

        mvc.perform(post("/api/claims").with(HANDLER).contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson(TODAY, "x", "1")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/claims/{id}/assign", id).with(AGENT)).andExpect(status().isForbidden());

        mvc.perform(post("/api/claims/{id}/approve", id).with(HANDLER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedAmount\":100}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CLAIM_TRANSITION"));

        mvc.perform(post("/api/claims/{id}/assign", id).with(HANDLER)).andExpect(status().isOk());
        mvc.perform(post("/api/claims/{id}/reject", id).with(HANDLER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/claims/{id}/reject", id).with(HANDLER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Teminat dışı hasar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("Teminat dışı hasar"));

        mvc.perform(multipart("/api/claims/{id}/documents", id)
                        .file(new MockMultipartFile("file", "geç.pdf", "application/pdf", PDF))
                        .param("documentType", "OTHER").with(AGENT))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CLAIM_CLOSED"));
    }

    private ResultActions report(LocalDate incidentDate, String description, String amount) throws Exception {
        return mvc.perform(post("/api/claims").with(AGENT).contentType(MediaType.APPLICATION_JSON)
                .content(reportJson(incidentDate, description, amount)));
    }

    private String reportJson(LocalDate incidentDate, String description, String amount) {
        return """
                {"policyId":%d,"incidentDate":"%s","description":"%s","estimatedAmount":%s,"incidentCityId":null}
                """.formatted(policy, incidentDate, description, amount);
    }

    private static DocumentContext json(ResultActions result) throws Exception {
        return JsonPath.parse(result.andReturn().getResponse().getContentAsString());
    }

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
