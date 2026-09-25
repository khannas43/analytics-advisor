package gov.rajasthan.smart.srse.compiler;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every {@code ?} the fuzzy SQL emits must have a parameter bound to it.
 *
 * <p>This is the third time this project has shipped a placeholder/parameter
 * mismatch — first a duplicated {@code ?} in the mismatch-only filter, then the
 * scoped fan-out query embedding its derived table twice, and now
 * {@code similarityExpr} using its right side twice while callers bound it
 * once. Typed-text fuzzy failed outright as a result: a search for a name
 * present 21,000 times returned a SQLException rather than rows.
 *
 * <p>Counting by eye keeps failing, so this counts.
 */
class FuzzyPlaceholderParityTest {

    @Test
    void theSimilarityExpressionRepeatsItsRightSideExactlyAsAdvertised() {
        String sql = FuzzyMatchSql.similarityExpr("t.col", "?", FuzzyOptions.DEFAULTS);
        assertEquals(FuzzyMatchSql.RIGHT_SIDE_PLACEHOLDERS, countPlaceholders(sql),
                "RIGHT_SIDE_PLACEHOLDERS must match what similarityExpr actually emits — "
                        + "callers bind against it: " + sql);
    }

    @Test
    void rightSideParamsSuppliesOnePerPlaceholder() {
        List<Object> params = FuzzyMatchSql.rightSideParams("Rajesh");
        assertEquals(countPlaceholders(FuzzyMatchSql.similarityExpr("t.col", "?", FuzzyOptions.DEFAULTS)),
                params.size());
        params.forEach(p -> assertEquals("Rajesh", p));
    }

    @Test
    void theBlockingKeyBindsItsRightSideExactlyOnce() {
        // Asymmetric with the similarity, which is the trap: the blocking key wraps its
        // argument once, so a caller binding "one per expression" is wrong in one
        // direction and "two per expression" is wrong in the other.
        assertEquals(1, countPlaceholders(FuzzyMatchSql.blockingKeyExpr("?", 3, FuzzyOptions.DEFAULTS)));
    }

    private static int countPlaceholders(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }
}
