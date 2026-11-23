package com.portfolio.insurance.report;

import com.jayway.jsonpath.JsonPath;
import com.portfolio.insurance.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not transactional: refreshing the materialized view commits (DBMS_MVIEW.REFRESH), so this test only reads seed
 * data and refreshes.
 */
@IntegrationTest
class DashboardIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void dashboardIsBuiltFromTheReportingViews() throws Exception {
        String first = YearMonth.now().minusMonths(11).toString();
        mvc.perform(get("/api/dashboard").with(as("claims", "CLAIM_HANDLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.activePolicies").value(greaterThan(0)))
                .andExpect(jsonPath("$.kpis.earnedPremium").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.kpis.lossRatio").isNumber())
                // 12 months x 4 products, zero-filled
                .andExpect(jsonPath("$.monthlyProduction.length()").value(48))
                .andExpect(jsonPath("$.monthlyProduction[0].month").value(first))
                .andExpect(jsonPath("$.productMix[*].productCode", contains("TRAFFIC", "CASCO", "HOME", "HEALTH")))
                .andExpect(jsonPath("$.lossRatios.length()").value(4))
                .andExpect(jsonPath("$.agents.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.claimStatuses[*].status",
                        contains("REPORTED", "IN_REVIEW", "APPROVED", "REJECTED", "PAID")));
    }

    @Test
    void adminRefreshesTheMaterializedView() throws Exception {
        String body = mvc.perform(post("/api/dashboard/refresh").with(as("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        LocalDateTime refreshedAt = LocalDateTime.parse(JsonPath.read(body, "$.refreshedAt"));
        assertThat(refreshedAt).isAfter(LocalDateTime.now().minusMinutes(5));

        mvc.perform(post("/api/dashboard/refresh").with(as("agent", "AGENT"))).andExpect(status().isForbidden());
    }

    private static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
