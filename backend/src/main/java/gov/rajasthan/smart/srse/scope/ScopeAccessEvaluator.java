package gov.rajasthan.smart.srse.scope;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure scope-access rules (PRODUCT_PLAN §7.2 / decision A16). §7.2 will emit SQL
 * from these semantics; nothing here touches queries.
 */
public final class ScopeAccessEvaluator {

    private ScopeAccessEvaluator() {
    }

    /**
     * Returns whether {@code dataPathsByDimension} is reachable given the user's
     * assigned node paths grouped by dimension.
     *
     * @param assignmentPathsByDimension assigned scope node paths per dimension id
     * @param dataPathsByDimension       data row's scope path per dimension id (may omit dimensions)
     */
    public static boolean canAccess(
            Map<Long, List<String>> assignmentPathsByDimension,
            Map<Long, String> dataPathsByDimension) {
        if (dataPathsByDimension == null || dataPathsByDimension.isEmpty()) {
            return false;
        }
        for (Map.Entry<Long, String> dataEntry : dataPathsByDimension.entrySet()) {
            Long dimensionId = dataEntry.getKey();
            List<String> assigned = assignmentPathsByDimension == null
                    ? null
                    : assignmentPathsByDimension.get(dimensionId);
            if (assigned == null || assigned.isEmpty()) {
                return false;
            }
            if (!matchesAnyAssignedPath(assigned, dataEntry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * AND across dimensions, OR within one: every dimension present in assignments
     * must match; multiple paths in the same dimension are disjunctive.
     */
    public static boolean matchesAcrossDimensions(
            Map<Long, List<String>> assignmentPathsByDimension,
            Map<Long, String> dataPathsByDimension) {
        return canAccess(assignmentPathsByDimension, dataPathsByDimension);
    }

    /**
     * Assignment covers the node and all descendants via materialised path prefix (A3).
     *
     * <p><b>Both sides are normalised, and both matter.</b> Normalising the
     * assignment stops {@code /G/JAIPUR} matching the unrelated
     * {@code /G/JAIPURX/} — the classic materialised-path bug, where a prefix
     * test silently grants a sibling whose name merely starts the same way.
     * Normalising the DATA path stops the opposite error: without it
     * {@code "/G/JAIPUR".startsWith("/G/JAIPUR/")} is false, so a user assigned
     * exactly the node being accessed is refused their own node. That one fails
     * closed, which is the safe direction but still wrong, and it would present
     * as "the district officer cannot see their own district".
     */
    public static boolean assignmentCoversDataPath(String assignmentPath, String dataPath) {
        return normalizePath(dataPath).startsWith(normalizePath(assignmentPath));
    }

    /**
     * No assignment rows in a dimension means no access in that dimension — not "all".
     * Whole-dimension access is granted only by assigning that dimension's root node.
     */
    public static boolean dimensionParticipates(Map<Long, List<String>> assignmentPathsByDimension, Long dimensionId) {
        List<String> paths = assignmentPathsByDimension.get(dimensionId);
        return paths != null && !paths.isEmpty();
    }

    public static Map<Long, List<String>> groupAssignmentPathsByDimension(
            Collection<ScopedAssignment> assignments) {
        Map<Long, List<String>> grouped = new HashMap<>();
        for (ScopedAssignment assignment : assignments) {
            grouped.computeIfAbsent(assignment.dimensionId(), (k) -> new java.util.ArrayList<>())
                    .add(assignment.nodePath());
        }
        return grouped;
    }

    private static boolean matchesAnyAssignedPath(List<String> assignedPaths, String dataPath) {
        for (String assigned : assignedPaths) {
            if (assignmentCoversDataPath(assigned, dataPath)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.endsWith("/") ? path : path + "/";
    }

    public record ScopedAssignment(long dimensionId, String nodePath) {
    }
}
