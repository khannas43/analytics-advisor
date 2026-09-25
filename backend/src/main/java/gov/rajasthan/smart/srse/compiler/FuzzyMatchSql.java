package gov.rajasthan.smart.srse.compiler;

/**
 * Normalized Levenshtein similarity and blocking keys, shared by {@link RuleCompiler}
 * (column vs. a bound literal) and {@link gov.rajasthan.smart.srse.analysis.RecordMatchService}
 * (column vs. column, cross-table). Both operands are raw SQL text supplied
 * by the caller — a column reference or a {@code ?} placeholder — never
 * officer input concatenated directly; callers own parameter binding.
 */
public final class FuzzyMatchSql {

    private FuzzyMatchSql() {
    }

    /** Single normalisation pipeline for blocking keys and similarity (§5.3). */
    public static String fuzzyNormalise(String columnSql, FuzzyOptions options) {
        FuzzyOptions resolved = FuzzyOptions.resolve(options);
        String expr = columnSql;
        if (!resolved.caseSensitive()) {
            expr = "lower(" + expr + ")";
        }
        if (resolved.ignoreSpaces()) {
            expr = "replace(" + expr + ", ' ', '')";
        }
        return expr;
    }

    public static String blockingKeyExpr(String columnSql, int prefixLen, FuzzyOptions options) {
        return "substr(" + fuzzyNormalise(columnSql, options) + ", 1, " + prefixLen + ")";
    }

    /**
     * How many times {@code rightSql} appears in {@link #similarityExpr} — once inside
     * {@code levenshtein_distance} and once inside {@code length} for the denominator.
     *
     * <p>It matters only when the right side is a bound {@code ?} rather than a column
     * reference, which is why the two-column match path never noticed: a caller that
     * binds its value once then supplies one parameter for two placeholders, and the
     * statement is rejected outright. Bind {@link #rightSideParams} instead of counting
     * by hand.
     */
    public static final int RIGHT_SIDE_PLACEHOLDERS = 2;

    /** The parameters a caller must bind when {@code rightSql} is {@code "?"}. */
    public static java.util.List<Object> rightSideParams(Object value) {
        return java.util.Collections.nCopies(RIGHT_SIDE_PLACEHOLDERS, value);
    }

    /** Yields a 0..1 similarity fraction; caller compares it against a bound threshold. */
    public static String similarityExpr(String leftSql, String rightSql) {
        return similarityExpr(leftSql, rightSql, FuzzyOptions.DEFAULTS);
    }

    public static String similarityExpr(String leftSql, String rightSql, FuzzyOptions options) {
        String left = fuzzyNormalise(leftSql, options);
        String right = fuzzyNormalise(rightSql, options);
        return "(1.0 - CAST(levenshtein_distance(" + left + ", " + right + ") AS DOUBLE) "
                + "/ GREATEST(length(" + leftSql + "), length(" + rightSql + "), 1))";
    }
}
