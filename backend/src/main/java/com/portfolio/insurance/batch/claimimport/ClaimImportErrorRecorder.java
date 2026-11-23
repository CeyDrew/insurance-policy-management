package com.portfolio.insurance.batch.claimimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Writes every skipped CSV line to CLAIM_IMPORT_ERROR with its line number and reason, so the import report
 * (REST/UI) can show exactly which lines were rejected. Step scoped: holds the current job execution id.
 */
@Slf4j
@RequiredArgsConstructor
class ClaimImportErrorRecorder implements SkipListener<ClaimCsvLine, ClaimImportItem>, StepExecutionListener {

    private static final int MAX_LINE = 4000;
    private static final int MAX_MESSAGE = 1000;

    private final JdbcTemplate jdbc;
    private long jobExecutionId;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.jobExecutionId = stepExecution.getJobExecutionId();
    }

    @Override
    public void onSkipInRead(Throwable t) {
        if (t instanceof FlatFileParseException parse) {
            record(parse.getLineNumber(), parse.getInput(), "Satır okunamadı: " + parse.getMessage());
        }
    }

    @Override
    public void onSkipInProcess(ClaimCsvLine line, Throwable t) {
        record(line.lineNumber(), line.content(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(ClaimImportItem item, Throwable t) {
        record(item.source().lineNumber(), item.source().content(), "Kayıt yazılamadı: " + t.getMessage());
    }

    private void record(int lineNumber, String rawLine, String message) {
        log.debug("Claim import line {} skipped: {}", lineNumber, message);
        jdbc.update("""
                INSERT INTO claim_import_error (job_execution_id, line_number, raw_line, error_message)
                VALUES (?, ?, ?, ?)
                """, jobExecutionId, lineNumber, truncate(rawLine, MAX_LINE),
                truncate(message == null ? "Bilinmeyen hata" : message, MAX_MESSAGE));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
