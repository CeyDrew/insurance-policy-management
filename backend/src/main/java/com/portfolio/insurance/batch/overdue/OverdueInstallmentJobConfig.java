package com.portfolio.insurance.batch.overdue;

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
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * overdueInstallmentJob, two chunk-oriented steps:
 * <ol>
 *   <li>markOverdueStep: PENDING installments due before the run date become OVERDUE (+ notification),</li>
 *   <li>suspendPoliciesStep: ACTIVE policies with {@code app.batch.suspend-after-overdue} or more overdue
 *       installments are suspended through PKG_POLICY.CHANGE_STATUS (+ notification).</li>
 * </ol>
 * The same work done set-based in PL/SQL is {@code overdueInstallmentPlsqlJob}.
 */
@Configuration(proxyBeanMethods = false)
class OverdueInstallmentJobConfig {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    record OverdueInstallment(long installmentId, int installmentNo, LocalDate dueDate, BigDecimal amount,
                              long policyId, String policyNumber, long customerId, String email, String phone) {
    }

    record PolicyToSuspend(long policyId, String policyNumber, long customerId, int overdueCount, String email,
                           String phone) {
    }

    @Bean
    Job overdueInstallmentJob(JobRepository jobRepository, Step markOverdueStep, Step suspendPoliciesStep,
                              JobLoggingListener listener) {
        return new JobBuilder(BatchJobs.OVERDUE_INSTALLMENT, jobRepository)
                .listener(listener)
                .start(markOverdueStep)
                .next(suspendPoliciesStep)
                .build();
    }

    @Bean
    Step markOverdueStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                         JdbcCursorItemReader<OverdueInstallment> overdueInstallmentReader, JdbcTemplate jdbc,
                         NotificationWriter notificationWriter, BatchProperties properties, JobLoggingListener listener) {
        ItemWriter<OverdueInstallment> writer = chunk -> {
            jdbc.batchUpdate("""
                            UPDATE installment
                               SET status = 'OVERDUE', updated_at = SYSTIMESTAMP, updated_by = ?, version = version + 1
                             WHERE id = ? AND status = 'PENDING'
                            """,
                    chunk.getItems().stream().map(i -> new Object[]{BatchJobs.BATCH_USER, i.installmentId()}).toList());
            notificationWriter.write(chunk.getItems().stream()
                    .map(i -> new Notification(i.customerId(), i.policyId(), "INSTALLMENT_OVERDUE", i.email(), i.phone(),
                            "Gecikmiş taksit",
                            "%s numaralı poliçenizin %d. taksidi (%s TL, vade %s) ödenmemiştir.".formatted(
                                    i.policyNumber(), i.installmentNo(),
                                    String.format(Locale.forLanguageTag("tr-TR"), "%,.2f", i.amount()),
                                    i.dueDate().format(DATE))))
                    .toList());
        };
        return new StepBuilder("markOverdueStep", jobRepository)
                .<OverdueInstallment, OverdueInstallment>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(overdueInstallmentReader)
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .listener(listener)
                .build();
    }

    @Bean
    Step suspendPoliciesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                             JdbcCursorItemReader<PolicyToSuspend> policyToSuspendReader, JdbcTemplate jdbc,
                             NotificationWriter notificationWriter, BatchProperties properties,
                             JobLoggingListener listener) {
        ItemWriter<PolicyToSuspend> writer = chunk -> {
            for (PolicyToSuspend policy : chunk) {
                // Transition rules live in PKG_POLICY; a policy changed concurrently fails with a BusinessException
                // and is skipped.
                PlsqlErrors.call(() -> jdbc.update("BEGIN pkg_policy.change_status(?, 'SUSPENDED', ?); END;",
                        policy.policyId(), BatchJobs.BATCH_USER));
            }
            notificationWriter.write(chunk.getItems().stream()
                    .map(p -> new Notification(p.customerId(), p.policyId(), "POLICY_SUSPENDED", p.email(), p.phone(),
                            "Poliçeniz askıya alındı",
                            "%s numaralı poliçeniz %d ödenmemiş taksit nedeniyle askıya alınmıştır.".formatted(
                                    p.policyNumber(), p.overdueCount())))
                    .toList());
        };
        return new StepBuilder("suspendPoliciesStep", jobRepository)
                .<PolicyToSuspend, PolicyToSuspend>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(policyToSuspendReader)
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
    JdbcCursorItemReader<OverdueInstallment> overdueInstallmentReader(
            DataSource dataSource, @Value("#{jobParameters['" + BatchJobs.PARAM_RUN_DATE + "']}") LocalDate runDate) {
        return new JdbcCursorItemReaderBuilder<OverdueInstallment>()
                .name("overdueInstallmentReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT i.id, i.installment_no, i.due_date, i.amount, p.id AS policy_id, p.policy_number,
                               p.customer_id, c.email, c.phone
                          FROM installment i
                          JOIN policy p ON p.id = i.policy_id
                          JOIN customer c ON c.id = p.customer_id
                         WHERE i.status = 'PENDING'
                           AND i.due_date < ?
                           AND p.status IN ('ACTIVE', 'SUSPENDED')
                         ORDER BY i.id
                        """)
                .preparedStatementSetter(ps -> ps.setDate(1, Date.valueOf(runDate)))
                .rowMapper((rs, rowNum) -> new OverdueInstallment(rs.getLong("id"), rs.getInt("installment_no"),
                        rs.getObject("due_date", LocalDate.class), rs.getBigDecimal("amount"), rs.getLong("policy_id"),
                        rs.getString("policy_number"), rs.getLong("customer_id"), rs.getString("email"),
                        rs.getString("phone")))
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<PolicyToSuspend> policyToSuspendReader(DataSource dataSource, BatchProperties properties) {
        return new JdbcCursorItemReaderBuilder<PolicyToSuspend>()
                .name("policyToSuspendReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT p.id, p.policy_number, p.customer_id, o.overdue_count, c.email, c.phone
                          FROM policy p
                          JOIN customer c ON c.id = p.customer_id
                          JOIN (SELECT policy_id, COUNT(*) AS overdue_count
                                  FROM installment
                                 WHERE status = 'OVERDUE'
                                 GROUP BY policy_id) o ON o.policy_id = p.id
                         WHERE p.status = 'ACTIVE'
                           AND o.overdue_count >= ?
                         ORDER BY p.id
                        """)
                .preparedStatementSetter(ps -> ps.setInt(1, properties.suspendAfterOverdue()))
                .rowMapper((rs, rowNum) -> new PolicyToSuspend(rs.getLong("id"), rs.getString("policy_number"),
                        rs.getLong("customer_id"), rs.getInt("overdue_count"), rs.getString("email"),
                        rs.getString("phone")))
                .build();
    }
}
