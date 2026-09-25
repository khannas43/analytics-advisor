package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.scope.ScopeNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves bound IN-list codes for one dimension (§7.2.3) — pure, unit-tested. */
public final class ScopePredicateCodeResolver {

    private ScopePredicateCodeResolver() {
    }

    /**
     * At binding depth {@code bindingDepth}, collect node codes whose materialised
     * paths prefix-match any officer assignment path in this dimension.
     */
    public static List<String> codesForBinding(
            List<TableScopePolicy.AssignmentNode> assignments,
            int bindingDepth,
            List<ScopeNode> nodesInDimension) {
        Set<String> codes = new LinkedHashSet<>();
        for (TableScopePolicy.AssignmentNode assignment : assignments) {
            for (ScopeNode node : nodesInDimension) {
                if (node.getLevel().getDepth() != bindingDepth) {
                    continue;
                }
                if (pathsPrefixMatch(assignment.path(), node.getPath())) {
                    codes.add(node.getCode());
                }
            }
        }
        return List.copyOf(codes);
    }

    static boolean pathsPrefixMatch(String assignmentPath, String nodePath) {
        String a = normalize(assignmentPath);
        String n = normalize(nodePath);
        return n.startsWith(a);
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.endsWith("/") ? path : path + "/";
    }
}
