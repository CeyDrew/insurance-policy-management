package com.portfolio.insurance.batch.expiration;

import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.batch.BatchProperties;
import com.portfolio.insurance.batch.support.JobLoggingListener;
import com.portfolio.insurance.batch.support.Notification;
import com.portfolio.insurance.batch.support.NotificationWriter;
import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.plsql.PlsqlErrors;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * policyExpirationJob: closes policies whose term ended before the run date.
 * A policy whose renewal was issued becomes RENEWED, every other one EXPIRED (with a notification).
 * The status change itself goes through PKG_POLICY.CHANGE_STATUS, which enforces the transition rules.
 */
@Configuration(proxyBeanMethods = false)
class PolicyExpirationJobConfig {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    record EndedPolicy(long policyId, String policyNumber, String status, LocalDate endDate, boolean renewalIssued,
                       long customerId, String email, String phone) {
    }

    record StatusChange(EndedPolicy policy, String newStatus) {
    }

    @Bean
    Job policyExpirationJob(JobRepository jobRepository, Step expirePoliciesStep, JobLoggingListener listener) {
        return new JobBuilder(BatchJobs.POLICY_EXPIRATION, jobRepository)
                .listener(listener)
                .start(expirePoliciesStep)
                .build();
    }

    @Bean
    Step expirePoliciesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                            JdbcCursorItemReader<EndedPolicy> endedPolicyReader, JdbcTemplate jdbc,
                            NotificationWriter notificationWriter, BatchProperties properties,
                            JobLoggingListener listener) {
        // SUSPENDED -> RENEWED is not an allowed transition, so a suspended policy always expires.
        ItemProcessor<EndedPolicy, StatusChange> processor = policy -> new StatusChange(policy,
                policy.renewalIssued() && "ACTIVE".equals(policy.status()) ? "RENEWED" : "EXPIRED");

        ItemWriter<StatusChange> writer = chunk -> {
            for (StatusChange change : chunk) {
                PlsqlErrors.call(() -> jdbc.update("BEGIN pkg_policy.change_status(?, ?, ?); END;",
                        change.policy().policyId(), change.newStatus(), BatchJobs.BATCH_USER));
            }
            notificationWriter.write(chunk.getItems().stream()
                    .filter(change -> "EXPIRED".equals(change.newStatus()))
                    .map(StatusChange::policy)
                    .map(p -> new Notification(p.customerId(), p.policyId(), "POLICY_EXPIRED", p.email(), p.phone(),
                            "Poliçenizin süresi doldu",
                            "%s numaralı poliçenizin süresi %s tarihinde dolmuştur. Teminatınızın devamı için acentenizle görüşün."
                                    .formatted(p.policyNumber(), p.endDate().format(DATE))))
                    .toList());
        };

        return new StepBuilder("expirePoliciesStep", jobRepository)
                .<EndedPolicy, StatusChange>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(endedPolicyReader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .skip(BusinessException.class)
                .skipLimit(properties.skipLimit())
                .listener(listener)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<EndedPolicy> endedPolicyReader(
            DataSource dataSource, @Value("#{jobParameters['" + BatchJobs.PARAM_RUN_DATE + "']}") LocalDate runDate) {
        return new JdbcCursorItemReaderBuilder<EndedPolicy>()
                .name("endedPolicyReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT p.id, p.policy_number, p.status, p.end_date, p.customer_id, c.email, c.phone,
                               CASE WHEN EXISTS (SELECT 1 FROM policy r
                                                  WHERE r.renewed_from_policy_id = p.id
                                                    AND r.status <> 'QUOTE') THEN 1 ELSE 0 END AS renewal_issued
                          FROM policy p
                          JOIN customer c ON c.id = p.customer_id
                         WHERE p.status IN ('ACTIVE', 'SUSPENDED')
                           AND p.end_date < ?
                         ORDER BY p.id
                        """)
                .preparedStatementSetter(ps -> ps.setDate(1, Date.valueOf(runDate)))
                .rowMapper((rs, rowNum) -> new EndedPolicy(rs.getLong("id"), rs.getString("policy_number"),
                        rs.getString("status"), rs.getObject("end_date", LocalDate.class),
                        rs.getInt("renewal_issued") == 1, rs.getLong("customer_id"), rs.getString("email"),
                        rs.getString("phone")))
                .build();
    }
}
