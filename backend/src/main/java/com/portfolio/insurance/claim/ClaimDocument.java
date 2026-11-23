package com.portfolio.insurance.claim;

import com.portfolio.insurance.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Metadata of an uploaded claim document. The file itself lives on the local file system under
 * {@code app.claims.document-dir}; {@code storagePath} is relative to that directory and never contains
 * user-supplied names.
 */
@Getter
@Entity
@Table(name = "claim_document")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "claim_document_seq")
    @SequenceGenerator(name = "claim_document_seq", sequenceName = "claim_document_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "storage_path", nullable = false, unique = true, length = 500)
    private String storagePath;

    ClaimDocument(Claim claim, DocumentType documentType, String fileName, String contentType, long fileSize,
                  String storagePath) {
        this.claim = claim;
        this.documentType = documentType;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.storagePath = storagePath;
    }
}
