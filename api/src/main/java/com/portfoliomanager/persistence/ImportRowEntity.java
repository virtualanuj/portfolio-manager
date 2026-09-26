package com.portfoliomanager.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One staged CSV row; the JSON columns hold text that the import service reads and writes. */
@Entity
@Table(name = "import_row")
@IdClass(ImportRowId.class)
public class ImportRowEntity {

    @Id
    @Column(name = "batch_id")
    private UUID batchId;

    @Id
    @Column(name = "line_no")
    private int lineNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String raw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String parsed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String errors = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String warnings = "[]";

    protected ImportRowEntity() {}

    public ImportRowEntity(
            UUID batchId, int lineNo, String raw, String parsed, String errors, String warnings) {
        this.batchId = batchId;
        this.lineNo = lineNo;
        this.raw = raw;
        this.parsed = parsed;
        this.errors = errors;
        this.warnings = warnings;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getRaw() {
        return raw;
    }

    public String getParsed() {
        return parsed;
    }

    public String getErrors() {
        return errors;
    }

    public String getWarnings() {
        return warnings;
    }
}
