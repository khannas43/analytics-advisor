package gov.rajasthan.smart.srse.lakehouse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * One table's FROM contribution for Analysis SQL (§7.2.3 Part 0).
 * Filter is a derived table, never ON/WHERE on the outer join.
 */
public record ScopeFilteredFrom(String qualifiedName, String whereSql, List<Object> bindValues) {

    public static final String EMPTY_PREDICATE = "1 = 0";

    public boolean appliesFilter() {
        return whereSql != null;
    }

    public boolean isEmptyResult() {
        return EMPTY_PREDICATE.equals(whereSql);
    }

    public static ScopeFilteredFrom unfiltered(String qualifiedName) {
        return new ScopeFilteredFrom(qualifiedName, null, List.of());
    }

    public static ScopeFilteredFrom emptyResult(String qualifiedName) {
        return new ScopeFilteredFrom(qualifiedName, EMPTY_PREDICATE, List.of());
    }

    public static ScopeFilteredFrom filtered(String qualifiedName, String whereSql, List<Object> bindValues) {
        return new ScopeFilteredFrom(qualifiedName, whereSql, List.copyOf(bindValues));
    }

    /** Merges officer rule predicates into the scope derived-table WHERE (§4.5 / AA-13). */
    public ScopeFilteredFrom withRulePredicate(String ruleSql, List<Object> ruleParams) {
        if (ruleSql == null || ruleSql.isBlank()) {
            return this;
        }
        if (isEmptyResult()) {
            return this;
        }
        List<Object> mergedParams = new java.util.ArrayList<>();
        if (appliesFilter()) {
            mergedParams.addAll(bindValues());
        }
        mergedParams.addAll(ruleParams);
        String mergedWhere = appliesFilter()
                ? whereSql + " AND (" + ruleSql + ")"
                : ruleSql;
        return filtered(qualifiedName, mergedWhere, mergedParams);
    }

    /** Bare {@code catalog.schema.table alias} when unfiltered — byte-identical to pre-7.2.3. */
    public String fromFragment(String alias) {
        if (!appliesFilter()) {
            return qualifiedName + " " + alias;
        }
        return "(SELECT * FROM " + qualifiedName + " t WHERE " + whereSql + ") " + alias;
    }

    /** Table expression inside a nested subquery (ANY_OF UNNEST wrapper). */
    public String innerTableExpression() {
        if (!appliesFilter()) {
            return qualifiedName;
        }
        return "(SELECT * FROM " + qualifiedName + " t WHERE " + whereSql + ")";
    }

    public static String inClause(String columnName, int valueCount) {
        if (valueCount <= 0) {
            throw new IllegalArgumentException("IN list must not be empty — use empty-result predicate instead");
        }
        String placeholders = java.util.stream.IntStream.range(0, valueCount)
                .mapToObj(i -> "?")
                .collect(Collectors.joining(", "));
        return columnName + " IN (" + placeholders + ")";
    }
}
