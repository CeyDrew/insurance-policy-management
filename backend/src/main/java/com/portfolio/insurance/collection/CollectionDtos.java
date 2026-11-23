package com.portfolio.insurance.collection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CollectionDtos {

    private CollectionDtos() {
    }

    public enum Direction { OUTBOUND, INBOUND }

    public enum FileStatus { GENERATING, GENERATED, PROCESSING, COMPLETED, FAILED }

    public enum Outcome { COLLECTED, REJECTED, ERROR }

    /**
     * @param answered     outbound only: a result file for this request has been applied or is being applied
     * @param interrupted  inbound only: stopped while being applied; can be restarted
     */
    public record CollectionFileSummary(Long id, Direction direction, Long requestFileId, String fileName,
                                        LocalDate fileDate, FileStatus status, Long recordCount,
                                        BigDecimal totalAmount, long collectedCount, BigDecimal collectedAmount,
                                        long rejectedCount, long errorCount, String errorMessage,
                                        Long jobExecutionId, LocalDateTime createdAt, String createdBy,
                                        LocalDateTime completedAt, boolean answered, boolean interrupted) {
    }

    /** Rejected lines of a result file grouped by the bank's code. */
    public record RejectReason(String code, String description, long count, BigDecimal amount) {
    }

    public record CollectionFileDetail(CollectionFileSummary file, List<RejectReason> rejectReasons,
                                       List<CollectionFileSummary> resultFiles) {
    }

    public record CollectionResultRow(long lineNo, Long installmentId, Long policyId, String policyNumber,
                                      Integer installmentNo, BigDecimal amount, String resultCode, Outcome outcome,
                                      Long paymentId, String message) {
    }

    public record CreateRequestFile(LocalDate runDate) {
    }
}
