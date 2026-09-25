package gov.rajasthan.smart.srse.compiler;

import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService.RegisteredColumn;
import gov.rajasthan.smart.srse.lakehouse.QualifiedColumn;
import gov.rajasthan.smart.srse.lakehouse.QualifiedTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Rule-to-SQL compiler for Extract Records (§4.5).
 *
 * <p>Walks a {@link Ast.PredicateSpec} and emits a parameterised WHERE fragment
 * for use inside a scoped derived table ({@code t} alias). Values are always
 * bound; columns resolve only through {@link RuleColumnResolver}.
 */
@Component
public class RuleCompiler {

    private static final String TABLE_ALIAS = "t";

    private final RuleColumnResolver columns;

    public RuleCompiler(RuleColumnResolver columns) {
        this.columns = columns;
    }

    public CompiledQuery compile(Ast.PredicateSpec spec, QualifiedTable table) {
        List<Object> params = new ArrayList<>();
        String sql = emit(spec.root(), table, params);
        return new CompiledQuery(sql, params);
    }

    private String emit(Ast.Node node, QualifiedTable table, List<Object> params) {
        if (node instanceof Ast.GroupNode g) {
            return emitGroup(g, table, params);
        }
        if (node instanceof Ast.PredicateNode p) {
            return emitPredicate(p, table, params);
        }
        throw new IllegalStateException("Unexpected node: " + node);
    }

    private String emitGroup(Ast.GroupNode g, QualifiedTable table, List<Object> params) {
        String joiner = g.op() == Ast.BoolOp.AND ? " AND " : " OR ";
        StringJoiner sj = new StringJoiner(joiner, "(", ")");
        for (Ast.Node child : g.children()) {
            sj.add(emit(child, table, params));
        }
        return sj.toString();
    }

    @SuppressWarnings("unchecked")
    private String emitPredicate(Ast.PredicateNode p, QualifiedTable table, List<Object> params) {
        RegisteredColumn described = columns.resolve(p.column(), table);
        SqlTypeFamily columnFamily = SqlTypeFamily.of(described.dataType());
        String colRef = columnRef(p.column().column(), columnFamily, p.operator(), p.value());
        return switch (p.operator()) {
            case EQ  -> bind(colRef + " = ?",  p.value(), params);
            case NE  -> bind(colRef + " <> ?", p.value(), params);
            case LT  -> bind(colRef + " < ?",  p.value(), params);
            case LTE -> bind(colRef + " <= ?", p.value(), params);
            case GT  -> bind(colRef + " > ?",  p.value(), params);
            case GTE -> bind(colRef + " >= ?", p.value(), params);

            case IS_TRUE  -> colRef + " = TRUE";
            case IS_FALSE -> colRef + " = FALSE";
            case IS_NULL  -> colRef + " IS NULL";
            case NOT_NULL -> colRef + " IS NOT NULL";

            case IN     -> emitIn(colRef, (List<Object>) p.value(), params, false);
            case NOT_IN -> emitIn(colRef, (List<Object>) p.value(), params, true);

            case BETWEEN -> emitBetween(colRef, (List<Object>) p.value(), params);

            case FUZZY_MATCH -> emitFuzzyMatch(colRef, (List<Object>) p.value(), params);
        };
    }

    private String columnRef(String columnName, SqlTypeFamily columnFamily,
                             Ast.Operator operator, Object value) {
        String bare = TABLE_ALIAS + "." + columnName;
        if (operator == Ast.Operator.IS_NULL
                || operator == Ast.Operator.NOT_NULL
                || operator == Ast.Operator.IS_TRUE
                || operator == Ast.Operator.IS_FALSE) {
            return bare;
        }
        if (columnFamily == SqlTypeFamily.BOOLEAN || columnFamily == SqlTypeFamily.TEMPORAL) {
            return bare;
        }
        if (value == null) {
            return bare;
        }
        return TypeCoercion.alignColumnToValue(bare, columnFamily, SqlTypeFamily.ofValue(value));
    }

    private String bind(String frag, Object value, List<Object> params) {
        params.add(value);
        return frag;
    }

    private String emitIn(String col, List<Object> values, List<Object> params, boolean negate) {
        if (values == null || values.isEmpty()) {
            return negate ? "TRUE" : "FALSE";
        }
        StringJoiner ph = new StringJoiner(", ", "(", ")");
        for (Object v : values) {
            ph.add("?");
            params.add(v);
        }
        return col + (negate ? " NOT IN " : " IN ") + ph;
    }

    private String emitBetween(String col, List<Object> bounds, List<Object> params) {
        if (bounds == null || bounds.size() != 2) {
            throw new IllegalArgumentException("BETWEEN requires exactly two bounds");
        }
        params.add(bounds.get(0));
        params.add(bounds.get(1));
        return col + " BETWEEN ? AND ?";
    }

    private String emitFuzzyMatch(String col, List<Object> value, List<Object> params) {
        if (value == null || value.size() != 2) {
            throw new IllegalArgumentException("FUZZY_MATCH requires [name, thresholdPercent]");
        }
        Object nameObj = value.get(0);
        if (!(nameObj instanceof String name) || name.isBlank()) {
            throw new IllegalArgumentException("FUZZY_MATCH requires a non-blank name");
        }
        double thresholdPct = ((Number) value.get(1)).doubleValue();
        if (thresholdPct < 0 || thresholdPct > 100) {
            throw new IllegalArgumentException("FUZZY_MATCH threshold must be between 0 and 100");
        }
        params.add(name);
        params.add(name);
        params.add(thresholdPct / 100.0);
        return FuzzyMatchSql.similarityExpr(col, "?") + " >= ?";
    }
}
