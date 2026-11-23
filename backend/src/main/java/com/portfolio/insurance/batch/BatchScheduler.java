package com.portfolio.insurance.batch;

import com.portfolio.insurance.batch.api.BatchJobService;
import com.portfolio.insurance.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;

/**
 * Cron triggers of the jobs. Off by default for local demos ({@code app.batch.scheduling.enabled=false});
 * cron expressions are configured under {@code app.batch.scheduling.cron}.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty("app.batch.scheduling.enabled")
@RequiredArgsConstructor
class BatchScheduler {

    private final BatchJobService batchJobService;

    @Scheduled(cron = "${app.batch.scheduling.cron.overdueInstallmentJob}")
    void overdueInstallments() {
        launch(BatchJobs.OVERDUE_INSTALLMENT);
    }

    @Scheduled(cron = "${app.batch.scheduling.cron.policyExpirationJob}")
    void policyExpiration() {
        launch(BatchJobs.POLICY_EXPIRATION);
    }

    @Scheduled(cron = "${app.batch.scheduling.cron.policyRenewalJob}")
    void policyRenewal() {
        launch(BatchJobs.POLICY_RENEWAL);
    }

    @Scheduled(cron = "${app.batch.scheduling.cron.monthlyProductionReportJob}")
    void monthlyProductionReport() {
        launch(BatchJobs.MONTHLY_PRODUCTION_REPORT);
    }

    private void launch(String jobName) {
        try {
            batchJobService.launch(jobName, Map.of(), "scheduler");
        } catch (BusinessException e) {
            log.warn("Scheduled launch of {} skipped: {}", jobName, e.getMessage());
        }
    }
}
