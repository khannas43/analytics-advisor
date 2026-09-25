package gov.rajasthan.smart.srse.analysis;

import gov.rajasthan.smart.srse.compiler.SqlTypeFamily;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService.RegisteredColumn;
import gov.rajasthan.smart.srse.lakehouse.QualifiedTable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/** Emits and validates GROUP BY / aggregate SELECT for grouped matches (§5.5 / AA-14). */
final class MatchGroupingSql {

    static final String NULL_GROUP_LABEL = "(NULL)";

    private MatchGroupingSql() {}

    static void validateGroupedRequest(RecordMatchRequest req, int maxKeys, int maxAggregates) {
        if (!req.grouped()) {
            return;
        }
        if (req.dedup() != null) {
            throw new IllegalArgumentException("Dedup cannot be combined with grouping");
        }
        if (!req.comparisonGroups().isEmpty() || req.mismatchOnly()) {
            throw new IllegalArgumentException("Post-join comparisons cannot be combined with grouping");
        }
        if (req.groupByColumns().size() > maxKeys) {
            throw new IllegalArgumentException(
                    "At most " + maxKeys + " grouping columns allowed");
        }
        if (req.aggregates().size() > maxAggregates) {
            throw new IllegalArgumentException(
                    "At most " + maxAggregates + " aggregates allowed");
        }
        if (req.aggregates().isEmpty() && req.groupByColumns().isEmpty()) {
            throw new IllegalArgumentException("Grouping requires at least one group key or aggregate");
        }
    }

    static void validateColumnsOnSides(
            RecordMatchRequest req,
            QualifiedTable sourceTable,
            QualifiedTable targetTable,
            LakehouseRegistryService registry) {
        List<DisplayColumn> all = new ArrayList<>();
        all.addAll(req.groupByColumns());
        for (AggregateSpec agg : req.aggregates()) {
            if (agg.column() != null) {
                all.add(agg.column());
            }
        }
        for (DisplayColumn col : all) {
            QualifiedTable table = col.qualifiedTable();
            if (!table.equals(sourceTable) && !table.equals(targetTable)) {
                throw new IllegalArgumentException(
                        "Grouping column must be on source or target table: " + table.qualifiedName());
            }
            registry.validateColumn(col.qualifiedColumn());
        }
        for (AggregateSpec agg : req.aggregates()) {
            if (agg.column() == null) {
                continue;
            }
            RegisteredColumn described = registry.describeColumns(
                    agg.column().qualifiedTable(), List.of(agg.column().column()))
                    .get(agg.column().column());
            validateAggregateType(agg, described);
        }
    }

    static void validateAggregateType(AggregateSpec agg, RegisteredColumn described) {
        if (agg.countStar() || described == null) {
            return;
        }
        SqlTypeFamily family = SqlTypeFamily.of(described.dataType());
        if (agg.function() == AggregateFunction.SUM || agg.function() == AggregateFunction.AVG) {
            if (family != SqlTypeFamily.NUMBER) {
                throw new IllegalArgumentException(
                        agg.function() + " requires a numeric column; "
                                + agg.column().column() + " is " + described.dataType()
                                + ". Use COUNT, MIN/MAX on text or dates, or fix the type upstream.");
            }
        }
    }

    static void appendGroupedSelect(
            StringBuilder select,
            Set<String> outerColumns,
            RecordMatchRequest req,
            QualifiedTable sourceTable,
            QualifiedTable targetTable) {
        boolean first = true;
        for (DisplayColumn g : req.groupByColumns()) {
            if (!first) {
                select.append(", ");
            }
            first = false;
            String side = sideAlias(g.qualifiedTable(), sourceTable, targetTable);
            String outAlias = allocateUniqueAlias(groupKeyAlias(g, side), outerColumns);
            String expr = side + "." + g.column();
            select.append("COALESCE(CAST(").append(expr).append(" AS VARCHAR), '")
                    .append(NULL_GROUP_LABEL).append("') AS \"").append(outAlias).append("\"");
        }
        for (AggregateSpec agg : req.aggregates()) {
            if (!first) {
                select.append(", ");
            }
            first = false;
            String side = agg.column() == null
                    ? null
                    : sideAlias(agg.column().qualifiedTable(), sourceTable, targetTable);
            String outAlias = allocateUniqueAlias(defaultAggregateAlias(agg), outerColumns);
            select.append(aggregateExpression(agg, side)).append(" AS \"").append(outAlias).append("\"");
        }
        if (first) {
            throw new IllegalStateException("Grouped select must project at least one column");
        }
    }

    static String groupByClause(
            RecordMatchRequest req, QualifiedTable sourceTable, QualifiedTable targetTable) {
        if (req.groupByColumns().isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (DisplayColumn g : req.groupByColumns()) {
            String side = sideAlias(g.qualifiedTable(), sourceTable, targetTable);
            joiner.add(side + "." + g.column());
        }
        return joiner.toString();
    }

    static String sideAlias(DisplayColumn col, QualifiedTable sourceTable, QualifiedTable targetTable) {
        return sideAlias(col.qualifiedTable(), sourceTable, targetTable);
    }

    static String sideAlias(QualifiedTable table, QualifiedTable sourceTable, QualifiedTable targetTable) {
        if (table.equals(sourceTable)) {
            return "src";
        }
        if (table.equals(targetTable)) {
            return "tgt";
        }
        throw new IllegalArgumentException("Column table " + table.qualifiedName() + " is not source or target");
    }

    private static String sidePrefix(QualifiedTable table, QualifiedTable sourceTable, QualifiedTable targetTable) {
        return sideAlias(table, sourceTable, targetTable);
    }

    private static String aggregateExpression(AggregateSpec agg, String sideAlias) {
        return switch (agg.function()) {
            case COUNT -> {
                if (agg.countStar()) {
                    yield "count(*)";
                }
                String ref = sideAlias + "." + agg.column().column();
                yield agg.distinct() ? "count(DISTINCT " + ref + ")" : "count(" + ref + ")";
            }
            case SUM -> "sum(" + sideAlias + "." + agg.column().column() + ")";
            case AVG -> "avg(" + sideAlias + "." + agg.column().column() + ")";
            case MIN -> "min(" + sideAlias + "." + agg.column().column() + ")";
            case MAX -> "max(" + sideAlias + "." + agg.column().column() + ")";
        };
    }

    private static String groupKeyAlias(DisplayColumn g, String side) {
        return side + "_" + g.column();
    }

    private static String defaultAggregateAlias(AggregateSpec agg) {
        if (agg.alias() != null && !agg.alias().isBlank()) {
            return agg.alias().trim();
        }
        if (agg.countStar()) {
            return agg.distinct() ? "count_distinct_all" : "count_all";
        }
        String base = agg.function().name().toLowerCase() + "_" + agg.column().column();
        return agg.distinct() ? "count_distinct_" + agg.column().column() : base;
    }

    private static String allocateUniqueAlias(String baseAlias, Set<String> outerColumns) {
        String candidate = baseAlias;
        int suffix = 2;
        while (outerColumns.contains(candidate)) {
            candidate = baseAlias + "_" + suffix++;
        }
        outerColumns.add(candidate);
        return candidate;
    }

    /** Single-table grouped extract: target table equals source. */
    static void validateSingleSourceGrouped(RecordMatchRequest req, QualifiedTable table,
                                            LakehouseRegistryService registry, int maxKeys, int maxAggregates) {
        validateGroupedRequest(req, maxKeys, maxAggregates);
        validateColumnsOnSides(req, table, table, registry);
    }
}
