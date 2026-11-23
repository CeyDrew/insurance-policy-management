package com.portfolio.insurance.collection;

import com.portfolio.insurance.batch.BatchJobs;
import com.portfolio.insurance.batch.BatchProperties;
import com.portfolio.insurance.batch.api.BatchDtos.ExecutionSummary;
import com.portfolio.insurance.batch.api.BatchJobService;
import com.portfolio.insurance.batch.collection.BankFile;
import com.portfolio.insurance.batch.collection.BankSimulator;
import com.portfolio.insurance.batch.collection.FileHash;
import com.portfolio.insurance.collection.CollectionDtos.CollectionFileDetail;
import com.portfolio.insurance.collection.CollectionDtos.CollectionFileSummary;
import com.portfolio.insurance.collection.CollectionDtos.CollectionResultRow;
import com.portfolio.insurance.collection.CollectionDtos.Direction;
import com.portfolio.insurance.collection.CollectionDtos.FileStatus;
import com.portfolio.insurance.collection.CollectionDtos.Outcome;
import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.error.ResourceNotFoundException;
import com.portfolio.insurance.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bank collection files: generating request files, receiving result files (upload, inbox or the bank simulator)
 * and showing what each file did. The work itself is done by collectionRequestExportJob and
 * collectionResultImportJob.
 */
@Service
@RequiredArgsConstructor
public class CollectionFileService {

    // same as the multipart limit; larger files go through the inbox folder
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final CollectionFileRepository files;
    private final BatchJobService batchJobService;
    private final BatchProperties properties;
    private final Clock clock;

    public PageResponse<CollectionFileSummary> list(Direction direction, int page, int size) {
        int pageSize = Math.clamp(size, 1, 100);
        int pageIndex = Math.max(page, 0);
        long total = files.count(direction);
        return new PageResponse<>(files.page(direction, pageIndex * pageSize, pageSize), pageIndex, pageSize, total,
                (int) Math.ceil((double) total / pageSize));
    }

    public CollectionFileDetail detail(long id) {
        CollectionFileSummary file = get(id);
        return file.direction() == Direction.INBOUND
                ? new CollectionFileDetail(file, files.rejectReasons(id), List.of())
                : new CollectionFileDetail(file, List.of(), files.resultFilesOf(id));
    }

    public PageResponse<CollectionResultRow> results(long id, Outcome outcome, int page, int size) {
        get(id);
        int pageSize = Math.clamp(size, 1, 200);
        int pageIndex = Math.max(page, 0);
        long total = files.countResults(id, outcome);
        return new PageResponse<>(files.results(id, outcome, pageIndex * pageSize, pageSize), pageIndex, pageSize,
                total, (int) Math.ceil((double) total / pageSize));
    }

    public Resource download(long id) {
        get(id);
        Path root = properties.collection().dir().toAbsolutePath().normalize();
        Path file = files.storagePath(id).map(path -> Path.of(path).toAbsolutePath().normalize())
                .filter(path -> path.startsWith(root) && Files.isRegularFile(path))
                .orElseThrow(() -> new ResourceNotFoundException("Dosya bulunamadı."));
        return new FileSystemResource(file);
    }

    /** Starts collectionRequestExportJob; the new file appears in the list when the job has registered it. */
    public ExecutionSummary createRequestFile(LocalDate runDate, String user) {
        return batchJobService.launch(BatchJobs.COLLECTION_REQUEST_EXPORT,
                runDate == null ? Map.of() : Map.of(BatchJobs.PARAM_RUN_DATE, runDate.toString()), user);
    }

    /** Stores an uploaded result file and starts the import in the background. */
    public ExecutionSummary uploadResult(MultipartFile upload, String user) {
        String name = Optional.ofNullable(upload.getOriginalFilename()).map(n -> Path.of(n).getFileName().toString())
                .orElse("sonuc.txt");
        if (upload.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "Yüklenen dosya boş.");
        }
        if (upload.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE",
                    "Dosya en fazla 10 MB olabilir; daha büyük dosyaları inbox klasörüne bırakın.");
        }
        Path target = newInboundPath(name);
        try (InputStream in = upload.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + target, e);
        }
        return importResult(target, name, null, user, false);
    }

    /**
     * Demo only: plays the bank. Answers a generated request file with a result file (mostly collected, some
     * rejected) and imports it.
     */
    public ExecutionSummary simulateBankAnswer(long requestFileId, Integer partitions, String user) {
        CollectionFileSummary request = get(requestFileId);
        if (request.direction() != Direction.OUTBOUND || request.status() != FileStatus.GENERATED) {
            throw new BusinessException("NOT_A_REQUEST_FILE", "Sadece üretilmiş bir talep dosyası cevaplanabilir.");
        }
        if (request.answered()) {
            throw new BusinessException("ALREADY_ANSWERED", "Bu talep dosyasının sonucu zaten işlendi.");
        }
        Path requestPath = files.storagePath(requestFileId).map(Path::of)
                .orElseThrow(() -> new ResourceNotFoundException("Dosya bulunamadı."));
        LocalDate today = LocalDate.now(clock);
        String name = "SN-%s-%06d.txt".formatted(today.format(DateTimeFormatter.BASIC_ISO_DATE), requestFileId);
        Path target = newInboundPath(name);
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + target.getParent(), e);
        }
        BankSimulator.answer(requestPath, target, today);
        return importResult(target, name, partitions, user, false);
    }

    /**
     * Starts collectionResultImportJob on a stored file, refusing a copy of a file that has already been applied
     * (or is being applied).
     */
    public ExecutionSummary importResult(Path file, String name, Integer partitions, String user,
                                         boolean waitForCompletion) {
        String hash = FileHash.sha256(file);
        if (files.existsActiveInbound(hash)) {
            throw new BusinessException("DUPLICATE_FILE", "Bu sonuç dosyası daha önce işlendi veya işleniyor.");
        }
        return batchJobService.importCollectionResult(file, name, hash, partitions, user, waitForCompletion);
    }

    /** Resumes the import of a file that stopped half way. */
    public ExecutionSummary restart(long id) {
        CollectionFileSummary file = get(id);
        if (!file.interrupted() || file.jobExecutionId() == null) {
            throw new BusinessException("NOT_RESTARTABLE",
                    "Sadece yarıda kalmış bir sonuç dosyası yeniden başlatılabilir.");
        }
        return batchJobService.restart(file.jobExecutionId());
    }

    /** Result codes and their meaning, for the screen's legend. */
    public Map<String, String> resultCodes() {
        return BankFile.RESULT_CODES;
    }

    private CollectionFileSummary get(long id) {
        return files.find(id).orElseThrow(() -> new ResourceNotFoundException("Tahsilat dosyası", id));
    }

    /** Stored under a unique name; the original name is kept as a job parameter and on COLLECTION_FILE. */
    private Path newInboundPath(String originalName) {
        String safe = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        return properties.collection().inboundDir().toAbsolutePath()
                .resolve(LocalDateTime.now(clock).format(STAMP) + "-" + UUID.randomUUID().toString().substring(0, 8)
                        + "-" + safe);
    }
}
