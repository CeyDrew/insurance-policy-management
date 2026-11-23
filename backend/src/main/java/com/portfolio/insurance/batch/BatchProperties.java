package com.portfolio.insurance.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Map;

/**
 * Settings of the batch jobs ({@code app.batch.*}).
 *
 * @param chunkSize              items per transaction
 * @param skipLimit              skipped items tolerated before a step fails
 * @param renewalDaysAhead       policies ending within this many days get a renewal quote
 * @param suspendAfterOverdue    number of overdue installments that suspends a policy
 * @param importDir              where uploaded claim CSV files are stored
 * @param reportDir              where generated Excel reports are written
 * @param scheduling             cron triggers; disabled by default for the local demo
 */
@ConfigurationProperties("app.batch")
public record BatchProperties(
        int chunkSize,
        int skipLimit,
        int renewalDaysAhead,
        int suspendAfterOverdue,
        Path importDir,
        Path reportDir,
        Scheduling scheduling) {

    /**
     * @param enabled turns all cron triggers on or off
     * @param cron    job name -> Spring cron expression
     */
    public record Scheduling(boolean enabled, Map<String, String> cron) {
    }
}
