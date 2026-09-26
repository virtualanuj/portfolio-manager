package com.portfoliomanager.persistence;

import com.portfoliomanager.domain.ImportBatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An uploaded CSV waiting for the user to review and commit it. */
@Entity
@Table(name = "import_batch")
public class ImportBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportBatchStatus status = ImportBatchStatus.STAGED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ImportBatchEntity() {}

    public ImportBatchEntity(String filename, Instant createdAt) {
        this.filename = filename;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public ImportBatchStatus getStatus() {
        return status;
    }

    public void markCommitted() {
        this.status = ImportBatchStatus.COMMITTED;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
