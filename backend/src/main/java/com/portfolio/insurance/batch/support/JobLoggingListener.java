package com.portfolio.insurance.batch.support;

import com.portfolio.insurance.batch.BatchJobs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Logs start/end of every job and step with read/write/skip counts, and marks steps that skipped items
 * with the {@value BatchJobs#COMPLETED_WITH_SKIPS} exit code so the monitoring screen can highlight them.
 */
@Slf4j
@Component
public class JobLoggingListener implements JobExecutionListener, StepExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Job {} started (execution {}, parameters {})", jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(), jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("Job {} finished with {} / {} in {} ms", jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus(), jobExecution.getExitStatus().getExitCode(),
                millis(jobExecution.getStartTime(), jobExecution.getEndTime()));
    }

    @Override
    public ExitStatus afterStep(StepExecution step) {
        long skips = step.getReadSkipCount() + step.getProcessSkipCount() + step.getWriteSkipCount();
        log.info("Step {} finished: read={}, filtered={}, written={}, skipped={}, commits={}, rollbacks={}",
                step.getStepName(), step.getReadCount(), step.getFilterCount(), step.getWriteCount(), skips,
                step.getCommitCount(), step.getRollbackCount());
        if (skips > 0 && ExitStatus.COMPLETED.getExitCode().equals(step.getExitStatus().getExitCode())) {
            return new ExitStatus(BatchJobs.COMPLETED_WITH_SKIPS, skips + " kayıt atlandı");
        }
        return null;
    }

    private static long millis(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? 0 : Duration.between(start, end).toMillis();
    }
}
