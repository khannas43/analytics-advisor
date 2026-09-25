package gov.rajasthan.smart.srse.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AA-16 §5.3 — shared fuzzy normalisation must not drift between blocking and similarity. */
class FuzzyMatchSqlTest {

    private static final String COL = "src.father_name";

    @Test
    void defaultOptionsMatchLegacyLowerBlockingAndSimilarity() {
        String blocking = FuzzyMatchSql.blockingKeyExpr(COL, 3, null);
        String similarity = FuzzyMatchSql.similarityExpr(COL, "tgt.father_name", null);
        assertEquals("substr(lower(" + COL + "), 1, 3)", blocking);
        assertTrue(similarity.contains("levenshtein_distance(lower(" + COL + "), lower(tgt.father_name))"));
    }

    @Test
    void ignoreSpacesAppliesToBlockingAndSimilarityTogether() {
        FuzzyOptions opts = new FuzzyOptions(false, true);
        String blocking = FuzzyMatchSql.blockingKeyExpr(COL, 3, opts);
        String similarity = FuzzyMatchSql.similarityExpr(COL, "tgt.father_name", opts);
        assertTrue(blocking.contains("replace(lower(" + COL + "), ' ', '')"));
        assertTrue(similarity.contains("replace(lower(" + COL + "), ' ', '')"));
        assertTrue(similarity.contains("replace(lower(tgt.father_name), ' ', '')"));
    }

    @Test
    void caseSensitiveDropsLowerFromBothPaths() {
        FuzzyOptions opts = new FuzzyOptions(true, false);
        String blocking = FuzzyMatchSql.blockingKeyExpr(COL, 3, opts);
        String similarity = FuzzyMatchSql.similarityExpr(COL, "tgt.father_name", opts);
        assertEquals("substr(" + COL + ", 1, 3)", blocking);
        assertFalse(similarity.contains("lower(" + COL + ")"));
        assertTrue(similarity.contains("levenshtein_distance(" + COL + ", tgt.father_name)"));
    }
}
