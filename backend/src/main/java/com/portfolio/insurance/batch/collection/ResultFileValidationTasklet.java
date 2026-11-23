package com.portfolio.insurance.batch.collection;

import com.portfolio.insurance.batch.collection.BankRecord.Detail;
import com.portfolio.insurance.batch.collection.BankRecord.Header;
import com.portfolio.insurance.batch.collection.BankRecord.Trailer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;

/**
 * First step of collectionResultImportJob: reads the whole result file once and checks its structure before
 * anything is applied. Header first, trailer last, consecutive line numbers, and the trailer's record count and
 * total amount must equal the detail lines. A file that fails is registered as FAILED (in its own transaction,
 * so the record survives the step's rollback) and the job fails without touching a single installment.
 * <p>
 * A valid file is registered as PROCESSING; its id and detail count go into the job execution context for the
 * partitioner and the writer. The unique index on the file hash rejects a second copy of an applied file.
 */
class ResultFileValidationTasklet implements Tasklet {

    static final String CONTEXT_FILE_ID = "collectionFileId";
    static final String CONTEXT_DETAIL_COUNT = "detailCount";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate separateTransaction;
    private final Path inputFile;
    private final String fileName;
    private final String fileHash;
    private final String launchedBy;
    private final LocalDate today;

    ResultFileValidationTasklet(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Path inputFile,
                                String fileName, String fileHash, String launchedBy, LocalDate today) {
        this.jdbc = jdbc;
        this.separateTransaction = new TransactionTemplate(transactionManager);
        this.separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.inputFile = inputFile;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.launchedBy = launchedBy;
        this.today = today;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        long jobExecutionId = chunkContext.getStepContext().getStepExecution().getJobExecutionId();
        Summary summary;
        Long requestFileId;
        try {
            summary = read();
            requestFileId = requestFile(summary.header().fileNumber());
        } catch (CollectionFileException e) {
            separateTransaction.executeWithoutResult(status ->
                    register("FAILED", null, today, null, null, e.getMessage(), jobExecutionId));
            throw e;
        }

        long fileId;
        try {
            fileId = register("PROCESSING", requestFileId, summary.header().fileDate(), summary.detailCount(),
                    summary.total(), null, jobExecutionId);
        } catch (DuplicateKeyException e) {
            throw new CollectionFileException("Bu sonuç dosyası daha önce işlendi.");
        }
        contribution.incrementReadCount();

        ExecutionContext jobContext = chunkContext.getStepContext().getStepExecution().getJobExecution()
                .getExecutionContext();
        jobContext.putLong(CONTEXT_FILE_ID, fileId);
        jobContext.putLong(CONTEXT_DETAIL_COUNT, summary.detailCount());
        return RepeatStatus.FINISHED;
    }

    private record Summary(Header header, long detailCount, BigDecimal total) {
    }

    private Summary read() throws Exception {
        FlatFileItemReader<BankRecord> reader = new FlatFileItemReaderBuilder<BankRecord>()
                .name("resultFileValidationReader")
                .resource(new FileSystemResource(inputFile))
                .encoding("US-ASCII")
                .lineMapper(BankFile.lineMapper())
                .saveState(false)
                .build();
        reader.open(new ExecutionContext());
        try {
            Header header = null;
            Trailer trailer = null;
            long details = 0;
            BigDecimal total = BigDecimal.ZERO;
            BankRecord record;
            while ((record = read(reader)) != null) {
                if (trailer != null) {
                    throw new CollectionFileException("Trailer kaydından sonra satır var.");
                }
                switch (record) {
                    case Header h -> {
                        if (header != null) {
                            throw new CollectionFileException("Dosyada birden fazla header kaydı var.");
                        }
                        header = h;
                    }
                    case Detail d -> {
                        if (header == null) {
                            throw new CollectionFileException("Dosya header kaydıyla başlamıyor.");
                        }
                        details++;
                        if (d.lineNo() != details) {
                            throw new CollectionFileException("Detay sıra numarası beklenen %d, bulunan %d."
                                    .formatted(details, d.lineNo()));
                        }
                        total = total.add(d.amount());
                    }
                    case Trailer t -> trailer = t;
                }
            }
            if (header == null) {
                throw new CollectionFileException("Dosya boş veya header kaydı yok.");
            }
            if (!BankFile.TYPE_RESULT.equals(header.fileType())) {
                throw new CollectionFileException("Dosya tipi '%s'; sonuç dosyası ('%s') bekleniyordu."
                        .formatted(header.fileType(), BankFile.TYPE_RESULT));
            }
            if (trailer == null) {
                throw new CollectionFileException("Trailer kaydı yok; dosya eksik aktarılmış olabilir.");
            }
            if (trailer.recordCount() != details) {
                throw new CollectionFileException("Trailer kayıt sayısı %d, dosyadaki detay sayısı %d."
                        .formatted(trailer.recordCount(), details));
            }
            if (trailer.totalAmount().compareTo(total) != 0) {
                throw new CollectionFileException("Trailer toplam tutarı %s TL, detayların toplamı %s TL."
                        .formatted(trailer.totalAmount().toPlainString(), total.toPlainString()));
            }
            return new Summary(header, details, total);
        } finally {
            reader.close();
        }
    }

    private static BankRecord read(FlatFileItemReader<BankRecord> reader) throws Exception {
        try {
            return reader.read();
        } catch (FlatFileParseException e) {
            throw new CollectionFileException("Satır %d okunamadı (format dışı veya %d karakter değil)."
                    .formatted(e.getLineNumber(), BankFile.LINE_LENGTH));
        }
    }

    private Long requestFile(long fileNumber) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM collection_file WHERE id = ? AND direction = 'OUTBOUND'", Long.class, fileNumber);
        if (ids.isEmpty()) {
            throw new CollectionFileException("Header'daki dosya numarası (%d) gönderilmiş bir talep dosyasına ait değil."
                    .formatted(fileNumber));
        }
        return ids.getFirst();
    }

    private long register(String status, Long requestFileId, LocalDate fileDate, Long recordCount, BigDecimal total,
                          String error, long jobExecutionId) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO collection_file (direction, request_file_id, file_name, storage_path, file_hash,
                                                 file_date, status, record_count, total_amount, error_message,
                                                 job_execution_id, created_by, completed_at)
                    VALUES ('INBOUND', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 'FAILED' THEN SYSTIMESTAMP END)
                    """, new String[]{"id"});
            ps.setObject(1, requestFileId);
            ps.setString(2, fileName);
            ps.setString(3, inputFile.toString());
            ps.setString(4, fileHash);
            ps.setDate(5, Date.valueOf(fileDate));
            ps.setString(6, status);
            ps.setObject(7, recordCount);
            ps.setBigDecimal(8, total);
            ps.setString(9, error);
            ps.setLong(10, jobExecutionId);
            ps.setString(11, launchedBy);
            ps.setString(12, status);
            return ps;
        }, key);
        return key.getKeyAs(Number.class).longValue();
    }
}
