package gov.rajasthan.smart.srse.scope;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Specification tests for Part 3 semantics — §7.2 will implement against these rules. */
class ScopeAssignmentSemanticsTest {

    private static final long GEO = 1L;
    private static final long DEPT = 2L;

    @Test
    void andAcrossDimensionsOrWithinOne() {
        Map<Long, List<String>> assignments = Map.of(
                GEO, List.of("/G/JAIPUR/", "/G/ALWAR/"),
                DEPT, List.of("/D/HEALTH/"));
        assertTrue(ScopeAccessEvaluator.canAccess(assignments, Map.of(
                GEO, "/G/JAIPUR/SANGANER/",
                DEPT, "/D/HEALTH/")));
        assertTrue(ScopeAccessEvaluator.canAccess(assignments, Map.of(
                GEO, "/G/ALWAR/TOWN/",
                DEPT, "/D/HEALTH/")));
        assertFalse(ScopeAccessEvaluator.canAccess(assignments, Map.of(
                GEO, "/G/JAIPUR/SANGANER/",
                DEPT, "/D/EDUCATION/")));
    }

    @Test
    void aSiblingWhoseNameSharesAPrefixIsNotCovered() {
        // The materialised-path trap: a bare startsWith would grant JAIPURX to
        // someone assigned JAIPUR. Guarded by normalising the assignment side.
        assertFalse(ScopeAccessEvaluator.assignmentCoversDataPath("/G/JAIPUR/", "/G/JAIPURX/"));
        assertFalse(ScopeAccessEvaluator.assignmentCoversDataPath("/G/JAIPUR", "/G/JAIPURX/"));
    }

    @Test
    void aUserAlwaysCoversTheirOwnNodeHoweverThePathIsWritten() {
        // Fails closed rather than open, but still wrong: it reads as a district
        // officer being refused their own district.
        assertTrue(ScopeAccessEvaluator.assignmentCoversDataPath("/G/JAIPUR/", "/G/JAIPUR"));
        assertTrue(ScopeAccessEvaluator.assignmentCoversDataPath("/G/JAIPUR", "/G/JAIPUR/"));
        assertTrue(ScopeAccessEvaluator.assignmentCoversDataPath("/G/JAIPUR", "/G/JAIPUR"));
        assertTrue(ScopeAccessEvaluator.assignmentCoversDataPath("/G/JAIPUR", "/G/JAIPUR/SANGANER"));
    }

    @Test
    void assignmentCoversDescendantsViaPathPrefix() {
        assertTrue(ScopeAccessEvaluator.assignmentCoversDataPath("/G/JAIPUR/", "/G/JAIPUR/SANGANER/"));
        assertFalse(ScopeAccessEvaluator.assignmentCoversDataPath("/G/JAIPUR/", "/G/ALWAR/TOWN/"));
    }

    @Test
    void noAssignmentInDimensionMeansNoAccess() {
        Map<Long, List<String>> assignments = Map.of(GEO, List.of("/G/JAIPUR/"));
        assertFalse(ScopeAccessEvaluator.dimensionParticipates(assignments, DEPT));
        assertFalse(ScopeAccessEvaluator.canAccess(assignments, Map.of(
                GEO, "/G/JAIPUR/",
                DEPT, "/D/HEALTH/")));
    }

    @Test
    void rootNodeAssignmentGrantsWholeDimension() {
        Map<Long, List<String>> assignments = Map.of(GEO, List.of("/G/"));
        assertTrue(ScopeAccessEvaluator.canAccess(assignments, Map.of(
                GEO, "/G/JAIPUR/SANGANER/")));
    }
}
