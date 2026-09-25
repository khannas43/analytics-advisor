package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.scope.ScopeLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "table_scope_binding",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_table_scope_binding_table_level",
                columnNames = {"registered_table_id", "scope_level_id"})
)
public class TableScopeBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registered_table_id", nullable = false)
    private RegisteredTable registeredTable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scope_level_id", nullable = false)
    private ScopeLevel scopeLevel;

    @Column(name = "column_name", nullable = false, length = 128)
    private String columnName;

    protected TableScopeBinding() {
    }

    public TableScopeBinding(RegisteredTable registeredTable, ScopeLevel scopeLevel, String columnName) {
        this.registeredTable = registeredTable;
        this.scopeLevel = scopeLevel;
        this.columnName = columnName;
    }

    public Long getId() {
        return id;
    }

    public RegisteredTable getRegisteredTable() {
        return registeredTable;
    }

    public ScopeLevel getScopeLevel() {
        return scopeLevel;
    }

    public String getColumnName() {
        return columnName;
    }
}
