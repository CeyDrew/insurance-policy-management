package com.portfolio.insurance.claim;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

/**
 * @param documentDir     root directory of uploaded claim documents (local file system)
 * @param maxDocumentSize largest accepted upload
 */
@ConfigurationProperties("app.claims")
public record ClaimsProperties(Path documentDir, DataSize maxDocumentSize) {
}
