package gov.rajasthan.smart.srse.metadata.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "table_column_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_table_column_snapshot",
                columnNames = {"catalog_name", "schema_name", "table_name", "column_name"})
)
public class TableColumnSnapshot {

    private static final int IDENTIFIER_LENGTH = 128;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_name", nullable = false, length = IDENTIFIER_LENGTH)
    private String catalogName;

    @Column(name = "schema_name", nullable = false, length = IDENTIFIER_LENGTH)
    private String schemaName;

    @Column(name = "table_name", nullable = false, length = IDENTIFIER_LENGTH)
    private String tableName;

    @Column(name = "column_name", nullable = false, length = IDENTIFIER_LENGTH)
    private String columnName;

    @Column(name = "data_type", nullable = false, length = 256)
    private String dataType;

    @Column(name = "ordinal_position", nullable = false)
    private int ordinalPosition;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    protected TableColumnSnapshot() {
    }

    public TableColumnSnapshot(
            String catalogName,
            String schemaName,
            String tableName,
            String columnName,
            String dataType,
            int ordinalPosition,
            Instant syncedAt,
            Long runId) {
        this.catalogName = catalogName;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.columnName = columnName;
        this.dataType = dataType;
        this.ordinalPosition = ordinalPosition;
        this.syncedAt = syncedAt;
        this.runId = runId;
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

    public String getColumnName() {
        return columnName;
    }

    public String getDataType() {
        return dataType;
    }

    public int getOrdinalPosition() {
        return ordinalPosition;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
