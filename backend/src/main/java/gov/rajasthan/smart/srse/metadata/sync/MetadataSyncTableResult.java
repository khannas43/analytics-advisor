package gov.rajasthan.smart.srse.metadata.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "metadata_sync_table_result")
public class MetadataSyncTableResult {

    private static final int IDENTIFIER_LENGTH = 128;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "catalog_name", nullable = false, length = IDENTIFIER_LENGTH)
    private String catalogName;

    @Column(name = "schema_name", nullable = false, length = IDENTIFIER_LENGTH)
    private String schemaName;

    @Column(name = "table_name", nullable = false, length = IDENTIFIER_LENGTH)
    private String tableName;

    @Enumerated(EnumType.STRING)
    @Column(name = "table_outcome", nullable = false, length = 32)
    private MetadataTableOutcome tableOutcome;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    protected MetadataSyncTableResult() {
    }

    public MetadataSyncTableResult(
            Long runId,
            String catalogName,
            String schemaName,
            String tableName,
            MetadataTableOutcome tableOutcome,
            String errorMessage) {
        this.runId = runId;
        this.catalogName = catalogName;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.tableOutcome = tableOutcome;
        this.errorMessage = errorMessage;
    }

    public Long getRunId() {
        return runId;
    }

    public String getCatalogName() {
        return catalogName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public MetadataTableOutcome getTableOutcome() {
        return tableOutcome;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
