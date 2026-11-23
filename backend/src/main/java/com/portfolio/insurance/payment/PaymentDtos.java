package com.portfolio.insurance.payment;

import com.portfolio.insurance.policy.InstallmentStatus;
import com.portfolio.insurance.policy.PolicyStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Request and response types of the collection (tahsilat) API. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * @param daysOverdue days past the due date for unpaid installments, otherwise 0
     */
    public record InstallmentRow(Long id, Long policyId, String policyNumber, PolicyStatus policyStatus,
                                 Long customerId, String customerName, Integer installmentNo, LocalDate dueDate,
                                 BigDecimal amount, InstallmentStatus status, LocalDateTime paidAt, long daysOverdue) {
    }

    public record PayInstallmentRequest(@NotNull(message = "Ödeme yöntemi zorunludur.") PaymentMethod paymentMethod) {
    }

    /**
     * @param policyReactivated true when this payment cleared the last overdue installment of a suspended policy
     */
    public record PaymentResult(Long paymentId, String referenceNo, BigDecimal amount, PaymentMethod paymentMethod,
                                Long installmentId, Integer installmentNo, Long policyId, String policyNumber,
                                boolean policyReactivated) {
    }
}
