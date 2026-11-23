package com.portfolio.insurance.batch;

import com.portfolio.insurance.batch.api.BatchJobService;
import com.portfolio.insurance.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchSchedulerTest {

    private final BatchJobService jobs = mock(BatchJobService.class);
    private final BatchScheduler scheduler = new BatchScheduler(jobs);

    @Test
    void eachTriggerLaunchesItsJobWithDefaultParameters() {
        scheduler.overdueInstallments();
        scheduler.policyExpiration();
        scheduler.policyRenewal();
        scheduler.monthlyProductionReport();

        verify(jobs).launch(BatchJobs.OVERDUE_INSTALLMENT, Map.of(), "scheduler");
        verify(jobs).launch(BatchJobs.POLICY_EXPIRATION, Map.of(), "scheduler");
        verify(jobs).launch(BatchJobs.POLICY_RENEWAL, Map.of(), "scheduler");
        verify(jobs).launch(BatchJobs.MONTHLY_PRODUCTION_REPORT, Map.of(), "scheduler");
    }

    @Test
    void aRefusedLaunchDoesNotBreakTheScheduler() {
        // e.g. the job is already running after a manual start
        when(jobs.launch(anyString(), any(), anyString()))
                .thenThrow(new BusinessException("JOB_ALREADY_RUNNING", "running"));

        assertThatCode(scheduler::policyRenewal).doesNotThrowAnyException();
    }
}
