package com.portfolio.insurance.batch.api;

import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.batch.BatchProperties;
import com.portfolio.insurance.batch.api.BatchDtos.ExecutionDetail;
import com.portfolio.insurance.batch.api.BatchDtos.ExecutionSummary;
import com.portfolio.insurance.batch.api.BatchDtos.ImportError;
import com.portfolio.insurance.batch.api.BatchDtos.JobSummary;
import com.portfolio.insurance.batch.api.BatchDtos.ReportFile;
import com.portfolio.insurance.batch.api.BatchDtos.StepDetail;
import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.error.ResourceNotFoundException;
import com.portfolio.insurance.common.web.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Launches jobs and reads their history for the monitoring API.
 * <p>
 * Manual launches run asynchronously on virtual threads: the request returns as soon as the execution is
 * created and the UI polls its status. The application's own (synchronous) JobOperator is left untouched for
 * the scheduler and tests.
 */
@Slf4j
@Service
public class BatchJobService {

    private static final Pattern REPORT_NAME = Pattern.compile("^[\\w.-]+\\.xlsx$");
    private static final DateTimeFormatter UPLOAD_PREFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    private static final String EXECUTION_SUMMARY_SQL = """
            SELECT e.job_execution_id, i.job_name, e.status, e.exit_code, e.exit_message, e.start_time, e.end_time,
                   NVL(SUM(s.read_count), 0) AS read_count, NVL(SUM(s.write_count), 0) AS write_count,
                   NVL(SUM(s.read_skip_count + s.process_skip_count + s.write_skip_count), 0) AS skip_count
              FROM batch_job_execution e
              JOIN batch_job_instance i ON i.job_instance_id = e.job_instance_id
              LEFT JOIN batch_step_execution s ON s.job_execution_id = e.job_execution_id
             WHERE i.job_name = :jobName
             GROUP BY e.job_execution_id, i.job_name, e.status, e.exit_code, e.exit_message, e.start_time, e.end_time
             ORDER BY e.job_execution_id DESC
             OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """;

    private final Map<String, Job> jobs;
    private final JobRepository jobRepository;
    private final TaskExecutorJobOperator asyncOperator;
    private final TaskExecutorJobOperator syncOperator;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final BatchProperties properties;
    private final Clock clock;

    public BatchJobService(List<Job> jobs, JobRepository jobRepository, JdbcTemplate jdbc, BatchProperties properties,
                           Clock clock) throws Exception {
        this.jobs = jobs.stream().collect(Collectors.toMap(Job::getName, Function.identity()));
        this.jobRepository = jobRepository;
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.properties = properties;
        this.clock = clock;

        MapJobRegistry jobRegistry = new MapJobRegistry();
        for (Job job : jobs) {
            jobRegistry.register(job);
        }
        this.asyncOperator = new TaskExecutorJobOperator();
        this.asyncOperator.setJobRepository(jobRepository);
        this.asyncOperator.setJobRegistry(jobRegistry);
        this.asyncOperator.setTaskExecutor(new VirtualThreadTaskExecutor("batch-"));
        this.asyncOperator.afterPropertiesSet();

        // the inbox poller waits for the import to finish before it moves the file
        this.syncOperator = new TaskExecutorJobOperator();
        this.syncOperator.setJobRepository(jobRepository);
        this.syncOperator.setJobRegistry(jobRegistry);
        this.syncOperator.setTaskExecutor(new SyncTaskExecutor());
        this.syncOperator.afterPropertiesSet();
    }

    public List<JobSummary> listJobs() {
        Map<String, String> cron = properties.scheduling().enabled() ? properties.scheduling().cron() : Map.of();
        return Arrays.stream(BatchJobCatalog.values())
                .filter(entry -> jobs.containsKey(entry.jobName()))
                .map(entry -> new JobSummary(entry.jobName(), entry.title(), entry.description(), entry.parameters(),
                        cron.get(entry.jobName()), lastExecution(entry.jobName()).orElse(null)))
                .toList();
    }

