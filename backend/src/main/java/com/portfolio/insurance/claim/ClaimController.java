package com.portfolio.insurance.claim;

import com.portfolio.insurance.claim.ClaimDtos.ApproveClaimRequest;
import com.portfolio.insurance.claim.ClaimDtos.ClaimDetail;
import com.portfolio.insurance.claim.ClaimDtos.ClaimDocumentDto;
import com.portfolio.insurance.claim.ClaimDtos.ClaimSummary;
import com.portfolio.insurance.claim.ClaimDtos.RejectClaimRequest;
import com.portfolio.insurance.claim.ClaimDtos.ReportClaimRequest;
import com.portfolio.insurance.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Tag(name = "Claims", description = "Claim notification, review, decision, payment and documents")
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    @Operation(summary = "Search claims (paged); mine=true lists claims assigned to the current user")
    @GetMapping
    public PageResponse<ClaimSummary> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) Long policyId,
            @RequestParam(defaultValue = "false") boolean mine,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return claimService.search(query, status, policyId, mine, authentication.getName(), pageable);
    }

    @Operation(summary = "Claim detail with documents and status history")
    @GetMapping("/{id}")
    public ClaimDetail findById(@PathVariable Long id) {
        return claimService.findById(id);
    }

    @Operation(summary = "Report a claim (hasar ihbarı)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ClaimDetail report(@Valid @RequestBody ReportClaimRequest request) {
        return claimService.report(request);
    }

    @Operation(summary = "Take over a claim as its handler")
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLAIM_HANDLER')")
    public ClaimDetail assign(@PathVariable Long id, Authentication authentication) {
        return claimService.assignToMe(id, authentication.getName());
    }

    @Operation(summary = "Approve a claim in review")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLAIM_HANDLER')")
    public ClaimDetail approve(@PathVariable Long id, @Valid @RequestBody ApproveClaimRequest request,
                               Authentication authentication) {
        return claimService.approve(id, request, authentication.getName());
    }

    @Operation(summary = "Reject a claim in review")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLAIM_HANDLER')")
    public ClaimDetail reject(@PathVariable Long id, @Valid @RequestBody RejectClaimRequest request,
                              Authentication authentication) {
        return claimService.reject(id, request.note(), authentication.getName());
    }

    @Operation(summary = "Pay an approved claim (lowers the no-claims step of vehicle policies)")
    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLAIM_HANDLER')")
    public ClaimDetail pay(@PathVariable Long id, Authentication authentication) {
        return claimService.pay(id, authentication.getName());
    }

    @Operation(summary = "Upload a document (PDF, JPEG or PNG)")
    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimDocumentDto upload(@PathVariable Long id, @RequestParam DocumentType documentType,
                                   @RequestPart("file") MultipartFile file) {
        return claimService.addDocument(id, documentType, file);
    }

    @Operation(summary = "Download a document")
    @GetMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long documentId) {
        ClaimService.DocumentDownload download = claimService.document(id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }
}
