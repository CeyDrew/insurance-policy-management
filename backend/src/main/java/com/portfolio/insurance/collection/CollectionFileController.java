package com.portfolio.insurance.collection;

import com.portfolio.insurance.batch.api.BatchDtos.ExecutionSummary;
import com.portfolio.insurance.collection.CollectionDtos.CollectionFileDetail;
import com.portfolio.insurance.collection.CollectionDtos.CollectionFileSummary;
import com.portfolio.insurance.collection.CollectionDtos.CollectionResultRow;
import com.portfolio.insurance.collection.CollectionDtos.CreateRequestFile;
import com.portfolio.insurance.collection.CollectionDtos.Direction;
import com.portfolio.insurance.collection.CollectionDtos.Outcome;
import com.portfolio.insurance.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
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

import java.util.Map;

@Tag(name = "Bank collection files", description = "Auto-debit request files and bank result files")
@RestController
@RequestMapping("/api/collection-files")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CollectionFileController {

    private final CollectionFileService service;

    @Operation(summary = "Request and result files, newest first")
    @GetMapping
    public PageResponse<CollectionFileSummary> list(@RequestParam(required = false) Direction direction,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return service.list(direction, page, size);
    }

    @Operation(summary = "A file with reject reasons (result file) or its answers (request file)")
    @GetMapping("/{id}")
    public CollectionFileDetail detail(@PathVariable long id) {
        return service.detail(id);
    }

    @Operation(summary = "Applied lines of a result file")
    @GetMapping("/{id}/results")
    public PageResponse<CollectionResultRow> results(@PathVariable long id,
                                                     @RequestParam(required = false) Outcome outcome,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        return service.results(id, outcome, page, size);
    }

    @Operation(summary = "Download the file")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        Resource file = service.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.getFilename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file);
    }

    @Operation(summary = "Generate a request file (collectionRequestExportJob)")
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionSummary createRequest(@RequestBody(required = false) CreateRequestFile request,
                                          Authentication authentication) {
        return service.createRequestFile(request == null ? null : request.runDate(), authentication.getName());
    }

    @Operation(summary = "Upload a bank result file and apply it (collectionResultImportJob)")
    @PostMapping(path = "/results", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionSummary uploadResult(@RequestPart("file") MultipartFile file, Authentication authentication) {
        return service.uploadResult(file, authentication.getName());
    }

    @Operation(summary = "Demo: let the bank simulator answer a request file and apply the answer")
    @PostMapping("/{id}/bank-answer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionSummary simulateBankAnswer(@PathVariable long id,
                                               @RequestParam(required = false) Integer partitions,
                                               Authentication authentication) {
        return service.simulateBankAnswer(id, partitions, authentication.getName());
    }

    @Operation(summary = "Resume a result file whose import stopped half way")
    @PostMapping("/{id}/restart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionSummary restart(@PathVariable long id) {
        return service.restart(id);
    }

    @Operation(summary = "Bank result codes")
    @GetMapping("/result-codes")
    public Map<String, String> resultCodes() {
        return service.resultCodes();
    }
}
