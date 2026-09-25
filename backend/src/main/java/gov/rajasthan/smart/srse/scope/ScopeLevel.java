package gov.rajasthan.smart.srse.scope;

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
        name = "scope_level",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_scope_level_dimension_depth",
                columnNames = {"dimension_id", "depth"})
)
public class ScopeLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dimension_id", nullable = false)
    private ScopeDimension dimension;

    @Column(nullable = false)
    private int depth;

    @Column(nullable = false)
    private String name;

    protected ScopeLevel() {
    }

    public ScopeLevel(ScopeDimension dimension, int depth, String name) {
        this.dimension = dimension;
        this.depth = depth;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public ScopeDimension getDimension() {
        return dimension;
    }

    public int getDepth() {
        return depth;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
