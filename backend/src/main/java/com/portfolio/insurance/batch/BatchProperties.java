package com.portfolio.insurance.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
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
 * @param collection             bank collection files
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
        Collection collection,
        Scheduling scheduling) {

    /**
     * @param enabled turns all cron triggers on or off
     * @param cron    job name -> Spring cron expression
     */
    public record Scheduling(boolean enabled, Map<String, String> cron) {
    }

    /**
     * Bank collection files (docs/bank-file-format.md).
     *
     * @param dir          root of outbound/, inbound/, inbox/, processed/ and error/
     * @param bankCode     4-digit bank code written into file headers
     * @param daysAhead    installments due within this many days of the run date are requested
     * @param partitions   default number of parallel partitions of the result import
     * @param pollInbox    watch inbox/ and import result files dropped there
     * @param pollInterval delay between two inbox scans
     * @param failAtLine   fault injection: the import crashes on this detail line (0 = off)
     */
    public record Collection(Path dir, String bankCode, int daysAhead, int partitions, boolean pollInbox,
                             Duration pollInterval, long failAtLine) {

        public Path outboundDir() {
            return dir.resolve("outbound");
        }

        public Path inboundDir() {
            return dir.resolve("inbound");
        }

        public Path inboxDir() {
            return dir.resolve("inbox");
        }

        public Path processedDir() {
            return dir.resolve("processed");
        }

        public Path errorDir() {
            return dir.resolve("error");
        }
    }
}
