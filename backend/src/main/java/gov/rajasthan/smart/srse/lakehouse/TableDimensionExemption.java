package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.scope.ScopeDimension;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "table_dimension_exemption")
public class TableDimensionExemption {

    @EmbeddedId
    private TableDimensionExemptionId id = new TableDimensionExemptionId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("registeredTableId")
    @JoinColumn(name = "registered_table_id", nullable = false)
    private RegisteredTable registeredTable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("scopeDimensionId")
    @JoinColumn(name = "scope_dimension_id", nullable = false)
    private ScopeDimension scopeDimension;

    protected TableDimensionExemption() {
    }

    public TableDimensionExemption(RegisteredTable registeredTable, ScopeDimension scopeDimension) {
        this.registeredTable = registeredTable;
        this.scopeDimension = scopeDimension;
    }

    @PrePersist
    void assignId() {
        this.id = new TableDimensionExemptionId(registeredTable.getId(), scopeDimension.getId());
    }

    public RegisteredTable getRegisteredTable() {
        return registeredTable;
    }

    public ScopeDimension getScopeDimension() {
        return scopeDimension;
    }
}