    /**
     * Starts a job with the given business parameters; missing ones get defaults (runDate = today,
     * month = previous month). Returns immediately with the created execution.
     */
    public ExecutionSummary launch(String jobName, Map<String, String> parameters, String launchedBy) {
        if (BatchJobs.CLAIM_IMPORT.equals(jobName)) {
            throw new BusinessException("UPLOAD_REQUIRED", "Hasar içe aktarma için CSV dosyası yükleyin.");
        }
        if (BatchJobs.COLLECTION_RESULT_IMPORT.equals(jobName)) {
            throw new BusinessException("UPLOAD_REQUIRED", "Banka sonuç dosyasını Tahsilat Dosyaları ekranından yükleyin.");
        }
        return start(getJob(jobName), buildParameters(jobName, parameters == null ? Map.of() : parameters, launchedBy));
    }

    /** Stores the uploaded CSV in the import directory and starts claimImportJob on it. */
    public ExecutionSummary importClaims(MultipartFile file, String launchedBy) {
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("hasar.csv");
        if (file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "Yüklenen dosya boş.");
        }
        if (!originalName.toLowerCase().endsWith(".csv")) {
            throw new BusinessException("INVALID_FILE_TYPE", "Sadece .csv dosyası yüklenebilir.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Dosya en fazla 5 MB olabilir.");
        }

