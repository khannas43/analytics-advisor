package gov.rajasthan.smart.srse.analysis;

/**
 * Read-only Analysis guardrails for the Admin page and officer UI pickers.
 */
public record AnalysisLimitsResponse(
        int maxTargetSets,
        int multiMatchBudgetSeconds,
        int maxGroupColumns,
        /** Same cap as {@code maxGroupColumns} — grouping keys in a grouped match (§5.5). */
        int maxGroupingColumns,
        /** Same cap as {@code maxGroupColumns} — aggregates per grouped match (§5.5). */
        int maxAggregates,
        int maxAnyOfGroupsPerSide,
        int maxProbedPairs,
        int blockingPrefixLen,
        long maxEstimatedRows) {
}
