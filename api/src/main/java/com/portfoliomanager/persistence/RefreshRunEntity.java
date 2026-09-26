package com.portfoliomanager.persistence;

import com.portfoliomanager.domain.RefreshStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One refresh run. The partial unique index on {@code status = 'RUNNING'} means the database itself
 * allows only one running row, which is what makes refresh single-flight.
 */
@Entity
@Table(name = "refresh_run")
public class RefreshRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefreshStatus status;

    /** JSON array of per-instrument results. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String results = "[]";

    protected RefreshRunEntity() {}

    public static RefreshRunEntity started(Instant at) {
        RefreshRunEntity run = new RefreshRunEntity();
        run.startedAt = at;
        run.status = RefreshStatus.RUNNING;
        return run;
    }

    public void finish(RefreshStatus status, String resultsJson, Instant at) {
        this.status = status;
        this.results = resultsJson;
        this.finishedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public RefreshStatus getStatus() {
        return status;
    }

    public String getResults() {
        return results;
    }
}
