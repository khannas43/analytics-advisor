package gov.rajasthan.smart.srse.lakehouse;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableScopePolicyTest {

    private static final long GEO = 1L;
    private static final long DEPT = 2L;

    @Test
    void superAdminBypassesVisibility() {
        var table = new TableScopePolicy.TableScopeMetadata(false, List.of(), Set.of());
        assertTrue(TableScopePolicy.isTableVisible(TableScopePolicy.OfficerScopeView.bypass(), table));
    }

    @Test
    void sharedReferenceVisibleWithoutAssignments() {
        var officer = new TableScopePolicy.OfficerScopeView(false, Map.of());
        var table = new TableScopePolicy.TableScopeMetadata(true, List.of(), Set.of());
        assertTrue(TableScopePolicy.isTableVisible(officer, table));
    }

    @Test
    void noAssignmentsSeesNoScopedData() {
        var officer = new TableScopePolicy.OfficerScopeView(false, Map.of());
        var table = new TableScopePolicy.TableScopeMetadata(false, List.of(binding(GEO, 2, "district_code")), Set.of());
        assertFalse(TableScopePolicy.isTableVisible(officer, table));
    }

    @Test
    void boundInOneDimensionButNotSecondIsInvisible() {
        var officer = new TableScopePolicy.OfficerScopeView(
                false,
                Map.of(
                        GEO, List.of(new TableScopePolicy.AssignmentNode(2)),
                        DEPT, List.of(new TableScopePolicy.AssignmentNode(1))));
        var table = new TableScopePolicy.TableScopeMetadata(
                false, List.of(binding(GEO, 2, "district_code")), Set.of());
        assertFalse(TableScopePolicy.isTableVisible(officer, table));
    }

    @Test
    void exemptFromSecondDimensionIsVisible() {
        var officer = new TableScopePolicy.OfficerScopeView(
                false,
                Map.of(
                        GEO, List.of(new TableScopePolicy.AssignmentNode(2)),
                        DEPT, List.of(new TableScopePolicy.AssignmentNode(1))));
        var table = new TableScopePolicy.TableScopeMetadata(
                false, List.of(binding(GEO, 2, "district_code")), Set.of(DEPT));
        assertTrue(TableScopePolicy.isTableVisible(officer, table));
    }

    @Test
    void tableWithNoBindingsIsInvisible() {
        var officer = new TableScopePolicy.OfficerScopeView(
                false, Map.of(GEO, List.of(new TableScopePolicy.AssignmentNode(2))));
        var table = new TableScopePolicy.TableScopeMetadata(false, List.of(), Set.of());
        assertFalse(TableScopePolicy.isTableVisible(officer, table));
    }

    @Test
    void coarsestBindingCoversTwoDepthsInOneDimension() {
        var officer = new TableScopePolicy.OfficerScopeView(
                false,
                Map.of(
                        GEO,
                        List.of(
                                new TableScopePolicy.AssignmentNode(2),
                                new TableScopePolicy.AssignmentNode(3))));
        var table = new TableScopePolicy.TableScopeMetadata(
                false,
                List.of(
                        binding(GEO, 1, "state_code"),
                        binding(GEO, 2, "district_code"),
                        binding(GEO, 3, "village_code")),
                Set.of());
        Optional<TableScopePolicy.LevelBinding> chosen =
                TableScopePolicy.chosenBindingForDimension(officer, table, GEO);
        assertTrue(chosen.isPresent());
        // Depth 3, not 2. The officer holds something at depth 3, and a
        // district_code filter cannot express it: every row of that node's
        // parent district shares the district code, so filtering there would
        // return the whole district instead of the one village. The coarsest
        // binding is preferred only among those specific enough to be exact.
        assertEquals(3, chosen.get().levelDepth());
        assertEquals("village_code", chosen.get().columnName());
    }

    @Test
    void theCoarsestUsableBindingWinsWhenSeveralAreExact() {
        // A17's optimisation: a district officer filtered on district_code binds
        // one value, where village_code would bind every village in the district.
        var officer = new TableScopePolicy.OfficerScopeView(
                false, Map.of(GEO, List.of(new TableScopePolicy.AssignmentNode(1))));
        var table = new TableScopePolicy.TableScopeMetadata(
                false,
                List.of(
                        binding(GEO, 1, "district_code"),
                        binding(GEO, 2, "taluka_code"),
                        binding(GEO, 3, "village_code")),
                Set.of());
        Optional<TableScopePolicy.LevelBinding> chosen =
                TableScopePolicy.chosenBindingForDimension(officer, table, GEO);
        assertTrue(chosen.isPresent());
        assertEquals(1, chosen.get().levelDepth());
        assertEquals("district_code", chosen.get().columnName());
    }

    @Test
    void aTableBoundOnlyCoarserThanTheOfficersScopeIsUnusableAndInvisible() {
        // A taluka officer against a table carrying only district_code. The rows
        // do not say which taluka they belong to, so no filter can be exact —
        // and showing the table would mean showing the whole district.
        var talukaOfficer = new TableScopePolicy.OfficerScopeView(
                false, Map.of(GEO, List.of(new TableScopePolicy.AssignmentNode(2))));
        var table = new TableScopePolicy.TableScopeMetadata(
                false, List.of(binding(GEO, 1, "district_code")), Set.of());

        assertTrue(TableScopePolicy.chosenBindingForDimension(talukaOfficer, table, GEO).isEmpty(),
                "a binding too coarse to express the officer's scope is not a fallback");
        assertFalse(TableScopePolicy.isTableVisible(talukaOfficer, table),
                "and the table must be hidden rather than shown and filtered too loosely");
    }

    private static TableScopePolicy.LevelBinding binding(long dim, int depth, String column) {
        return new TableScopePolicy.LevelBinding(dim * 10 + depth, dim, depth, column);
    }
}
