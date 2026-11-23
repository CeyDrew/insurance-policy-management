package com.portfolio.insurance.batch.claimimport;

import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.batch.BatchProperties;
import com.portfolio.insurance.batch.support.JobLoggingListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

/**
 * claimImportJob: imports claim notifications (hasar ihbarı) from a semicolon-separated UTF-8 CSV file with a
 * header line. Invalid lines are skipped (up to {@code app.batch.skip-limit}) and recorded in CLAIM_IMPORT_ERROR;
 * the job then ends with exit code COMPLETED_WITH_SKIPS. Beyond the limit the job fails.
 */
@Configuration(proxyBeanMethods = false)
class ClaimImportJobConfig {

    @Bean
    Job claimImportJob(JobRepository jobRepository, Step importClaimsStep, JobLoggingListener listener) {
        return new JobBuilder(BatchJobs.CLAIM_IMPORT, jobRepository)
                .listener(listener)
                .start(importClaimsStep)
                .build();
    }

    @Bean
    Step importClaimsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                          FlatFileItemReader<ClaimCsvLine> claimCsvReader, ClaimCsvProcessor claimCsvProcessor,
                          ClaimImportErrorRecorder claimImportErrorRecorder, JdbcTemplate jdbc,
                          BatchProperties properties, JobLoggingListener listener) {
        return new StepBuilder("importClaimsStep", jobRepository)
                .<ClaimCsvLine, ClaimImportItem>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(claimCsvReader)
                .processor(claimCsvProcessor)
                .writer(new ClaimImportWriter(jdbc))
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .skip(ClaimImportException.class, FlatFileParseException.class)
                .skipLimit(properties.skipLimit())
                .skipListener(claimImportErrorRecorder)
                .listener(claimImportErrorRecorder)
                .listener(listener)
                .build();
    }

    @Bean
    @StepScope
    FlatFileItemReader<ClaimCsvLine> claimCsvReader(
            @Value("#{jobParameters['" + BatchJobs.PARAM_INPUT_FILE + "']}") String inputFile) {
        return new FlatFileItemReaderBuilder<ClaimCsvLine>()
                .name("claimCsvReader")
                .resource(new FileSystemResource(inputFile))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1) // header
                .lineMapper((line, lineNumber) -> new ClaimCsvLine(lineNumber, line))
                .build();
    }

    @Bean
    @StepScope
    ClaimCsvProcessor claimCsvProcessor(JdbcTemplate jdbc, Clock clock) {
        return new ClaimCsvProcessor(jdbc, LocalDate.now(clock));
    }

    @Bean
    @StepScope
    ClaimImportErrorRecorder claimImportErrorRecorder(JdbcTemplate jdbc) {
        return new ClaimImportErrorRecorder(jdbc);
    }
}
