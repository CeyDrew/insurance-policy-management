package com.portfolio.insurance.claim;

import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentStorageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

    @TempDir
    private Path dir;

    private DocumentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new DocumentStorage(new ClaimsProperties(dir, DataSize.ofBytes(64)));
    }

    @Test
    void detectsTypeFromContentNotFromTheClaimedNameOrContentType() throws Exception {
        // Named and labelled as PDF, but the bytes are a PNG.
        var file = new MockMultipartFile("file", "rapor.pdf", "application/pdf", PNG);

        DocumentStorage.StoredFile stored = storage.store(42, file);

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.storagePath()).startsWith("42/").endsWith(".png").doesNotContain("rapor");
        assertThat(stored.size()).isEqualTo(PNG.length);
        assertThat(Files.readAllBytes(dir.resolve(stored.storagePath()))).isEqualTo(PNG);
        assertThat(storage.load(stored.storagePath()).exists()).isTrue();
    }

    @Test
    void storesJpeg() {
        var stored = storage.store(7, new MockMultipartFile("file", "foto", null, JPEG));

        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.storagePath()).endsWith(".jpg");
    }

    @Test
    void rejectsEmptyOversizedAndUnknownFiles() {
        assertThatThrownBy(() -> storage.store(1, new MockMultipartFile("file", "a.pdf", null, new byte[0])))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("EMPTY_FILE"));
        assertThatThrownBy(() -> storage.store(1, new MockMultipartFile("file", "a.pdf", null, new byte[65])))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("FILE_TOO_LARGE"));
        assertThatThrownBy(() -> storage.store(1, new MockMultipartFile("file", "a.exe", null, "MZ\u0090\u0000".getBytes())))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_DOCUMENT_TYPE"));
        // Shorter than any signature
        assertThatThrownBy(() -> storage.store(1, new MockMultipartFile("file", "a", null, new byte[]{'%'})))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void loadRefusesPathsOutsideTheDocumentDirectoryAndMissingFiles() throws Exception {
        Files.writeString(dir.getParent().resolve("secret.txt"), "x");

        assertThatThrownBy(() -> storage.load("../secret.txt")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> storage.load("1/missing.pdf")).isInstanceOf(ResourceNotFoundException.class);
    }
}
