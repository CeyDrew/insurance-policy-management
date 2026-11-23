package com.portfolio.insurance.collection;

import com.portfolio.insurance.batch.BatchProperties;
import com.portfolio.insurance.batch.api.BatchDtos.ExecutionSummary;
import com.portfolio.insurance.common.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Watches {@code <collection dir>/inbox}: a result file dropped there (e.g. by an SFTP job) is moved to inbound/
 * and imported; afterwards the original goes to processed/ or, if the import failed or was refused, to error/.
 * Files modified in the last few seconds are left alone, in case they are still being copied.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnBooleanProperty("app.batch.collection.poll-inbox")
class CollectionInboxPoller {

    private static final Duration SETTLE_TIME = Duration.ofSeconds(3);

    private final CollectionFileService service;
    private final BatchProperties.Collection settings;
    private final Clock clock;

    CollectionInboxPoller(CollectionFileService service, BatchProperties properties, Clock clock) {
        this.service = service;
        this.settings = properties.collection();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.batch.collection.poll-interval:10s}")
    void poll() {
        for (Path file : readyFiles()) {
            process(file);
        }
    }

    List<Path> readyFiles() {
        Path inbox = settings.inboxDir();
        try {
            Files.createDirectories(inbox);
            Instant settled = clock.instant().minus(SETTLE_TIME);
            try (Stream<Path> files = Files.list(inbox)) {
                return files.filter(Files::isRegularFile)
                        .filter(file -> lastModified(file).isBefore(settled))
                        .sorted()
                        .toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not scan " + inbox, e);
        }
    }

    void process(Path file) {
        String name = file.getFileName().toString();
        boolean success;
        try {
            Path working = settings.inboundDir().resolve(clock.millis() + "-" + name);
            Files.createDirectories(working.getParent());
            Files.copy(file, working, StandardCopyOption.REPLACE_EXISTING);
            ExecutionSummary execution = service.importResult(working, name, null, "inbox", true);
            success = "COMPLETED".equals(execution.status());
            log.info("Inbox file {} imported by execution {}: {}", name, execution.id(), execution.status());
        } catch (BusinessException e) {
            log.warn("Inbox file {} refused: {}", name, e.getMessage());
            success = false;
        } catch (IOException e) {
            log.error("Inbox file {} could not be read", name, e);
            success = false;
        }
        moveTo(file, success ? settings.processedDir() : settings.errorDir());
    }

    private void moveTo(Path file, Path dir) {
        try {
            Files.createDirectories(dir);
            Files.move(file, dir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Could not move {} to {}", file, dir, e);
        }
    }

    private static Instant lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            return Instant.MAX;
        }
    }
}
