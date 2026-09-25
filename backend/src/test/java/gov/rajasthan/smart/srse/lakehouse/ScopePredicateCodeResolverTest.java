package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.scope.ScopeDimension;
import gov.rajasthan.smart.srse.scope.ScopeLevel;
import gov.rajasthan.smart.srse.scope.ScopeNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopePredicateCodeResolverTest {

    @Test
    void collectsCodesAtBindingDepthUnderAssignmentPath() {
        ScopeNode jaipur = node(2, "/GEO/RJ/Jaipur", "RJ-JPR");
        ScopeNode sanganer = node(2, "/GEO/RJ/Jaipur/Sanganer", "RJ-SGN");
        ScopeNode udaipur = node(2, "/GEO/RJ/Udaipur", "RJ-UDR");

        List<String> codes = ScopePredicateCodeResolver.codesForBinding(
                List.of(new TableScopePolicy.AssignmentNode(2, "/GEO/RJ/Jaipur", "RJ-JPR")),
                2,
                List.of(jaipur, sanganer, udaipur));

        assertEquals(2, codes.size());
        assertTrue(codes.contains("RJ-JPR"));
        assertTrue(codes.contains("RJ-SGN"));
    }

    @Test
    void emptyWhenNoNodeMatchesPath() {
        ScopeNode udaipur = node(2, "/GEO/RJ/Udaipur", "RJ-UDR");
        List<String> codes = ScopePredicateCodeResolver.codesForBinding(
                List.of(new TableScopePolicy.AssignmentNode(2, "/GEO/RJ/Jaipur", "RJ-JPR")),
                2,
                List.of(udaipur));
        assertTrue(codes.isEmpty());
    }

    private static ScopeNode node(int depth, String path, String code) {
        ScopeDimension dimension = new ScopeDimension("geo", "Geography", 1);
        ScopeLevel level = new ScopeLevel(dimension, depth, "level-" + depth);
        return new ScopeNode(dimension, level, null, code, code, path);
    }
}
