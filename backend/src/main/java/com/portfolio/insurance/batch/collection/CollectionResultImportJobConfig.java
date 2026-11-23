package com.portfolio.insurance.batch.collection;

import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.batch.BatchProperties;
import com.portfolio.insurance.batch.collection.BankRecord.Detail;
import com.portfolio.insurance.batch.support.JobLoggingListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;

/**
 * collectionResultImportJob: applies the bank's result file.
 *
 * <pre>
 * validateResultFileStep    whole-file check (header, line numbers, trailer count + total); FAILED -> nothing applied
 * applyResultsStep          partitioned: N workers, each reads its own range of detail lines
 *   applyResultsWorkerStep    FlatFileItemReader (PatternMatchingCompositeLineMapper + FixedLengthTokenizer)
 *                             -> PKG_COLLECTION.APPLY_RESULTS, one call per chunk
 * completeResultFileStep    totals from COLLECTION_RESULT, status COMPLETED
 * </pre>
 *
 * The file's SHA-256 is the only identifying job parameter: one job instance per file. A failed run is resumed by
 * restarting that execution; committed chunks are not read again, and PL/SQL ignores lines it already applied.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
class CollectionResultImportJobConfig {

    static final int MAX_PARTITIONS = 8;

    @Bean
    Job collectionResultImportJob(JobRepository jobRepository, Step validateResultFileStep, Step applyResultsStep,
                                  Step completeResultFileStep, JobLoggingListener listener, JdbcTemplate jdbc) {
        return new JobBuilder(BatchJobs.COLLECTION_RESULT_IMPORT, jobRepository)
                .listener(listener)
                .listener(new InterruptedImportListener(jdbc))
                .start(validateResultFileStep)
                .next(applyResultsStep)
                .next(completeResultFileStep)
                .build();
    }

    @Bean
    Step validateResultFileStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                Tasklet resultFileValidationTasklet) {
        return new StepBuilder("validateResultFileStep", jobRepository)
                .tasklet(resultFileValidationTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    Tasklet resultFileValidationTasklet(
            JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock,
            @Value("#{jobParameters['" + BatchJobs.PARAM_INPUT_FILE + "']}") String inputFile,
            @Value("#{jobParameters['" + BatchJobs.PARAM_ORIGINAL_FILE_NAME + "']}") String fileName,
            @Value("#{jobParameters['" + BatchJobs.PARAM_FILE_HASH + "']}") String fileHash,
            @Value("#{jobParameters['" + BatchJobs.PARAM_LAUNCHED_BY + "']}") String launchedBy) {
        return new ResultFileValidationTasklet(jdbc, transactionManager, Path.of(inputFile),
                fileName == null ? Path.of(inputFile).getFileName().toString() : fileName, fileHash,
                launchedBy == null ? BatchJobs.BATCH_USER : launchedBy, LocalDate.now(clock));
    }

    @Bean
    Step applyResultsStep(JobRepository jobRepository, Step applyResultsWorkerStep,
                          DetailRangePartitioner detailRangePartitioner) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("collection-");
        executor.setConcurrencyLimit(MAX_PARTITIONS);
        return new StepBuilder("applyResultsStep", jobRepository)
                .partitioner("applyResultsWorkerStep", detailRangePartitioner)
                .step(applyResultsWorkerStep)
                .taskExecutor(executor)
                .build();
    }

    @Bean
    @StepScope
    DetailRangePartitioner detailRangePartitioner(
            BatchProperties properties,
            @Value("#{jobExecutionContext['" + ResultFileValidationTasklet.CONTEXT_DETAIL_COUNT + "']}") Long count,
            @Value("#{jobParameters['" + BatchJobs.PARAM_PARTITIONS + "']}") Long partitions) {
        int requested = partitions == null ? properties.collection().partitions() : partitions.intValue();
        return new DetailRangePartitioner(count, Math.clamp(requested, 1, MAX_PARTITIONS));
    }

    @Bean
    Step applyResultsWorkerStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                FlatFileItemReader<BankRecord> resultLineReader,
                                ItemProcessor<BankRecord, Detail> resultLineProcessor,
                                BankResultWriter bankResultWriter, BatchProperties properties) {
        return new StepBuilder("applyResultsWorkerStep", jobRepository)
                .<BankRecord, Detail>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(resultLineReader)
                .processor(resultLineProcessor)
                .writer(bankResultWriter)
                .faultTolerant()
                // ORA-00060 between partitions touching the same policy: the chunk is simply retried
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .build();
    }

    @Bean
    @StepScope
    FlatFileItemReader<BankRecord> resultLineReader(
            @Value("#{jobParameters['" + BatchJobs.PARAM_INPUT_FILE + "']}") String inputFile,
            @Value("#{stepExecutionContext['" + DetailRangePartitioner.FROM_ITEM + "']}") Long fromItem,
            @Value("#{stepExecutionContext['" + DetailRangePartitioner.TO_ITEM + "']}") Long toItem) {
        return new FlatFileItemReaderBuilder<BankRecord>()
                .name("resultLineReader")
                .resource(new FileSystemResource(inputFile))
                .encoding("US-ASCII")
                .lineMapper(BankFile.lineMapper())
                .currentItemCount(fromItem.intValue() - 1)
                .maxItemCount(toItem.intValue())
                .build();
    }

    @Bean
    @StepScope
    ItemProcessor<BankRecord, Detail> resultLineProcessor(CollectionFaultInjector faultInjector) {
        return item -> {
            // the ranges cover detail lines only; anything else would be a partitioning bug
            Detail detail = (Detail) item;
            faultInjector.check(detail.lineNo());
            return detail;
        };
    }

    @Bean
    @StepScope
    BankResultWriter bankResultWriter(
            JdbcTemplate jdbc, JsonMapper jsonMapper,
            @Value("#{jobExecutionContext['" + ResultFileValidationTasklet.CONTEXT_FILE_ID + "']}") Long fileId) {
        return new BankResultWriter(jdbc, jsonMapper, fileId);
    }

    @Bean
    Step completeResultFileStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                Tasklet completeResultFileTasklet) {
        return new StepBuilder("completeResultFileStep", jobRepository)
                .tasklet(completeResultFileTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    Tasklet completeResultFileTasklet(
            JdbcTemplate jdbc,
            @Value("#{jobExecutionContext['" + ResultFileValidationTasklet.CONTEXT_FILE_ID + "']}") Long fileId) {
        return (contribution, chunkContext) -> {
            jdbc.update("BEGIN pkg_collection.complete_file(?); END;", fileId);
            jdbc.update("UPDATE collection_file SET error_message = NULL, job_execution_id = ? WHERE id = ?",
                    chunkContext.getStepContext().getStepExecution().getJobExecutionId(), fileId);
            return RepeatStatus.FINISHED;
        };
    }

    /**
     * A file that passed validation but stopped while being applied stays PROCESSING (a second upload is refused);
     * the reason is noted so the screen can offer a restart.
     */
    private record InterruptedImportListener(JdbcTemplate jdbc) implements JobExecutionListener {

        @Override
        public void afterJob(JobExecution execution) {
            if (execution.getStatus() == BatchStatus.COMPLETED
                    || !execution.getExecutionContext().containsKey(ResultFileValidationTasklet.CONTEXT_FILE_ID)) {
                return;
            }
            long fileId = execution.getExecutionContext().getLong(ResultFileValidationTasklet.CONTEXT_FILE_ID);
            // the partition manager only reports "unsuccessful step"; the worker's exception says what happened
            String reason = execution.getStepExecutions().stream()
                    .flatMap(step -> step.getFailureExceptions().stream())
                    .filter(e -> !(e instanceof JobExecutionException))
                    .findFirst()
                    .or(() -> execution.getAllFailureExceptions().stream().findFirst())
                    // Spring Batch wraps it ("Unable to process chunk"); the innermost cause is the real reason
                    .map(e -> NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                    .orElse(execution.getExitStatus().getExitCode());
            String message = "Yarıda kesildi, yeniden başlatılabilir: " + reason;
            jdbc.update("UPDATE collection_file SET error_message = ?, job_execution_id = ? WHERE id = ?",
                    message.length() > 1000 ? message.substring(0, 1000) : message, execution.getId(), fileId);
            log.warn("Collection result file {} interrupted in execution {}: {}", fileId, execution.getId(), reason);
        }
    }
}
