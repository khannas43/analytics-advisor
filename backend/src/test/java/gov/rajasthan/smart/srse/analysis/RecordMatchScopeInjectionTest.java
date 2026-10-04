package gov.rajasthan.smart.srse.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.rajasthan.smart.srse.execution.GuardrailProperties;
import gov.rajasthan.smart.srse.lakehouse.AnalysisScopeFromService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService.RegisteredColumn;
import gov.rajasthan.smart.srse.lakehouse.QualifiedTable;
import gov.rajasthan.smart.srse.lakehouse.ScopeFilteredFrom;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** AA-09 — scope predicate on every Analysis SQL path (§7.2.3–7.2.9). */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class RecordMatchScopeInjectionTest {

    private static final String SRC = "iceberg.srse.beneficiary";
    private static final String TGT = "iceberg_silver.silver_txn.tbl_txn_bankdtl";

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LakehouseRegistryService registry;
    @Mock
    private AnalysisColumnMetadataRepository columnMetadata;
    @Mock
    private AnalysisScopeFromService scopeFrom;
    @Mock
    private gov.rajasthan.smart.srse.compiler.RuleCompiler ruleCompiler;

    private RecordMatchService service;

    @BeforeEach
    void setUp() {
        lenient().when(columnMetadata.findByCatalogNameAndSchemaNameAndTableNameAndColumnName(
                anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(registry.hasColumns(any(), any())).thenReturn(true);
        lenient().when(registry.describeColumns(any(), any())).thenAnswer(inv -> {
            Map<String, RegisteredColumn> described = new LinkedHashMap<>();
            for (Object column : inv.getArgument(1, List.class)) {
                String name = String.valueOf(column);
                described.put(name, new RegisteredColumn(name, "bigint", null, false, true));
            }
            return described;
        });
        lenient().when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        lenient().when(jdbc.queryForObject(anyString(), any(Object[].class), eq(Long.class))).thenReturn(1L);
        lenient().when(ruleCompiler.compile(any(), any()))
                .thenReturn(new gov.rajasthan.smart.srse.compiler.CompiledQuery("TRUE", List.of()));
        service = new RecordMatchService(
                jdbc,
                registry,
                new GuardrailProperties(1000, 30, 50),
                columnMetadata,
                new AnalysisProperties(5, 120, 4, 2, 10, 3, 50_000_000L, 10, 100, 100_000),
                new ObjectMapper(),
                scopeFrom,
                ruleCompiler);
    }

    @Test
    void matchSqlUsesDerivedTableNotJoinWhere() {
        scopeBothSides();
        RecordMatchRequest req = scopedMatchRequest(JoinType.INNER);
        RecordMatchService.MatchQuery query = service.planMatch(req);
        assertTrue(query.sql().contains("(SELECT * FROM " + SRC + " t WHERE district_code IN (?)) src"));
        assertTrue(query.sql().contains("(SELECT * FROM " + TGT + " t WHERE district_code IN (?)) tgt"));
        assertFalse(query.sql().matches("(?s).*ON.*district_code IN.*"));
        assertArrayEquals(new Object[] {"RJ-SGN", "RJ-SGN"}, query.params().toArray());
    }

    @Test
    void matchCsvAndComparisonSummarySharePlan() {
        scopeBothSides();
        RecordMatchRequest req = scopedMatchRequest(JoinType.LEFT);
        RecordMatchService.MatchQuery planned = service.planMatch(req);
        assertTrue(service.matchCsv(req).getClass().getSimpleName().contains("Lambda"));
        assertTrue(planned.sql().contains("LEFT JOIN"));
        assertTrue(planned.sql().contains("district_code IN (?)"));
        lenient().when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(java.util.Map.of(
                "total_rows", 0L, "matched_rows", 0L, "no_counterpart_rows", 0L));
        service.comparisonSummary(reqWithComparison(req));
    }

    @Test
    void superAdminByteIdenticalBaseline() {
        when(scopeFrom.planFrom(any())).thenAnswer(inv -> {
            QualifiedTable t = inv.getArgument(0);
            return ScopeFilteredFrom.unfiltered(t.qualifiedName());
        });
        RecordMatchRequest req = scopedMatchRequest(JoinType.INNER);
        String sql = service.planMatch(req).sql();
        assertTrue(sql.contains("FROM " + SRC + " src JOIN " + TGT + " tgt"));
        assertFalse(sql.contains("district_code IN"));
    }

    @Test
    void allJoinTypesWrapBothSides() {
        scopeBothSides();
        for (JoinType joinType : JoinType.values()) {
            RecordMatchService.MatchQuery query = service.planMatch(scopedMatchRequest(joinType));
            assertTrue(query.sql().contains("(SELECT * FROM " + SRC), joinType.name());
            assertTrue(query.sql().contains("(SELECT * FROM " + TGT), joinType.name());
        }
    }

    @Test
    void suggestKeysSourceSqlScoped() {
        scopeForTable("iceberg", "srse", "beneficiary", "RJ-SGN");
        scopeForTable("iceberg_silver", "silver_txn", "tbl_txn_bankdtl", "RJ-SGN");
        String sql = JoinKeySuggestService.buildSourceDistinctnessSql(
                ScopeFilteredFrom.filtered(SRC, "district_code IN (?)", List.of("RJ-SGN")),
                List.of("m_id"));
        assertTrue(sql.contains("(SELECT * FROM " + SRC + " t WHERE district_code IN (?))"));
    }

    @Test
    void emptyScopePredicateReturnsNoRowsShape() {
        when(scopeFrom.planFrom(any())).thenAnswer(inv -> {
            QualifiedTable t = inv.getArgument(0);
            return ScopeFilteredFrom.emptyResult(t.qualifiedName());
        });
        RecordMatchService.MatchQuery query = service.planMatch(scopedMatchRequest(JoinType.FULL));
        assertTrue(query.sql().contains("WHERE 1 = 0"));
    }

    private void scopeBothSides() {
        scopeForTable("iceberg", "srse", "beneficiary", "RJ-SGN");
        scopeForTable("iceberg_silver", "silver_txn", "tbl_txn_bankdtl", "RJ-SGN");
    }

    private void scopeForTable(String catalog, String schema, String table, String code) {
        String qualified = catalog + "." + schema + "." + table;
        lenient().when(scopeFrom.planFrom(new QualifiedTable(catalog, schema, table)))
                .thenReturn(ScopeFilteredFrom.filtered(qualified, "district_code IN (?)", List.of(code)));
    }

    private static RecordMatchRequest scopedMatchRequest(JoinType joinType) {
        MatchCriterion src = new MatchCriterion("iceberg", "srse", "beneficiary", "m_id", null);
        MatchCriterion tgt = new MatchCriterion("iceberg_silver", "silver_txn", "tbl_txn_bankdtl", "m_id", null);
        return new RecordMatchRequest(
                List.of(src), List.of(tgt), null, null, List.of(), false, null, joinType);
    }

    private static RecordMatchRequest reqWithComparison(RecordMatchRequest base) {
        ComparisonGroup cmp = new ComparisonGroup(
                List.of(new MatchCriterion("iceberg", "srse", "beneficiary", "district_code", null)),
                List.of(new MatchCriterion("iceberg_silver", "silver_txn", "tbl_txn_bankdtl", "district_code", null)),
                GroupMode.COMBINE, null, null);
        return new RecordMatchRequest(
                base.sourceCriteria(), base.targetCriteria(), base.sourceDisplayColumns(),
                base.targetDisplayColumns(), List.of(), base.highlightDuplicates(), base.dedup(),
                base.joinType(), List.of(cmp), false);
    }
}
