package com.portfolio.insurance.batch.collection;

import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.batch.BatchProperties;
import com.portfolio.insurance.batch.support.JobLoggingListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.infrastructure.item.support.CompositeItemWriter;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * collectionRequestExportJob: writes the request file (talep dosyası) for the bank. Open installments of active or
 * suspended policies, due within {@code days-ahead} days of the run date and not already waiting for an answer in
 * an earlier file, become detail lines; each is linked to the file so it is not requested twice.
 *
 * <pre>
 * createRequestFileStep     registers the file (COLLECTION_FILE, GENERATING) and decides its path
 * writeRequestFileStep      cursor -> FlatFileItemWriter (FormatterLineAggregator, header/footer callbacks)
 *                                   + JdbcBatchItemWriter (installment.collection_request_file_id)
 * completeRequestFileStep   control totals, hash, status GENERATED
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
class CollectionRequestExportJobConfig {

    static final String CONTEXT_FILE_ID = "collectionFileId";
    static final String CONTEXT_FILE_PATH = "collectionFilePath";

    /**
     * Rows already linked to this file stay in the result set, so after a restart the cursor skips exactly the
     * rows that were written before the failure (the reader restores its position by count).
     */
    private static final String CANDIDATES_SQL = """
            SELECT i.id, p.policy_number, i.installment_no, i.due_date, i.amount
              FROM installment i
              JOIN policy p ON p.id = i.policy_id
             WHERE i.status IN ('PENDING', 'OVERDUE')
               AND p.status IN ('ACTIVE', 'SUSPENDED')
               AND i.due_date <= ?
               AND (i.collection_request_file_id IS NULL OR i.collection_request_file_id = ?)
             ORDER BY i.policy_id, i.installment_no, i.id
            """;

    @Bean
    Job collectionRequestExportJob(JobRepository jobRepository, Step createRequestFileStep,
                                   Step writeRequestFileStep, Step completeRequestFileStep,
                                   JobLoggingListener listener) {
        return new JobBuilder(BatchJobs.COLLECTION_REQUEST_EXPORT, jobRepository)
                .listener(listener)
                .start(createRequestFileStep)
                .next(writeRequestFileStep)
                .next(completeRequestFileStep)
                .build();
    }

