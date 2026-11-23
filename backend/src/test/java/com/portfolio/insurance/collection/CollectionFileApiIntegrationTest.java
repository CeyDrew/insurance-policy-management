package com.portfolio.insurance.collection;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.portfolio.insurance.IntegrationTest;
import com.portfolio.insurance.batch.collection.BankFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The demo flow through the API: request file, bank simulator answer, detail screen data. The jobs run in the
 * background and commit; the test waits for them.
 */
@IntegrationTest
class CollectionFileApiIntegrationTest {

    private static final RequestPostProcessor ADMIN = as("admin", "ADMIN");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE installment SET collection_request_file_id = NULL WHERE status <> 'PAID'");
    }

    @Test
    void onlyAdminsWorkWithBankFiles() throws Exception {
        mvc.perform(get("/api/collection-files").with(as("agent", "AGENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/collection-files/requests").with(as("claims", "CLAIM_HANDLER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestFileIsAnsweredBySimulatorAndShownWithRejectReasons() throws Exception {
        long execution = json(mvc.perform(post("/api/collection-files/requests").with(ADMIN))
                .andExpect(status().isAccepted())).read("$.id", Long.class);
        awaitExecution(execution);

        DocumentContext request = json(mvc.perform(get("/api/collection-files").param("direction", "OUTBOUND")
                .param("size", "1").with(ADMIN)));
        long requestId = request.read("$.content[0].id", Long.class);
        assertThat(request.read("$.content[0].status", String.class)).isEqualTo("GENERATED");
        assertThat(request.read("$.content[0].answered", Boolean.class)).isFalse();
        int records = request.read("$.content[0].recordCount", Integer.class);
        assertThat(records).isPositive(); // the seed data has installments falling due

        String requestText = mvc.perform(get("/api/collection-files/{id}/download", requestId).with(ADMIN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(requestText.lines().toList()).hasSize(records + 2)
                .allSatisfy(line -> assertThat(line).hasSize(120));

        long answer = json(mvc.perform(post("/api/collection-files/{id}/bank-answer", requestId).with(ADMIN))
                .andExpect(status().isAccepted())).read("$.id", Long.class);
        awaitExecution(answer);

        DocumentContext detail = json(mvc.perform(get("/api/collection-files/{id}", requestId).with(ADMIN)));
        assertThat(detail.read("$.file.answered", Boolean.class)).isTrue();
        long resultId = detail.read("$.resultFiles[0].id", Long.class);

        DocumentContext result = json(mvc.perform(get("/api/collection-files/{id}", resultId).with(ADMIN))
                .andExpect(status().isOk()));
        assertThat(result.read("$.file.status", String.class)).isEqualTo("COMPLETED");
        assertThat(result.read("$.file.requestFileId", Long.class)).isEqualTo(requestId);
        int collected = result.read("$.file.collectedCount", Integer.class);
        int rejected = result.read("$.file.rejectedCount", Integer.class);
        assertThat(result.read("$.file.errorCount", Integer.class)).isZero();
        assertThat(collected + rejected).isEqualTo(records);
        List<Integer> reasonCounts = result.read("$.rejectReasons[*].count");
        assertThat(reasonCounts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(rejected);

        mvc.perform(get("/api/collection-files/{id}/results", resultId).param("outcome", "REJECTED").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(rejected))
                .andExpect(jsonPath("$.content[*].outcome", everyItem(is("REJECTED"))));

        // a request file is answered once
        mvc.perform(post("/api/collection-files/{id}/bank-answer", requestId).with(ADMIN))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ALREADY_ANSWERED"));

        // uploading the very same result file again is refused before anything runs
        byte[] resultFile = mvc.perform(get("/api/collection-files/{id}/download", resultId).with(ADMIN))
                .andReturn().getResponse().getContentAsByteArray();
        mvc.perform(multipart("/api/collection-files/results")
                        .file(new MockMultipartFile("file", "tekrar.txt", "text/plain", resultFile)).with(ADMIN))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DUPLICATE_FILE"));
    }

    @Test
    void aResultFileWithoutTrailerIsRegisteredAsRejected() throws Exception {
        byte[] content = (BankFile.header("0099", LocalDate.now(), 1, BankFile.TYPE_RESULT) + "\n").getBytes();

        long execution = json(mvc.perform(multipart("/api/collection-files/results")
                        .file(new MockMultipartFile("file", "eksik.txt", "text/plain", content)).with(ADMIN))
                .andExpect(status().isAccepted())).read("$.id", Long.class);
        awaitExecution(execution);

        mvc.perform(get("/api/collection-files").param("direction", "INBOUND").param("size", "1").with(ADMIN))
                .andExpect(jsonPath("$.content[0].fileName").value("eksik.txt"))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"));
    }

    private void awaitExecution(long executionId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(90));
        while (true) {
            String status = json(mvc.perform(get("/api/batch/executions/{id}", executionId).with(ADMIN)))
                    .read("$.execution.status", String.class);
            if (!status.equals("STARTING") && !status.equals("STARTED")) {
                return;
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
