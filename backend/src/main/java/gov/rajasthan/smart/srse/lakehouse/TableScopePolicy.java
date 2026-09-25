package gov.rajasthan.smart.srse.lakehouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure scope-table visibility (D5) and coarsest binding selection (D2) for §7.2.
 * No request context — unit-tested directly.
 */
public final class TableScopePolicy {

    private TableScopePolicy() {
    }

    public record AssignmentNode(int depth) {
    }

    public record LevelBinding(long levelId, long dimensionId, int levelDepth, String columnName) {
    }

    public record TableScopeMetadata(
            boolean sharedReference,
            List<LevelBinding> bindings,
            Set<Long> exemptDimensionIds) {
    }

    public record OfficerScopeView(
            boolean bypassDataScoping,
            Map<Long, List<AssignmentNode>> assignmentsByDimension) {

        public static OfficerScopeView bypass() {
            return new OfficerScopeView(true, Map.of());
        }

        /**
         * No assignments and no bypass: shared-reference tables only.
         *
         * <p>This is what an identity we cannot resolve must get. The
         * distinction from {@link #bypass()} is the whole of the control —
         * getting it the wrong way round turns an unknown caller into an
         * unrestricted one, which is how a security check fails without
         * anything appearing broken.
         */
        public static OfficerScopeView denyScoped() {
            return new OfficerScopeView(false, Map.of());
        }
    }

    /** D3/D5 — SuperAdmin and shared reference bypass; deny-by-default per assigned dimension. */
    public static boolean isTableVisible(OfficerScopeView officer, TableScopeMetadata table) {
        if (officer.bypassDataScoping()) {
            return true;
        }
        if (table.sharedReference()) {
            return true;
        }
        Map<Long, List<AssignmentNode>> assigned = officer.assignmentsByDimension();
        if (assigned == null || assigned.isEmpty()) {
            return false;
        }
        for (Map.Entry<Long, List<AssignmentNode>> entry : assigned.entrySet()) {
            long dimensionId = entry.getKey();
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            if (table.exemptDimensionIds().contains(dimensionId)) {
                continue;
            }
            // Not "is there a binding" but "is there a binding we can actually
            // filter with". A table bound only coarser than the officer's scope
            // would otherwise be shown and then filtered too loosely, which is
            // the failure this whole section exists to prevent.
            if (chosenBindingForDimension(officer, table, dimensionId).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Coarsest binding in a dimension that is at or above every node the officer holds (D2).
     * Returns empty when no binding applies or dimension not relevant.
     */
    public static Optional<LevelBinding> chosenBindingForDimension(
            OfficerScopeView officer,
            TableScopeMetadata table,
            long dimensionId) {
        if (officer.bypassDataScoping() || table.sharedReference()) {
            return Optional.empty();
        }
        List<AssignmentNode> nodes = officer.assignmentsByDimension().get(dimensionId);
        if (nodes == null || nodes.isEmpty()) {
            return Optional.empty();
        }
        List<LevelBinding> inDimension = table.bindings().stream()
                .filter(b -> b.dimensionId() == dimensionId)
                .toList();
        if (inDimension.isEmpty()) {
            return Optional.empty();
        }
        // A binding can express the officer's scope only if its column is at
        // least as SPECIFIC as their deepest assignment. A taluka officer
        // against a table carrying only district_code cannot be filtered to
        // their taluka — the rows do not say which taluka they belong to — so
        // filtering on district would hand them the whole district. Such a
        // binding is unusable, not a fallback.
        int deepestAssignment = nodes.stream().mapToInt(AssignmentNode::depth).max().orElse(0);
        List<LevelBinding> usable = inDimension.stream()
                .filter(b -> b.levelDepth() >= deepestAssignment)
                .toList();
        // Among usable bindings prefer the COARSEST, which is the smallest
        // depth (A17). A district officer filtered on district_code binds one
        // value; the same officer filtered on village_code binds every village
        // in the district.
        return usable.stream().min(Comparator.comparingInt(LevelBinding::levelDepth));
    }
}
