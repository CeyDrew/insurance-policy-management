package com.portfolio.insurance.batch.renewal;

import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.batch.BatchProperties;
import com.portfolio.insurance.batch.support.JobLoggingListener;
import com.portfolio.insurance.batch.support.NotificationWriter;
import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.premium.PremiumEngine;
import com.portfolio.insurance.reference.ProductCode;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
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

/**
 * policyRenewalJob: creates renewal quotes for active policies ending within {@code app.batch.renewal-days-ahead}
 * days of the run date. Premiums come from PKG_PREMIUM. Re-running is safe: policies that already have a
 * renewal quote are not read again.
 */
@Configuration(proxyBeanMethods = false)
class PolicyRenewalJobConfig {

    private static final String CANDIDATES_SQL = """
            SELECT p.id, p.policy_number, p.product_id, pr.code AS product_code, pr.term_months, p.customer_id,
                   p.vehicle_id, p.property_id, p.agent_id, p.end_date, p.installment_count,
                   pkg_policy.next_ncd_step(p.id) AS next_ncd_step,
                   NVL(c.company_name, c.first_name || ' ' || c.last_name) AS customer_name, c.email, c.phone
              FROM policy p
              JOIN product pr ON pr.id = p.product_id
              JOIN customer c ON c.id = p.customer_id
             WHERE p.status = 'ACTIVE'
               AND p.end_date BETWEEN ? AND ?
               AND pr.active = 1
               AND NOT EXISTS (SELECT 1 FROM policy r WHERE r.renewed_from_policy_id = p.id)
             ORDER BY p.id
            """;

    @Bean
    Job policyRenewalJob(JobRepository jobRepository, Step renewalQuoteStep, JobLoggingListener listener) {
        return new JobBuilder(BatchJobs.POLICY_RENEWAL, jobRepository)
                .listener(listener)
                .start(renewalQuoteStep)
                .build();
    }

    @Bean
    Step renewalQuoteStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                          JdbcCursorItemReader<RenewalCandidate> renewalCandidateReader, PremiumEngine premiumEngine,
                          JdbcTemplate jdbc, NotificationWriter notificationWriter, BatchProperties properties,
                          JobLoggingListener listener) {
        return new StepBuilder("renewalQuoteStep", jobRepository)
                .<RenewalCandidate, RenewalQuote>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(renewalCandidateReader)
                .processor(new RenewalQuoteProcessor(premiumEngine))
                .writer(new RenewalQuoteWriter(jdbc, notificationWriter))
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
    JdbcCursorItemReader<RenewalCandidate> renewalCandidateReader(
            DataSource dataSource, BatchProperties properties,
            @Value("#{jobParameters['" + BatchJobs.PARAM_RUN_DATE + "']}") LocalDate runDate) {
        return new JdbcCursorItemReaderBuilder<RenewalCandidate>()
                .name("renewalCandidateReader")
                .dataSource(dataSource)
                .sql(CANDIDATES_SQL)
                .preparedStatementSetter(ps -> {
                    ps.setDate(1, Date.valueOf(runDate));
                    ps.setDate(2, Date.valueOf(runDate.plusDays(properties.renewalDaysAhead())));
                })
                .rowMapper((rs, rowNum) -> new RenewalCandidate(
                        rs.getLong("id"), rs.getString("policy_number"), rs.getLong("product_id"),
                        ProductCode.valueOf(rs.getString("product_code")), rs.getInt("term_months"),
                        rs.getLong("customer_id"), rs.getObject("vehicle_id", Long.class),
                        rs.getObject("property_id", Long.class), rs.getLong("agent_id"),
                        rs.getObject("end_date", LocalDate.class), rs.getInt("installment_count"),
                        rs.getInt("next_ncd_step"), rs.getString("customer_name"), rs.getString("email"),
                        rs.getString("phone")))
                .build();
    }
}
