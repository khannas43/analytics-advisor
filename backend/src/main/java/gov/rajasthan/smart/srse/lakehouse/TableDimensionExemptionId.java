package gov.rajasthan.smart.srse.lakehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TableDimensionExemptionId implements Serializable {

    @Column(name = "registered_table_id")
    private Long registeredTableId;

    @Column(name = "scope_dimension_id")
    private Long scopeDimensionId;

    protected TableDimensionExemptionId() {
    }

    public TableDimensionExemptionId(Long registeredTableId, Long scopeDimensionId) {
        this.registeredTableId = registeredTableId;
        this.scopeDimensionId = scopeDimensionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TableDimensionExemptionId that)) {
            return false;
        }
        return Objects.equals(registeredTableId, that.registeredTableId)
                && Objects.equals(scopeDimensionId, that.scopeDimensionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registeredTableId, scopeDimensionId);
    }
}
