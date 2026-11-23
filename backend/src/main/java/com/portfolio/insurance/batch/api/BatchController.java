package com.portfolio.insurance.batch.api;

import com.portfolio.insurance.batch.api.BatchDtos.ExecutionDetail;
import com.portfolio.insurance.batch.api.BatchDtos.ExecutionSummary;
import com.portfolio.insurance.batch.api.BatchDtos.ImportError;
import com.portfolio.insurance.batch.api.BatchDtos.JobSummary;
import com.portfolio.insurance.batch.api.BatchDtos.LaunchRequest;
import com.portfolio.insurance.batch.api.BatchDtos.ReportFile;
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

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Batch jobs", description = "Job list, manual launch, execution history, claim import and reports")
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BatchController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final BatchJobService batchJobService;

    @Operation(summary = "List jobs with their last execution")
    @GetMapping("/jobs")
    public List<JobSummary> jobs() {
        return batchJobService.listJobs();
    }

    @Operation(summary = "Launch a job asynchronously (parameters: runDate=yyyy-MM-dd or month=yyyy-MM)")
    @PostMapping("/jobs/{jobName}/executions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionSummary launch(@PathVariable String jobName, @RequestBody(required = false) LaunchRequest request,
                                   Authentication authentication) {
        return batchJobService.launch(jobName, request == null ? null : request.parameters(), authentication.getName());
    }

    @Operation(summary = "Execution history of a job (newest first)")
    @GetMapping("/jobs/{jobName}/executions")
    public PageResponse<ExecutionSummary> executions(@PathVariable String jobName,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return batchJobService.executions(jobName, page, size);
    }

    @Operation(summary = "Execution detail with step statistics")
    @GetMapping("/executions/{executionId}")
    public ExecutionDetail execution(@PathVariable long executionId) {
        return batchJobService.execution(executionId);
    }

    @Operation(summary = "Rejected lines of a claim import execution")
    @GetMapping("/executions/{executionId}/errors")
    public List<ImportError> importErrors(@PathVariable long executionId) {
        return batchJobService.importErrors(executionId);
    }

    @Operation(summary = "Upload a claim CSV (policy_number;incident_date;description;estimated_amount[;city_plate]) and import it")
    @PostMapping(path = "/claim-imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionSummary importClaims(@RequestPart("file") MultipartFile file, Authentication authentication) {
        return batchJobService.importClaims(file, authentication.getName());
    }

    @Operation(summary = "Generated Excel reports")
    @GetMapping("/reports")
    public List<ReportFile> reports() {
        return batchJobService.reports();
    }

    @Operation(summary = "Download a report")
    @GetMapping("/reports/{fileName}")
    public ResponseEntity<Resource> downloadReport(@PathVariable String fileName) {
        Resource report = batchJobService.report(fileName);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(report);
    }
}
