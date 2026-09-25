package gov.rajasthan.smart.srse.lakehouse;

import gov.rajasthan.smart.srse.scope.ScopeDimension;
import gov.rajasthan.smart.srse.scope.ScopeLevel;
import gov.rajasthan.smart.srse.scope.ScopeNode;
import gov.rajasthan.smart.srse.scope.ScopeNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisScopeFromServiceTest {

    private static final long GEO = 10L;
    private static final long DEPT = 20L;
    private static final String QUALIFIED = "iceberg.srse.beneficiary";

    @Mock
    private OfficerRegistryScopeService officerScope;
    @Mock
    private RegisteredTableRepository registeredTableRepository;
    @Mock
    private RegisteredTableScopeCatalog scopeCatalog;
    @Mock
    private ScopeNodeRepository scopeNodeRepository;

    private AnalysisScopeFromService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisScopeFromService(
                officerScope,
                registeredTableRepository,
                scopeCatalog,
                scopeNodeRepository,
                new ScopeFilterProperties(1000));
    }

    @Test
    void bypassOfficerLeavesSqlUnfiltered() {
        when(officerScope.currentOfficerScope()).thenReturn(TableScopePolicy.OfficerScopeView.bypass());
        ScopeFilteredFrom plan = service.planFrom(new QualifiedTable("iceberg", "srse", "beneficiary"));
        assertFalse(plan.appliesFilter());
        assertEquals("iceberg.srse.beneficiary src", plan.fromFragment("src").trim());
    }

    @Test
    void sharedReferenceUnfiltered() {
        when(officerScope.currentOfficerScope()).thenReturn(officerWithGeo());
        stubRegistration(false);
        when(scopeCatalog.metadataFor(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TableScopePolicy.TableScopeMetadata(true, List.of(), Set.of()));

        ScopeFilteredFrom plan = service.planFrom(new QualifiedTable("iceberg", "srse", "beneficiary"));
        assertFalse(plan.appliesFilter());
    }

    @Test
    void twoDimensionsAndedWithBoundParams() {
        when(officerScope.currentOfficerScope()).thenReturn(officerWithGeoAndDept());
        stubRegistration(false);
        when(scopeCatalog.metadataFor(org.mockito.ArgumentMatchers.any())).thenReturn(metadata(
                binding(GEO, 2, "district_code"),
                binding(DEPT, 1, "dept_code")));
        when(scopeNodeRepository.findByDimensionWithFetchOrderByPath(GEO)).thenReturn(List.of(
                node(GEO, 2, "/GEO/RJ/Jaipur", "RJ-JPR")));
        when(scopeNodeRepository.findByDimensionWithFetchOrderByPath(DEPT)).thenReturn(List.of(
                node(DEPT, 1, "/DEPT/EDU", "EDU")));

        ScopeFilteredFrom plan = service.planFrom(new QualifiedTable("iceberg", "srse", "beneficiary"));
        assertTrue(plan.appliesFilter());
        assertTrue(plan.whereSql().contains("district_code IN (?)"));
        assertTrue(plan.whereSql().contains("dept_code IN (?)"));
        assertTrue(plan.whereSql().contains(" AND "));
        assertEquals(List.of("RJ-JPR", "EDU"), plan.bindValues());
    }

    @Test
    void exemptDimensionSkipped() {
        when(officerScope.currentOfficerScope()).thenReturn(officerWithGeoAndDept());
        stubRegistration(false);
        when(scopeCatalog.metadataFor(org.mockito.ArgumentMatchers.any())).thenReturn(
                new TableScopePolicy.TableScopeMetadata(
                        false,
                        List.of(binding(GEO, 2, "district_code")),
                        Set.of(DEPT)));
        when(scopeNodeRepository.findByDimensionWithFetchOrderByPath(GEO)).thenReturn(List.of(
                node(GEO, 2, "/GEO/RJ/Jaipur", "RJ-JPR")));

        ScopeFilteredFrom plan = service.planFrom(new QualifiedTable("iceberg", "srse", "beneficiary"));
        assertTrue(plan.whereSql().contains("district_code IN (?)"));
        assertFalse(plan.whereSql().contains("dept_code"));
    }

    @Test
    void emptyCodeSetEmitsFalsePredicate() {
        when(officerScope.currentOfficerScope()).thenReturn(officerWithGeo());
        stubRegistration(false);
        when(scopeCatalog.metadataFor(org.mockito.ArgumentMatchers.any())).thenReturn(metadata(
                binding(GEO, 2, "district_code")));
        when(scopeNodeRepository.findByDimensionWithFetchOrderByPath(GEO)).thenReturn(List.of(
                node(GEO, 2, "/GEO/RJ/Udaipur", "RJ-UDR")));

        ScopeFilteredFrom plan = service.planFrom(new QualifiedTable("iceberg", "srse", "beneficiary"));
        assertEquals(ScopeFilteredFrom.EMPTY_PREDICATE, plan.whereSql());
        assertTrue(plan.bindValues().isEmpty());
    }

    @Test
    void overCapRefusesWithActionableMessage() {
        service = new AnalysisScopeFromService(
                officerScope, registeredTableRepository, scopeCatalog, scopeNodeRepository,
                new ScopeFilterProperties(1));
        when(officerScope.currentOfficerScope()).thenReturn(officerWithGeo());
        stubRegistration(false);
        when(scopeCatalog.metadataFor(org.mockito.ArgumentMatchers.any())).thenReturn(metadata(
                binding(GEO, 2, "district_code")));
        when(scopeNodeRepository.findByDimensionWithFetchOrderByPath(GEO)).thenReturn(List.of(
                node(GEO, 2, "/GEO/RJ/Jaipur", "RJ-JPR"),
                node(GEO, 2, "/GEO/RJ/Jaipur/Sanganer", "RJ-SGN")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.planFrom(new QualifiedTable("iceberg", "srse", "beneficiary")));
        assertTrue(ex.getMessage().contains(QUALIFIED));
        assertTrue(ex.getMessage().contains("dimension " + GEO));
    }

    private void stubRegistration(boolean shared) {
        RegisteredTable reg = new RegisteredTable(1L, "iceberg", "srse", "beneficiary", "GOLD", shared);
        when(registeredTableRepository.findByCatalogNameAndSchemaNameAndTableName("iceberg", "srse", "beneficiary"))
                .thenReturn(Optional.of(reg));
    }

    private static TableScopePolicy.OfficerScopeView officerWithGeo() {
        return new TableScopePolicy.OfficerScopeView(false, Map.of(
                GEO, List.of(new TableScopePolicy.AssignmentNode(2, "/GEO/RJ/Jaipur", "RJ-JPR"))));
    }

    private static TableScopePolicy.OfficerScopeView officerWithGeoAndDept() {
        return new TableScopePolicy.OfficerScopeView(false, Map.of(
                GEO, List.of(new TableScopePolicy.AssignmentNode(2, "/GEO/RJ/Jaipur", "RJ-JPR")),
                DEPT, List.of(new TableScopePolicy.AssignmentNode(1, "/DEPT/EDU", "EDU"))));
    }

    private static TableScopePolicy.TableScopeMetadata metadata(TableScopePolicy.LevelBinding... bindings) {
        return new TableScopePolicy.TableScopeMetadata(false, List.of(bindings), Set.of());
    }

    private static TableScopePolicy.LevelBinding binding(long dim, int depth, String column) {
        return new TableScopePolicy.LevelBinding(dim, dim, depth, column);
    }

    private static ScopeNode node(long dimId, int depth, String path, String code) {
        ScopeDimension dimension = new ScopeDimension("dim-" + dimId, "Dim " + dimId, 1);
        ScopeLevel level = new ScopeLevel(dimension, depth, "level-" + depth);
        return new ScopeNode(dimension, level, null, code, code, path);
    }
}
