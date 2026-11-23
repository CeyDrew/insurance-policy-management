package com.portfolio.insurance.batch.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Response and request types of the batch monitoring API. */
public final class BatchDtos {

    private BatchDtos() {
    }

    /**
     * @param parameters names of the business parameters the job accepts (e.g. runDate)
     * @param cron       schedule if cron triggering is enabled, otherwise null
     */
    public record JobSummary(String name, String title, String description, List<String> parameters, String cron,
                             ExecutionSummary lastExecution) {
    }

    /**
     * @param status   Spring Batch status (STARTING, STARTED, COMPLETED, FAILED, STOPPED...)
     * @param exitCode exit code, e.g. COMPLETED_WITH_SKIPS when invalid items were skipped
     */
    public record ExecutionSummary(long id, String jobName, String status, String exitCode, String exitMessage,
                                   LocalDateTime startTime, LocalDateTime endTime, Long durationMillis,
                                   long readCount, long writeCount, long skipCount, Map<String, String> parameters) {
    }

    public record StepDetail(long id, String name, String status, String exitCode, String exitMessage,
                             LocalDateTime startTime, LocalDateTime endTime, Long durationMillis, long readCount,
                             long filterCount, long writeCount, long readSkipCount, long processSkipCount,
                             long writeSkipCount, long commitCount, long rollbackCount) {
    }

    public record ExecutionDetail(ExecutionSummary execution, List<StepDetail> steps, long importErrorCount) {
    }

    public record ImportError(int lineNumber, String rawLine, String message) {
    }

    public record ReportFile(String name, long sizeBytes, LocalDateTime lastModified) {
    }

    /** Business parameters for a manual launch, e.g. {"runDate": "2026-11-23"} or {"month": "2026-10"}. */
    public record LaunchRequest(Map<String, String> parameters) {
    }
}
