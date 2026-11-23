package com.portfolio.insurance.payment;

import com.portfolio.insurance.common.web.PageResponse;
import com.portfolio.insurance.payment.PaymentDtos.InstallmentRow;
import com.portfolio.insurance.payment.PaymentDtos.PayInstallmentRequest;
import com.portfolio.insurance.payment.PaymentDtos.PaymentResult;
import com.portfolio.insurance.policy.InstallmentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Collections", description = "Installments and premium payments (simulated)")
@RestController
@RequestMapping("/api/installments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Installments with policy and customer, ordered by due date")
    @GetMapping
    public PageResponse<InstallmentRow> installments(
            @RequestParam(required = false) InstallmentStatus status,
            @RequestParam(defaultValue = "false") boolean unpaidOnly,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long policyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return paymentService.installments(status, unpaidOnly, query, policyId, dueTo, page, size);
    }

    @Operation(summary = "Record a full payment of an installment")
    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResult pay(@PathVariable Long id, @Valid @RequestBody PayInstallmentRequest request,
                             Authentication authentication) {
        return paymentService.pay(id, request.paymentMethod(), authentication.getName());
    }
}
