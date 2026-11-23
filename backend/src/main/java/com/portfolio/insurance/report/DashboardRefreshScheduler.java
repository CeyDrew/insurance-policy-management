package com.portfolio.insurance.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly refresh of the dashboard's materialized views, together with the batch jobs' schedule. */
@Slf4j
@Component
@ConditionalOnBooleanProperty("app.batch.scheduling.enabled")
@RequiredArgsConstructor
class DashboardRefreshScheduler {

    private final DashboardService dashboardService;

    @Scheduled(cron = "${app.batch.scheduling.cron.dashboardRefresh:0 45 1 * * *}")
    void refresh() {
        log.info("Dashboard views refreshed at {}", dashboardService.refresh());
    }
}
