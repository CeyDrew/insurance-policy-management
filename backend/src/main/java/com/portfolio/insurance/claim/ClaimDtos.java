package com.portfolio.insurance.claim;

import com.portfolio.insurance.reference.ProductCode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request and response types of the claim API. */
public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record ClaimSummary(Long id, String claimNumber, ClaimStatus status, Long policyId, String policyNumber,
                               String productName, String customerName, LocalDate incidentDate,
                               LocalDateTime reportedAt, BigDecimal estimatedAmount, BigDecimal approvedAmount,
                               String handlerName) {
    }

    public record ClaimDocumentDto(Long id, DocumentType documentType, String fileName, String contentType,
                                   Long fileSize, LocalDateTime createdAt, String createdBy) {
    }

    /** One audited change of the claim, read from AUDIT_LOG. */
    public record HistoryEntry(LocalDateTime changedAt, String changedBy, String operation, ClaimStatus fromStatus,
                               ClaimStatus toStatus) {
    }

    public record ClaimDetail(Long id, String claimNumber, ClaimStatus status,
                              Long policyId, String policyNumber, ProductCode productCode, String productName,
                              String policyStatus,
                              Integer ncdStep, Long customerId, String customerName, String insuredObject,
                              LocalDate incidentDate, LocalDateTime reportedAt, String incidentCityName,
                              String description, BigDecimal estimatedAmount, BigDecimal approvedAmount,
                              BigDecimal paidAmount, Long handlerId, String handlerName, String handlerUsername,
                              String decisionNote, LocalDateTime decidedAt, LocalDateTime paidAt, String createdBy,
                              Integer version, List<ClaimDocumentDto> documents, List<HistoryEntry> history) {
    }

    public record ReportClaimRequest(
            @NotNull(message = "Poliçe zorunludur.") Long policyId,
            @NotNull(message = "Hasar tarihi zorunludur.")
            @PastOrPresent(message = "Hasar tarihi ileri bir tarih olamaz.")
            LocalDate incidentDate,
            Long incidentCityId,
            @NotBlank(message = "Hasar açıklaması zorunludur.")
            @Size(max = 2000, message = "Açıklama en fazla 2000 karakter olabilir.")
            String description,
            @NotNull(message = "Tahmini tutar zorunludur.")
            @Positive(message = "Tahmini tutar pozitif olmalıdır.")
            @DecimalMax(value = "100000000", message = "Tahmini tutar çok yüksek.")
            BigDecimal estimatedAmount) {
    }

    public record ApproveClaimRequest(
            @NotNull(message = "Onaylanan tutar zorunludur.")
            @Positive(message = "Onaylanan tutar pozitif olmalıdır.")
            @DecimalMax(value = "100000000", message = "Onaylanan tutar çok yüksek.")
            BigDecimal approvedAmount,
            @Size(max = 1000, message = "Not en fazla 1000 karakter olabilir.") String note) {
    }

    public record RejectClaimRequest(
            @NotBlank(message = "Red gerekçesi zorunludur.")
            @Size(max = 1000, message = "Gerekçe en fazla 1000 karakter olabilir.")
            String note) {
    }
}
