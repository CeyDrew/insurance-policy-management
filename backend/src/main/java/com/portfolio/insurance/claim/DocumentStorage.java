package com.portfolio.insurance.claim;

import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

/**
 * Stores claim documents on the local file system. Only PDF, JPEG and PNG are accepted, recognized by their
 * first bytes (not by the client-supplied content type). Files are saved as {@code <claimId>/<uuid>.<ext>},
 * so user-supplied file names never reach the file system.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ClaimsProperties.class)
class DocumentStorage {

    enum FileKind {
        PDF("application/pdf", "pdf", new byte[]{'%', 'P', 'D', 'F'}),
        PNG("image/png", "png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}),
        JPEG("image/jpeg", "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        final String contentType;
        final String extension;
        final byte[] signature;

        FileKind(String contentType, String extension, byte[] signature) {
            this.contentType = contentType;
            this.extension = extension;
            this.signature = signature;
        }
    }

    record StoredFile(String storagePath, String contentType, long size) {
    }

    private final ClaimsProperties properties;

    StoredFile store(long claimId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "Yüklenen dosya boş.");
        }
        if (file.getSize() > properties.maxDocumentSize().toBytes()) {
            throw new BusinessException("FILE_TOO_LARGE",
                    "Dosya en fazla %d MB olabilir.".formatted(properties.maxDocumentSize().toMegabytes()));
        }
        FileKind kind = detect(file);
        String relative = claimId + "/" + UUID.randomUUID() + "." + kind.extension;
        Path target = root().resolve(relative).normalize();
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store claim document " + target, e);
        }
        return new StoredFile(relative, kind.contentType, file.getSize());
    }

    Resource load(String storagePath) {
        Path file = root().resolve(storagePath).normalize();
        if (!file.startsWith(root()) || !Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Evrak dosyası bulunamadı.");
        }
        return new FileSystemResource(file);
    }

    private Path root() {
        return properties.documentDir().toAbsolutePath().normalize();
    }

    private static FileKind detect(MultipartFile file) {
        byte[] head = new byte[4];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(head, 0, head.length);
            for (FileKind kind : FileKind.values()) {
                if (read >= kind.signature.length
                        && Arrays.equals(Arrays.copyOf(head, kind.signature.length), kind.signature)) {
                    return kind;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read uploaded file", e);
        }
        throw new BusinessException("INVALID_DOCUMENT_TYPE", "Sadece PDF, JPEG veya PNG dosyası yüklenebilir.");
    }
}
