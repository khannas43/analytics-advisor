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
        name = "scope_node",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_scope_node_dimension_code",
                columnNames = {"dimension_id", "code"})
)
public class ScopeNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dimension_id", nullable = false)
    private ScopeDimension dimension;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private ScopeLevel level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ScopeNode parent;

    @Column(nullable = false, length = 128)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Materialised path for subtree / prefix matching (A3). */
    @Column(nullable = false, length = 1024)
    private String path;

    protected ScopeNode() {
    }

    public ScopeNode(ScopeDimension dimension, ScopeLevel level, ScopeNode parent,
                     String code, String name, String path) {
        this.dimension = dimension;
        this.level = level;
        this.parent = parent;
        this.code = code;
        this.name = name;
        this.path = path;
    }

    public Long getId() {
        return id;
    }

    public ScopeDimension getDimension() {
        return dimension;
    }

    public ScopeLevel getLevel() {
        return level;
    }

    public ScopeNode getParent() {
        return parent;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }
}
