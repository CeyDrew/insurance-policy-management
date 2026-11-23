package com.portfolio.insurance.batch.overdue;

import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.batch.BatchProperties;
import com.portfolio.insurance.batch.support.JobLoggingListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Map;

/**
 * overdueInstallmentPlsqlJob: the same work as overdueInstallmentJob in a single call to
 * PKG_BATCH.MARK_OVERDUE_INSTALLMENTS (BULK COLLECT + FORALL). Used for large volumes and for the comparison
 * in docs/batch-jobs.md.
 * <p>
 * The procedure commits after every slice, so the tasklet runs without a surrounding Spring transaction.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
class OverdueInstallmentPlsqlJobConfig {

    @Bean
    Job overdueInstallmentPlsqlJob(JobRepository jobRepository, Step markOverduePlsqlStep, JobLoggingListener listener) {
        return new JobBuilder(BatchJobs.OVERDUE_INSTALLMENT_PLSQL, jobRepository)
                .listener(listener)
                .start(markOverduePlsqlStep)
                .build();
    }

    @Bean
    Step markOverduePlsqlStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              Tasklet markOverduePlsqlTasklet, JobLoggingListener listener) {
        return new StepBuilder("markOverduePlsqlStep", jobRepository)
                .tasklet(markOverduePlsqlTasklet, transactionManager)
                .transactionAttribute(new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_NOT_SUPPORTED))
                .listener(listener)
                .build();
    }

    @Bean
    @StepScope
    Tasklet markOverduePlsqlTasklet(JdbcTemplate jdbc, BatchProperties properties,
                                    @Value("#{jobParameters['" + BatchJobs.PARAM_RUN_DATE + "']}") LocalDate runDate) {
        SimpleJdbcCall call = new SimpleJdbcCall(jdbc)
                .withCatalogName("PKG_BATCH")
                .withProcedureName("MARK_OVERDUE_INSTALLMENTS")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_run_date", Types.DATE),
                        new SqlParameter("p_changed_by", Types.VARCHAR),
                        new SqlParameter("p_suspend_threshold", Types.NUMERIC),
                        new SqlParameter("p_limit", Types.NUMERIC),
                        new SqlOutParameter("p_marked", Types.NUMERIC),
                        new SqlOutParameter("p_suspended", Types.NUMERIC));
        return (contribution, chunkContext) -> {
            Map<String, Object> out = call.execute(new MapSqlParameterSource()
                    .addValue("p_run_date", Date.valueOf(runDate))
                    .addValue("p_changed_by", BatchJobs.BATCH_USER)
                    .addValue("p_suspend_threshold", properties.suspendAfterOverdue())
                    .addValue("p_limit", 5000));
            long marked = ((Number) out.get("p_marked")).longValue();
            long suspended = ((Number) out.get("p_suspended")).longValue();
            contribution.incrementWriteCount(marked + suspended);
            chunkContext.getStepContext().getStepExecution().getExecutionContext()
                    .putLong("markedInstallments", marked);
            chunkContext.getStepContext().getStepExecution().getExecutionContext()
                    .putLong("suspendedPolicies", suspended);
            log.info("PKG_BATCH marked {} installments overdue and suspended {} policies", marked, suspended);
            return RepeatStatus.FINISHED;
        };
    }
}