        Path target = properties.importDir().toAbsolutePath()
                .resolve(LocalDateTime.now(clock).format(UPLOAD_PREFIX) + "-" + UUID.randomUUID() + ".csv");
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store uploaded file " + target, e);
        }

        JobParameters parameters = new JobParametersBuilder()
                .addString(BatchJobs.PARAM_INPUT_FILE, target.toString())
                .addString(BatchJobs.PARAM_ORIGINAL_FILE_NAME, originalName, false)
                .addString(BatchJobs.PARAM_LAUNCHED_BY, launchedBy, false)
                .addLocalDateTime(BatchJobs.PARAM_LAUNCHED_AT, LocalDateTime.now(clock))
                .toJobParameters();
        return start(getJob(BatchJobs.CLAIM_IMPORT), parameters);
    }

    /**
     * Starts collectionResultImportJob on a stored result file. The file's hash is the only identifying parameter,
     * so a file that has already been applied cannot start a second job instance.
     *
     * @param waitForCompletion run on the caller's thread (inbox poller) instead of in the background
     */
    public ExecutionSummary importCollectionResult(Path file, String originalName, String fileHash, Integer partitions,
                                                   String launchedBy, boolean waitForCompletion) {
        JobParametersBuilder builder = new JobParametersBuilder()
                .addString(BatchJobs.PARAM_FILE_HASH, fileHash)
                .addString(BatchJobs.PARAM_INPUT_FILE, file.toAbsolutePath().toString(), false)
                .addString(BatchJobs.PARAM_ORIGINAL_FILE_NAME, originalName, false)
                .addString(BatchJobs.PARAM_LAUNCHED_BY, launchedBy, false)
                .addLocalDateTime(BatchJobs.PARAM_LAUNCHED_AT, LocalDateTime.now(clock), false);
        if (partitions != null) {
            builder.addLong(BatchJobs.PARAM_PARTITIONS, partitions.longValue(), false);
        }
        Job job = getJob(BatchJobs.COLLECTION_RESULT_IMPORT);
        return waitForCompletion ? startWith(syncOperator, job, builder.toJobParameters())
                : start(job, builder.toJobParameters());
    }

    /** Resumes a failed or stopped execution from where it stopped. */
    public ExecutionSummary restart(long executionId) {
        JobExecution execution = getExecution(executionId);
        if (execution.getStatus().isRunning()) {
            throw new BusinessException("JOB_ALREADY_RUNNING", "Bu iş zaten çalışıyor. Bitmesini bekleyin.");
        }
        try {
            JobExecution restarted = asyncOperator.restart(execution);
            log.info("Restarted execution {} of {} as {}", executionId, restarted.getJobInstance().getJobName(),
                    restarted.getId());
            return toSummary(restarted);
        } catch (JobRestartException e) {
            throw new BusinessException("JOB_RESTART_FAILED", "İş yeniden başlatılamadı: " + e.getMessage());
        }
    }

    public PageResponse<ExecutionSummary> executions(String jobName, int page, int size) {
        getJob(jobName);
        int pageSize = Math.clamp(size, 1, 100);
        int pageIndex = Math.max(page, 0);
        List<ExecutionSummary> rows = summaries(Map.of("jobName", jobName, "offset", pageIndex * pageSize,
                "size", pageSize));
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM batch_job_execution e
                  JOIN batch_job_instance i ON i.job_instance_id = e.job_instance_id
                 WHERE i.job_name = ?
                """, Long.class, jobName);
        long totalElements = total == null ? 0 : total;
        return new PageResponse<>(rows, pageIndex, pageSize, totalElements,
                (int) Math.ceil((double) totalElements / pageSize));
    }

    public ExecutionDetail execution(long executionId) {
        JobExecution execution = getExecution(executionId);
        List<StepDetail> steps = execution.getStepExecutions().stream()
                .sorted(Comparator.comparing(StepExecution::getId))
                .map(BatchJobService::toStepDetail)
                .toList();
        Long errors = jdbc.queryForObject("SELECT COUNT(*) FROM claim_import_error WHERE job_execution_id = ?",
                Long.class, executionId);
        return new ExecutionDetail(toSummary(execution), steps, errors == null ? 0 : errors);
    }

    public List<ImportError> importErrors(long executionId) {
        getExecution(executionId);
        return jdbc.query("""
                SELECT line_number, raw_line, error_message FROM claim_import_error
                 WHERE job_execution_id = ? ORDER BY line_number
                """, (rs, rowNum) -> new ImportError(rs.getInt("line_number"), rs.getString("raw_line"),
                rs.getString("error_message")), executionId);
    }

    public List<ReportFile> reports() {
        Path dir = properties.reportDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> REPORT_NAME.matcher(path.getFileName().toString()).matches())
                    .map(BatchJobService::toReportFile)
                    .sorted(Comparator.comparing(ReportFile::name).reversed())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not list reports in " + dir, e);
        }
    }

    /** Resolves a report by file name, refusing anything outside the report directory. */
    public Resource report(String fileName) {
        if (!REPORT_NAME.matcher(fileName).matches()) {
            throw new ResourceNotFoundException("Rapor", fileName);
        }
        Path dir = properties.reportDir().toAbsolutePath().normalize();
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Rapor", fileName);
        }
        return new FileSystemResource(file);
    }

    private ExecutionSummary start(Job job, JobParameters parameters) {
        return startWith(asyncOperator, job, parameters);
    }

    private ExecutionSummary startWith(TaskExecutorJobOperator operator, Job job, JobParameters parameters) {
        if (!jobRepository.findRunningJobExecutions(job.getName()).isEmpty()) {
            throw new BusinessException("JOB_ALREADY_RUNNING", "Bu iş zaten çalışıyor. Bitmesini bekleyin.");
        }
        try {
            JobExecution execution = operator.start(job, parameters);
            log.info("Launched {} as execution {} with {}", job.getName(), execution.getId(), parameters);
            return toSummary(execution);
        } catch (JobExecutionAlreadyRunningException e) {
            throw new BusinessException("JOB_ALREADY_RUNNING", "Bu iş zaten çalışıyor. Bitmesini bekleyin.");
        } catch (JobInstanceAlreadyCompleteException e) {
            throw new BusinessException("JOB_ALREADY_COMPLETE", "Bu parametrelerle iş zaten başarıyla tamamlanmış.");
        } catch (JobRestartException | InvalidJobParametersException e) {
            throw new BusinessException("JOB_LAUNCH_FAILED", "İş başlatılamadı: " + e.getMessage());
        }
    }

    private JobParameters buildParameters(String jobName, Map<String, String> requested, String launchedBy) {
        JobParametersBuilder builder = new JobParametersBuilder();
        if (BatchJobs.MONTHLY_PRODUCTION_REPORT.equals(jobName)) {
            YearMonth month = parse(requested.get(BatchJobs.PARAM_MONTH), YearMonth::parse,
                    YearMonth.now(clock).minusMonths(1), "Ay yyyy-AA biçiminde olmalıdır (örn. 2026-10).");
            builder.addString(BatchJobs.PARAM_MONTH, month.toString());
        } else {
            LocalDate runDate = parse(requested.get(BatchJobs.PARAM_RUN_DATE), LocalDate::parse, LocalDate.now(clock),
                    "İşlem tarihi yyyy-AA-gg biçiminde olmalıdır.");
            builder.addLocalDate(BatchJobs.PARAM_RUN_DATE, runDate);
        }
        return builder
                .addString(BatchJobs.PARAM_LAUNCHED_BY, launchedBy, false)
                .addLocalDateTime(BatchJobs.PARAM_LAUNCHED_AT, LocalDateTime.now(clock))
                .toJobParameters();
    }

    private static <T> T parse(String value, Function<String, T> parser, T defaultValue, String error) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return parser.apply(value.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException("INVALID_JOB_PARAMETER", error);
        }
    }

    private Job getJob(String jobName) {
        Job job = jobs.get(jobName);
        if (job == null) {
            throw new ResourceNotFoundException("Batch işi", jobName);
        }
        return job;
    }

    private JobExecution getExecution(long executionId) {
        JobExecution execution = jobRepository.getJobExecution(executionId);
        if (execution == null) {
            throw new ResourceNotFoundException("İş çalıştırması", executionId);
        }
        return execution;
    }

    private Optional<ExecutionSummary> lastExecution(String jobName) {
        return summaries(Map.of("jobName", jobName, "offset", 0, "size", 1)).stream().findFirst();
    }

    private List<ExecutionSummary> summaries(Map<String, Object> params) {
        List<ExecutionSummary> rows = namedJdbc.query(EXECUTION_SUMMARY_SQL, params, (rs, rowNum) -> {
            LocalDateTime start = rs.getObject("start_time", LocalDateTime.class);
            LocalDateTime end = rs.getObject("end_time", LocalDateTime.class);
            return new ExecutionSummary(rs.getLong("job_execution_id"), rs.getString("job_name"),
                    rs.getString("status"), rs.getString("exit_code"), rs.getString("exit_message"), start, end,
                    duration(start, end), rs.getLong("read_count"), rs.getLong("write_count"),
                    rs.getLong("skip_count"), Map.of());
        });
        if (rows.isEmpty()) {
            return rows;
        }
        Map<Long, Map<String, String>> parameters = new LinkedHashMap<>();
        namedJdbc.query("""
                        SELECT job_execution_id, parameter_name, parameter_value FROM batch_job_execution_params
                         WHERE job_execution_id IN (:ids) ORDER BY parameter_name
                        """,
                Map.of("ids", rows.stream().map(ExecutionSummary::id).toList()),
                rs -> {
                    parameters.computeIfAbsent(rs.getLong("job_execution_id"), id -> new LinkedHashMap<>())
                            .put(rs.getString("parameter_name"), rs.getString("parameter_value"));
                });
        List<ExecutionSummary> result = new ArrayList<>();
        for (ExecutionSummary row : rows) {
            result.add(new ExecutionSummary(row.id(), row.jobName(), row.status(), row.exitCode(), row.exitMessage(),
                    row.startTime(), row.endTime(), row.durationMillis(), row.readCount(), row.writeCount(),
                    row.skipCount(), parameters.getOrDefault(row.id(), Map.of())));
        }
        return result;
    }

    private static ExecutionSummary toSummary(JobExecution execution) {
        long read = 0;
        long written = 0;
        long skipped = 0;
        for (StepExecution step : execution.getStepExecutions()) {
            read += step.getReadCount();
            written += step.getWriteCount();
            skipped += step.getReadSkipCount() + step.getProcessSkipCount() + step.getWriteSkipCount();
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        execution.getJobParameters().parameters()
                .forEach(parameter -> parameters.put(parameter.name(), String.valueOf(parameter.value())));
        return new ExecutionSummary(execution.getId(), execution.getJobInstance().getJobName(),
                execution.getStatus().name(), execution.getExitStatus().getExitCode(),
                execution.getExitStatus().getExitDescription(), execution.getStartTime(), execution.getEndTime(),
                duration(execution.getStartTime(), execution.getEndTime()), read, written, skipped, parameters);
    }

    private static StepDetail toStepDetail(StepExecution step) {
        return new StepDetail(step.getId(), step.getStepName(), step.getStatus().name(),
                step.getExitStatus().getExitCode(), step.getExitStatus().getExitDescription(), step.getStartTime(),
                step.getEndTime(), duration(step.getStartTime(), step.getEndTime()), step.getReadCount(),
                step.getFilterCount(), step.getWriteCount(), step.getReadSkipCount(), step.getProcessSkipCount(),
                step.getWriteSkipCount(), step.getCommitCount(), step.getRollbackCount());
    }

    private static ReportFile toReportFile(Path path) {
        try {
            return new ReportFile(path.getFileName().toString(), Files.size(path),
                    LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private static Long duration(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? null : Duration.between(start, end).toMillis();
    }
}
