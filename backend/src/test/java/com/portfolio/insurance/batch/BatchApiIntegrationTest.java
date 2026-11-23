package com.portfolio.insurance.batch;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.portfolio.insurance.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Monitoring API: launches run asynchronously, so tests poll the execution until it finishes. */
@IntegrationTest
class BatchApiIntegrationTest {

    private static final RequestPostProcessor ADMIN = as("admin", "ADMIN");

    @Autowired
    private MockMvc mvc;

    @Test
    void listsJobsForAdminsOnly() throws Exception {
        mvc.perform(get("/api/batch/jobs").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[*].name", hasItems(BatchJobs.POLICY_RENEWAL, BatchJobs.OVERDUE_INSTALLMENT,
                        BatchJobs.OVERDUE_INSTALLMENT_PLSQL, BatchJobs.POLICY_EXPIRATION, BatchJobs.CLAIM_IMPORT,
                        BatchJobs.MONTHLY_PRODUCTION_REPORT)));

        mvc.perform(get("/api/batch/jobs").with(as("agent", "AGENT"))).andExpect(status().isForbidden());
    }

    @Test
    void launchesJobAndReportsStepStatistics() throws Exception {
        long id = json(mvc.perform(post("/api/batch/jobs/{job}/executions", BatchJobs.POLICY_EXPIRATION).with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"parameters\":{\"runDate\":\"2026-01-15\"}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobName").value(BatchJobs.POLICY_EXPIRATION))
                .andExpect(jsonPath("$.parameters.runDate").value("2026-01-15"))
                .andExpect(jsonPath("$.parameters.launchedBy").value("admin")))
                .read("$.id", Long.class);

        DocumentContext detail = awaitFinished(id);
        assertThat(detail.read("$.execution.status", String.class)).isEqualTo("COMPLETED");
        assertThat(detail.read("$.steps[0].name", String.class)).isEqualTo("expirePoliciesStep");

        mvc.perform(get("/api/batch/jobs/{job}/executions", BatchJobs.POLICY_EXPIRATION).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].parameters.runDate").value("2026-01-15"));
    }

    @Test
    void rejectsInvalidParametersAndUnknownJobs() throws Exception {
        mvc.perform(post("/api/batch/jobs/{job}/executions", BatchJobs.MONTHLY_PRODUCTION_REPORT).with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"parameters\":{\"month\":\"2026-13\"}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_JOB_PARAMETER"));

        mvc.perform(post("/api/batch/jobs/{job}/executions", "noSuchJob").with(ADMIN))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/batch/jobs/{job}/executions", BatchJobs.CLAIM_IMPORT).with(ADMIN))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UPLOAD_REQUIRED"));
    }

    @Test
    void uploadedCsvIsImportedAndErrorsAreListed() throws Exception {
        String csv = """
                policy_number;incident_date;description;estimated_amount
                TRF-1999-000001;01.01.2026;Bilinmeyen poliçe;1000
                """;
        MockMultipartFile file = new MockMultipartFile("file", "hasar.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        long id = json(mvc.perform(multipart("/api/batch/claim-imports").file(file).with(ADMIN))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.parameters.originalFileName").value("hasar.csv")))
                .read("$.id", Long.class);

        DocumentContext detail = awaitFinished(id);
        assertThat(detail.read("$.execution.exitCode", String.class)).isEqualTo(BatchJobs.COMPLETED_WITH_SKIPS);
        assertThat(detail.read("$.importErrorCount", Integer.class)).isEqualTo(1);

        mvc.perform(get("/api/batch/executions/{id}/errors", id).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lineNumber").value(2))
                .andExpect(jsonPath("$[0].message").value("Poliçe bulunamadı: TRF-1999-000001"));
    }

    @Test
    void rejectsNonCsvUploads() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "rapor.xlsx", "application/octet-stream", new byte[]{1});

        mvc.perform(multipart("/api/batch/claim-imports").file(file).with(ADMIN))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
    }

    @Test
    void generatedReportCanBeListedAndDownloaded() throws Exception {
        String month = YearMonth.now().minusMonths(1).toString();
        long id = json(mvc.perform(post("/api/batch/jobs/{job}/executions", BatchJobs.MONTHLY_PRODUCTION_REPORT)
                        .with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parameters\":{\"month\":\"" + month + "\"}}"))
                .andExpect(status().isAccepted())).read("$.id", Long.class);
        assertThat(awaitFinished(id).read("$.execution.status", String.class)).isEqualTo("COMPLETED");

        String fileName = "uretim-raporu-" + month + ".xlsx";
        mvc.perform(get("/api/batch/reports").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems(fileName)));

        byte[] body = mvc.perform(get("/api/batch/reports/{name}", fileName).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(fileName)))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).startsWith('P', 'K'); // xlsx is a zip archive

        // Unknown or non-report names are 404; encoded path traversal is already rejected by the security firewall.
        mvc.perform(get("/api/batch/reports/{name}", "missing.xlsx").with(ADMIN)).andExpect(status().isNotFound());
        mvc.perform(get("/api/batch/reports/{name}", "application.yml").with(ADMIN)).andExpect(status().isNotFound());
        mvc.perform(get("/api/batch/reports/..%2F..%2Fsecret.xlsx").with(ADMIN)).andExpect(status().is4xxClientError());
    }

    private DocumentContext awaitFinished(long executionId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (true) {
            DocumentContext detail = json(mvc.perform(get("/api/batch/executions/{id}", executionId).with(ADMIN))
                    .andExpect(status().isOk()));
            String status = detail.read("$.execution.status", String.class);
            if (!status.equals("STARTING") && !status.equals("STARTED")) {
                return detail;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Execution " + executionId + " did not finish, status " + status);
            }
            Thread.sleep(200);
        }
    }

    private static DocumentContext json(ResultActions result) throws Exception {
        return JsonPath.parse(result.andReturn().getResponse().getContentAsString());
    }

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