    @Bean
    Step createRequestFileStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                               Tasklet createRequestFileTasklet) {
        return new StepBuilder("createRequestFileStep", jobRepository)
                .tasklet(createRequestFileTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    Tasklet createRequestFileTasklet(JdbcTemplate jdbc, BatchProperties properties,
                                     @Value("#{jobParameters['" + BatchJobs.PARAM_RUN_DATE + "']}") LocalDate runDate,
                                     @Value("#{jobParameters['" + BatchJobs.PARAM_LAUNCHED_BY + "']}") String launchedBy) {
        return (contribution, chunkContext) -> {
            long jobExecutionId = chunkContext.getStepContext().getStepExecution().getJobExecutionId();
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO collection_file (direction, file_name, file_date, status, job_execution_id, created_by)
                        VALUES ('OUTBOUND', 'pending', ?, 'GENERATING', ?, ?)
                        """, new String[]{"id"});
                ps.setDate(1, Date.valueOf(runDate));
                ps.setLong(2, jobExecutionId);
                ps.setString(3, launchedBy == null ? BatchJobs.BATCH_USER : launchedBy);
                return ps;
            }, key);
            long fileId = key.getKeyAs(Number.class).longValue();
            String fileName = "TL-%s-%06d.txt".formatted(BankFile.DATE.format(runDate), fileId);
            Path path = properties.collection().outboundDir().toAbsolutePath().resolve(fileName);
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException e) {
                throw new IllegalStateException("Could not create " + path.getParent(), e);
            }
            jdbc.update("UPDATE collection_file SET file_name = ?, storage_path = ? WHERE id = ?",
                    fileName, path.toString(), fileId);

            ExecutionContext jobContext = chunkContext.getStepContext().getStepExecution().getJobExecution()
                    .getExecutionContext();
            jobContext.putLong(CONTEXT_FILE_ID, fileId);
            jobContext.putString(CONTEXT_FILE_PATH, path.toString());
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    Step writeRequestFileStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              JdbcCursorItemReader<RequestLine> requestCandidateReader,
                              CompositeItemWriter<RequestLine> requestFileWriter,
                              BatchProperties properties, JobLoggingListener listener) {
        return new StepBuilder("writeRequestFileStep", jobRepository)
                .<RequestLine, RequestLine>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(requestCandidateReader)
                .writer(requestFileWriter)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .listener(listener)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<RequestLine> requestCandidateReader(
            DataSource dataSource, BatchProperties properties,
            @Value("#{jobParameters['" + BatchJobs.PARAM_RUN_DATE + "']}") LocalDate runDate,
            @Value("#{jobExecutionContext['" + CONTEXT_FILE_ID + "']}") Long fileId) {
        LocalDate dueTo = runDate.plusDays(properties.collection().daysAhead());
        return new JdbcCursorItemReaderBuilder<RequestLine>()
                .name("requestCandidateReader")
                .dataSource(dataSource)
                .sql(CANDIDATES_SQL)
                .preparedStatementSetter(ps -> {
                    ps.setDate(1, Date.valueOf(dueTo));
                    ps.setLong(2, fileId);
                })
                .fetchSize(1_000)
                // getRow() is the absolute position, also after the reader jumps ahead on a restart
                .rowMapper((rs, rowNum) -> new RequestLine(rs.getRow(), rs.getLong("id"),
                        rs.getString("policy_number"), rs.getInt("installment_no"),
                        rs.getObject("due_date", LocalDate.class), rs.getBigDecimal("amount")))
                .build();
    }

    @Bean
    @StepScope
    CompositeItemWriter<RequestLine> requestFileWriter(
            DataSource dataSource, JdbcTemplate jdbc, BatchProperties properties,
            @Value("#{jobParameters['" + BatchJobs.PARAM_RUN_DATE + "']}") LocalDate runDate,
            @Value("#{jobExecutionContext['" + CONTEXT_FILE_ID + "']}") Long fileId,
            @Value("#{jobExecutionContext['" + CONTEXT_FILE_PATH + "']}") String filePath) {
        FlatFileItemWriter<RequestLine> file = new FlatFileItemWriterBuilder<RequestLine>()
                .name("requestFileWriter")
                .resource(new FileSystemResource(filePath))
                .encoding("US-ASCII")
                .lineSeparator("\n")
                .headerCallback(writer -> writer.write(BankFile.header(properties.collection().bankCode(), runDate,
                        fileId, BankFile.TYPE_REQUEST)))
                // Written when the step closes, after the last chunk has committed: the totals are read back
                // from the database, so they are right after a restart as well.
                .footerCallback(writer -> {
                    Map<String, Object> totals = jdbc.queryForMap("""
                            SELECT COUNT(*) AS cnt, NVL(SUM(amount), 0) AS total
                              FROM installment WHERE collection_request_file_id = ?
                            """, fileId);
                    writer.write(BankFile.trailer(((Number) totals.get("CNT")).longValue(),
                            (BigDecimal) totals.get("TOTAL")));
                })
                .formatted()
                .format(BankFile.DETAIL_FORMAT)
                .locale(Locale.ROOT)
                .fieldExtractor(line -> BankFile.detailFields(line.lineNo(), line.installmentId(),
                        line.policyNumber(), line.installmentNo(), line.dueDate(), line.amount(), "  "))
                .build();

        JdbcBatchItemWriter<RequestLine> link = new JdbcBatchItemWriterBuilder<RequestLine>()
                .dataSource(dataSource)
                .sql("""
                        UPDATE installment
                           SET collection_request_file_id = ?, updated_at = SYSTIMESTAMP, updated_by = ?
                         WHERE id = ?
                        """)
                .itemPreparedStatementSetter((line, ps) -> {
                    ps.setLong(1, fileId);
                    ps.setString(2, BatchJobs.BATCH_USER);
                    ps.setLong(3, line.installmentId());
                })
                .assertUpdates(true)
                .build();

        return new CompositeItemWriter<>(List.of(file, link));
    }

    @Bean
    Step completeRequestFileStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                 Tasklet completeRequestFileTasklet) {
        return new StepBuilder("completeRequestFileStep", jobRepository)
                .tasklet(completeRequestFileTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    Tasklet completeRequestFileTasklet(JdbcTemplate jdbc,
                                       @Value("#{jobExecutionContext['" + CONTEXT_FILE_ID + "']}") Long fileId,
                                       @Value("#{jobExecutionContext['" + CONTEXT_FILE_PATH + "']}") String filePath) {
        return (contribution, chunkContext) -> {
            jdbc.update("""
                    UPDATE collection_file f
                       SET (record_count, total_amount) = (
                               SELECT COUNT(*), NVL(SUM(amount), 0) FROM installment
                                WHERE collection_request_file_id = f.id),
                           file_hash = ?,
                           status = 'GENERATED',
                           completed_at = SYSTIMESTAMP
                     WHERE id = ?
                    """, FileHash.sha256(Path.of(filePath)), fileId);
            return RepeatStatus.FINISHED;
        };
    }

    /** One detail line of the request file. */
    record RequestLine(long lineNo, long installmentId, String policyNumber, int installmentNo, LocalDate dueDate,
                       BigDecimal amount) {
    }
}
