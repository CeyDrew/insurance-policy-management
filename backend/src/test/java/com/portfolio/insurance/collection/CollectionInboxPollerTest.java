package com.portfolio.insurance.collection;

import com.portfolio.insurance.batch.BatchProperties;
import com.portfolio.insurance.batch.api.BatchDtos.ExecutionSummary;
import com.portfolio.insurance.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionInboxPollerTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    @TempDir
    private Path dir;

    private final CollectionFileService service = mock(CollectionFileService.class);
    private CollectionInboxPoller poller;
    private BatchProperties.Collection settings;

    @BeforeEach
    void setUp() {
        settings = new BatchProperties.Collection(dir, "0099", 7, 4, true, Duration.ofSeconds(10), 0);
        BatchProperties properties = new BatchProperties(100, 100, 30, 2, dir, dir, settings,
                new BatchProperties.Scheduling(false, Map.of()));
        poller = new CollectionInboxPoller(service, properties, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    @Test
    void importedFileGoesToProcessedAndFailedOrRefusedFilesToError() throws Exception {
        Path ok = drop("SN-ok.txt", NOW.minusSeconds(60));
        Path failed = drop("SN-failed.txt", NOW.minusSeconds(60));
        Path duplicate = drop("SN-duplicate.txt", NOW.minusSeconds(60));
        when(service.importResult(any(), eq("SN-ok.txt"), any(), eq("inbox"), eq(true))).thenReturn(execution("COMPLETED"));
        when(service.importResult(any(), eq("SN-failed.txt"), any(), anyString(), anyBoolean())).thenReturn(execution("FAILED"));
        when(service.importResult(any(), eq("SN-duplicate.txt"), any(), anyString(), anyBoolean()))
                .thenThrow(new BusinessException("DUPLICATE_FILE", "daha önce işlendi"));

        poller.poll();

        assertThat(ok).doesNotExist();
        assertThat(settings.processedDir().resolve("SN-ok.txt")).exists();
        assertThat(settings.errorDir().resolve("SN-failed.txt")).exists();
        assertThat(settings.errorDir().resolve("SN-duplicate.txt")).exists();
        assertThat(failed).doesNotExist();
        assertThat(duplicate).doesNotExist();
        // the job works on a copy in inbound/, the inbox file only records the outcome
        try (var inbound = Files.list(settings.inboundDir())) {
            assertThat(inbound).hasSize(3);
        }
    }

    @Test
    void fileStillBeingWrittenIsLeftForTheNextScan() throws Exception {
        Path fresh = drop("SN-copying.txt", NOW.minusSeconds(1));

        poller.poll();

        assertThat(fresh).exists();
        verify(service, never()).importResult(any(), anyString(), any(), anyString(), anyBoolean());
    }

    private Path drop(String name, Instant modified) throws Exception {
        Files.createDirectories(settings.inboxDir());
        Path file = Files.writeString(settings.inboxDir().resolve(name), "H...");
        Files.setLastModifiedTime(file, FileTime.from(modified));
        return file;
    }

    private static ExecutionSummary execution(String status) {
        return new ExecutionSummary(1L, "collectionResultImportJob", status, status, null, null, null, null, 0, 0, 0,
                Map.of());
    }
}
