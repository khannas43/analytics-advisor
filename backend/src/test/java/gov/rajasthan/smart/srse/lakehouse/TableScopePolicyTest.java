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
        assertEquals(2, chosen.get().levelDepth());
        assertEquals("district_code", chosen.get().columnName());
    }

    private static TableScopePolicy.LevelBinding binding(long dim, int depth, String column) {
        return new TableScopePolicy.LevelBinding(dim * 10 + depth, dim, depth, column);
    }
}
