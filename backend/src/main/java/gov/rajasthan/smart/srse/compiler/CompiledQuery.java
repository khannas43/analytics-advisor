package gov.rajasthan.smart.srse.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Output of the compiler: a parameterised WHERE-clause fragment plus the
 * ordered list of bound parameter values.
 *
 * The predicate is ALWAYS parameterised ('?' placeholders). Values are bound,
 * never inlined — this is the structural SQL-injection defence. Callers pass
 * {@code params} straight to JdbcTemplate in order.
 */
public record CompiledQuery(String predicateSql, List<Object> params, FuzzyScoreProjection fuzzyScore) {

    public record FuzzyScoreProjection(String selectExpr, List<Object> params) {
        public FuzzyScoreProjection {
            params = copyParamList(params);
        }
    }

    public CompiledQuery(String predicateSql, List<Object> params) {
        this(predicateSql, params, null);
    }

    public CompiledQuery {
        params = copyParamList(params);
    }

    /** Bound parameters may be SQL NULL — {@link List#copyOf} rejects null elements. */
    private static List<Object> copyParamList(List<Object> params) {
        if (params == null || params.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(params));
    }
}
