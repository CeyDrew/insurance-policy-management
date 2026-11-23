package com.portfolio.insurance.batch.report;

import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.batch.BatchProperties;
import com.portfolio.insurance.batch.support.JobLoggingListener;
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
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.YearMonth;

/**
 * monthlyProductionReportJob: premium production (policies issued in the month) grouped by product and agent,
 * written to {@code app.batch.report-dir}/uretim-raporu-yyyy-MM.xlsx with Apache POI.
 */
@Configuration(proxyBeanMethods = false)
class ProductionReportJobConfig {

    @Bean
    Job monthlyProductionReportJob(JobRepository jobRepository, Step productionReportStep, JobLoggingListener listener) {
        return new JobBuilder(BatchJobs.MONTHLY_PRODUCTION_REPORT, jobRepository)
                .listener(listener)
                .start(productionReportStep)
                .build();
    }

    @Bean
    Step productionReportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              JdbcCursorItemReader<ProductionRow> productionRowReader,
                              ExcelProductionReportWriter productionReportWriter, BatchProperties properties,
                              JobLoggingListener listener) {
        return new StepBuilder("productionReportStep", jobRepository)
                .<ProductionRow, ProductionRow>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(productionRowReader)
                .writer(productionReportWriter)
                .listener(listener)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<ProductionRow> productionRowReader(
            DataSource dataSource, @Value("#{jobParameters['" + BatchJobs.PARAM_MONTH + "']}") String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        return new JdbcCursorItemReaderBuilder<ProductionRow>()
                .name("productionRowReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT pr.name AS product_name, u.full_name AS agent_name,
                               COUNT(*) AS policy_count,
                               SUM(CASE WHEN p.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count,
                               SUM(p.net_premium) AS net_premium, SUM(p.tax_amount) AS tax_amount,
                               SUM(p.gross_premium) AS gross_premium, NVL(SUM(p.refund_amount), 0) AS refunds
                          FROM policy p
                          JOIN product pr ON pr.id = p.product_id
                          JOIN app_user u ON u.id = p.agent_id
                         WHERE p.issued_at >= ? AND p.issued_at < ?
                         GROUP BY pr.id, pr.name, u.full_name
                         ORDER BY pr.id, u.full_name
                        """)
                .preparedStatementSetter(ps -> {
                    ps.setTimestamp(1, Timestamp.valueOf(yearMonth.atDay(1).atStartOfDay()));
                    ps.setTimestamp(2, Timestamp.valueOf(yearMonth.plusMonths(1).atDay(1).atStartOfDay()));
                })
                .rowMapper((rs, rowNum) -> new ProductionRow(rs.getString("product_name"), rs.getString("agent_name"),
                        rs.getLong("policy_count"), rs.getLong("cancelled_count"), rs.getBigDecimal("net_premium"),
                        rs.getBigDecimal("tax_amount"), rs.getBigDecimal("gross_premium"), rs.getBigDecimal("refunds")))
                .build();
    }

    @Bean
    @StepScope
    ExcelProductionReportWriter productionReportWriter(
            BatchProperties properties, @Value("#{jobParameters['" + BatchJobs.PARAM_MONTH + "']}") String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        return new ExcelProductionReportWriter(
                properties.reportDir().resolve(ExcelProductionReportWriter.fileName(yearMonth)), yearMonth);
    }
}
