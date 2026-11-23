package com.portfolio.insurance.policy;

import com.portfolio.insurance.common.web.PageResponse;
import com.portfolio.insurance.reference.ProductCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

@Tag(name = "Policies", description = "Quotes, policy issuance and cancellation")
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @Operation(summary = "Search policies and quotes (paged)")
    @GetMapping
    public PageResponse<PolicySummaryDto> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(required = false) ProductCode productCode,
            @RequestParam(required = false) Long customerId,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return policyService.search(query, status, productCode, customerId, pageable);
    }

    @Operation(summary = "Policy detail with installment plan")
    @GetMapping("/{id}")
    public PolicyDto findById(@PathVariable Long id) {
        return policyService.findById(id);
    }

    @Operation(summary = "Create a quote (premium is calculated by the premium engine)")
    @PostMapping("/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public PolicyDto createQuote(@Valid @RequestBody QuoteRequest request, Authentication authentication) {
        return policyService.createQuote(request, authentication.getName());
    }

    @Operation(summary = "Issue a quote as a policy")
    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public PolicyDto issue(@PathVariable Long id, Authentication authentication) {
        return policyService.issue(id, authentication.getName());
    }

    @Operation(summary = "Cancel an active or suspended policy")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public PolicyDto cancel(@PathVariable Long id, @Valid @RequestBody CancelPolicyRequest request,
                            Authentication authentication) {
        return policyService.cancel(id, request, authentication.getName());
    }
}
