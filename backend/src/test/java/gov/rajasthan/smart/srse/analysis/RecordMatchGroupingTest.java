package gov.rajasthan.smart.srse.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.rajasthan.smart.srse.audit.AuditCaptureService;
import gov.rajasthan.smart.srse.audit.AuditEventDraft;
import gov.rajasthan.smart.srse.audit.AuditScopeSummaryService;
import gov.rajasthan.smart.srse.compiler.Ast;
import gov.rajasthan.smart.srse.compiler.RuleColumnResolver;
import gov.rajasthan.smart.srse.compiler.RuleCompiler;
import gov.rajasthan.smart.srse.execution.GuardrailProperties;
import gov.rajasthan.smart.srse.lakehouse.AnalysisScopeFromService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService.RegisteredColumn;
import gov.rajasthan.smart.srse.lakehouse.QualifiedColumn;
import gov.rajasthan.smart.srse.lakehouse.QualifiedTable;
import gov.rajasthan.smart.srse.lakehouse.ScopeFilteredFrom;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AA-14 — GROUP BY / aggregates on the match path (§5.5 / 7.2.6). */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class RecordMatchGroupingTest {

    private static final String SRC = "iceberg.srse.beneficiary";
    private static final QualifiedTable SOURCE = new QualifiedTable("iceberg", "srse", "beneficiary");

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LakehouseRegistryService registry;
    @Mock
    private AnalysisColumnMetadataRepository columnMetadata;
    @Mock
    private AnalysisScopeFromService scopeFrom;
    @Mock
    private AuditCaptureService auditCapture;
    @Mock
    private AuditScopeSummaryService scopeSummary;

    private RecordMatchService matchService;

    @BeforeEach
    void setUp() {
        RuleCompiler ruleCompiler = new RuleCompiler(new RuleColumnResolver(registry));
        lenient().when(columnMetadata.findByCatalogNameAndSchemaNameAndTableNameAndColumnName(
                anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(registry.hasColumns(any(), any())).thenReturn(true);
        lenient().when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        stubColumnTypes(Map.of(
                "district", "varchar",
                "annual_income_total", "bigint",
                "dob", "date",
                "m_id", "bigint"));
        matchService = new RecordMatchService(
                jdbc,
                registry,
                new GuardrailProperties(1000, 30, 50),
                columnMetadata,
                new AnalysisProperties(5, 120, 4, 2, 10, 3, 50_000_000L, 10),
                new ObjectMapper(),
                scopeFrom,
                ruleCompiler);
    }

    @Test
    void scopeAndRuleApplyBeforeGroupByInSingleSourceSql() {
        when(scopeFrom.planFrom(SOURCE)).thenReturn(
                ScopeFilteredFrom.filtered(SRC, "district_code IN (?)", List.of("RJ-JPR")));
        Ast.PredicateSpec rules = new Ast.PredicateSpec(
                new Ast.PredicateNode(col("age_years"), Ast.Operator.GT, 18));
        RecordMatchRequest req = groupedSingleSource(
                new DisplayColumn("iceberg", "srse", "beneficiary", "district"),
                List.of(new AggregateSpec(AggregateFunction.COUNT, null, false, null)),
                rules);
        RecordMatchService.MatchQuery query = matchService.planMatch(req);
        assertTrue(query.sql().contains("(SELECT * FROM " + SRC + " t WHERE district_code IN (?) AND (t.age_years > ?)) src"));
        assertTrue(query.sql().contains(" GROUP BY src.district"));
        assertTrue(query.sql().contains("count(*)"));
    }

    @Test
    void sumOnTextColumnRefusedWithTypeInMessage() {
        when(scopeFrom.planFrom(SOURCE)).thenReturn(ScopeFilteredFrom.unfiltered(SRC));
        RecordMatchRequest req = groupedSingleSource(
                new DisplayColumn("iceberg", "srse", "beneficiary", "district"),
                List.of(new AggregateSpec(
                        AggregateFunction.SUM,
                        new DisplayColumn("iceberg", "srse", "beneficiary", "district"),
                        false,
                        null)),
                null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> matchService.planMatch(req));
        assertTrue(ex.getMessage().contains("SUM"));
        assertTrue(ex.getMessage().contains("varchar"), ex.getMessage());
    }

    @Test
    void minMaxAllowedOnTextAndDate() {
        when(scopeFrom.planFrom(SOURCE)).thenReturn(ScopeFilteredFrom.unfiltered(SRC));
        RecordMatchRequest textMin = groupedSingleSource(
                List.of(new DisplayColumn("iceberg", "srse", "beneficiary", "district")),
                List.of(new AggregateSpec(
                        AggregateFunction.MIN,
                        new DisplayColumn("iceberg", "srse", "beneficiary", "district"),
                        false,
                        null)),
                null);
        assertTrue(matchService.planMatch(textMin).sql().contains("min(src.district)"));
        RecordMatchRequest dateMax = groupedSingleSource(
                List.of(new DisplayColumn("iceberg", "srse", "beneficiary", "dob")),
                List.of(new AggregateSpec(
                        AggregateFunction.MAX,
                        new DisplayColumn("iceberg", "srse", "beneficiary", "dob"),
                        false,
                        null)),
                null);
        assertTrue(matchService.planMatch(dateMax).sql().contains("max(src.dob)"));
    }

    @Test
    void aggregateWithoutGroupByReturnsOneRowShape() {
        when(scopeFrom.planFrom(SOURCE)).thenReturn(ScopeFilteredFrom.unfiltered(SRC));
        RecordMatchRequest req = new RecordMatchRequest(
                List.of(), List.of(),
                List.of(new DisplayColumn("iceberg", "srse", "beneficiary", "district")),
                List.of(), List.of(), false, null, null, List.of(), false,
                null, null, true,
                List.of(),
                List.of(new AggregateSpec(AggregateFunction.COUNT, null, false, null)));
        String sql = matchService.planMatch(req).sql();
        assertTrue(sql.contains("count(*)"));
        assertFalse(sql.contains(" GROUP BY "));
    }

    @Test
    void nullGroupLabelInSelect() {
        when(scopeFrom.planFrom(SOURCE)).thenReturn(ScopeFilteredFrom.unfiltered(SRC));
        RecordMatchRequest req = groupedSingleSource(
                List.of(new DisplayColumn("iceberg", "srse", "beneficiary", "district")),
                List.of(new AggregateSpec(AggregateFunction.COUNT, null, false, null)),
                null);
        assertTrue(matchService.planMatch(req).sql().contains("'" + MatchGroupingSql.NULL_GROUP_LABEL + "'"));
    }

    @Test
    void hiddenGroupingColumnRefused() {
        when(scopeFrom.planFrom(SOURCE)).thenReturn(ScopeFilteredFrom.unfiltered(SRC));
        doThrow(new IllegalArgumentException("hidden"))
                .when(registry).validateColumn(new QualifiedColumn(SOURCE, "district"));
        RecordMatchRequest req = groupedSingleSource(
                List.of(new DisplayColumn("iceberg", "srse", "beneficiary", "district")),
                List.of(new AggregateSpec(AggregateFunction.COUNT, null, false, null)),
                null);
        assertThrows(IllegalArgumentException.class, () -> matchService.planMatch(req));
    }

    @Test
    void groupedFanOutMessageMentionsBeforeGrouping() {
        when(scopeFrom.planFrom(any())).thenReturn(ScopeFilteredFrom.unfiltered(SRC));
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(90_000L);
        RecordMatchService strictService = new RecordMatchService(
                jdbc,
                registry,
                new GuardrailProperties(1000, 30, 50),
                columnMetadata,
                new AnalysisProperties(5, 120, 4, 2, 10, 3, 1_000L, 10),
                new ObjectMapper(),
                scopeFrom,
                new RuleCompiler(new RuleColumnResolver(registry)));
        MatchCriterion src = new MatchCriterion("iceberg", "srse", "beneficiary", "district", null);
        MatchCriterion tgt = new MatchCriterion("iceberg", "srse", "beneficiary", "district", null);
        RecordMatchRequest req = new RecordMatchRequest(
                List.of(src), List.of(tgt), null, null, List.of(), false, null, JoinType.INNER,
                List.of(), false, null, null, false,
                List.of(new DisplayColumn("iceberg", "srse", "beneficiary", "district")),
                List.of(new AggregateSpec(AggregateFunction.COUNT, null, false, null)));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> strictService.planMatch(req));
        assertTrue(ex.getMessage().contains("before grouping"), ex.getMessage());
    }

    @Test
    void scopedOfficerSqlDiffersFromSuperAdminForSameGroupQuery() {
        when(scopeFrom.planFrom(SOURCE)).thenReturn(
                ScopeFilteredFrom.filtered(SRC, "district_code IN (?)", List.of("RJ-JPR")));
        RecordMatchRequest req = groupedSingleSource(
                List.of(new DisplayColumn("iceberg", "srse", "beneficiary", "district")),
                List.of(new AggregateSpec(AggregateFunction.COUNT, null, false, null)),
                null);
        String scoped = matchService.planMatch(req).sql();
        when(scopeFrom.planFrom(SOURCE)).thenReturn(ScopeFilteredFrom.unfiltered(SRC));
        String unscoped = matchService.planMatch(req).sql();
        assertTrue(scoped.contains("district_code IN (?)"));
        assertFalse(unscoped.contains("district_code IN"));
    }

    @Test
    void auditGroupedQueryShapeKeepsPlaceholders() {
        when(scopeFrom.planFrom(SOURCE)).thenReturn(ScopeFilteredFrom.unfiltered(SRC));
        when(scopeSummary.summarizeCurrentOfficer()).thenReturn("RJ-JPR");
        when(scopeSummary.currentActorUserId()).thenReturn(1L);
        AnalysisAuditService audit = new AnalysisAuditService(matchService, auditCapture, scopeSummary);
        Ast.PredicateSpec rules = new Ast.PredicateSpec(
                new Ast.PredicateNode(col("age_years"), Ast.Operator.GT, 48000));
        RecordMatchRequest req = groupedSingleSource(
                List.of(new DisplayColumn("iceberg", "srse", "beneficiary", "district")),
                List.of(new AggregateSpec(AggregateFunction.COUNT, null, false, null)),
                rules);
        audit.planMatchAudited(req);
        ArgumentCaptor<AuditEventDraft> captor = ArgumentCaptor.forClass(AuditEventDraft.class);
        verify(auditCapture).recordBestEffort(captor.capture());
        assertTrue(captor.getValue().queryShape().contains("GROUP BY"));
        assertTrue(captor.getValue().queryShape().contains("?"));
        assertFalse(captor.getValue().queryShape().contains("48000"));
    }

    private void stubColumnTypes(Map<String, String> types) {
        lenient().when(registry.describeColumns(any(), any())).thenAnswer(inv -> {
            Map<String, RegisteredColumn> described = new LinkedHashMap<>();
            for (Object column : inv.getArgument(1, List.class)) {
                String name = String.valueOf(column);
                described.put(name, new RegisteredColumn(name, types.getOrDefault(name, "bigint"), null, false, true));
            }
            return described;
        });
    }

    private static RecordMatchRequest groupedSingleSource(
            DisplayColumn groupBy,
            List<AggregateSpec> aggregates,
            Ast.PredicateSpec rules) {
        return groupedSingleSource(List.of(groupBy), aggregates, rules);
    }

    private static RecordMatchRequest groupedSingleSource(
            List<DisplayColumn> groupBy,
            List<AggregateSpec> aggregates,
            Ast.PredicateSpec rules) {
        return new RecordMatchRequest(
                List.of(), List.of(), List.of(), List.of(), List.of(), false, null, null, List.of(), false,
                rules, null, true, groupBy, aggregates);
    }

    private static QualifiedColumn col(String name) {
        return new QualifiedColumn(SOURCE, name);
    }
}
