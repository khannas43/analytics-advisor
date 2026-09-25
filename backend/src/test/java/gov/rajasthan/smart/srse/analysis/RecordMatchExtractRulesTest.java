package gov.rajasthan.smart.srse.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.rajasthan.smart.srse.audit.AuditActionType;
import gov.rajasthan.smart.srse.audit.AuditCaptureService;
import gov.rajasthan.smart.srse.audit.AuditEventDraft;
import gov.rajasthan.smart.srse.audit.AuditOutcome;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

/** AA-13 — extract rules inside scoped derived tables; single-source mode. */
@ExtendWith(MockitoExtension.class)
class RecordMatchExtractRulesTest {

    private static final String SRC = "iceberg.srse.beneficiary";
    private static final String TGT = "iceberg_silver.silver_txn.tbl_txn_bankdtl";
    private static final QualifiedTable SOURCE_TABLE = new QualifiedTable("iceberg", "srse", "beneficiary");

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

    private RuleCompiler ruleCompiler;
    private RecordMatchService matchService;
    private AnalysisAuditService auditService;

    @BeforeEach
    void setUp() {
        ruleCompiler = new RuleCompiler(new RuleColumnResolver(registry));
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
        scopeBothSides();
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
    void sourceRuleLivesInsideDerivedTableForAllJoinTypes() {
        Ast.PredicateSpec rules = new Ast.PredicateSpec(
                new Ast.PredicateNode(col("age_years"), Ast.Operator.GT, 18));
        for (JoinType joinType : JoinType.values()) {
            RecordMatchRequest req = joinRequest(joinType, rules, null);
            RecordMatchService.MatchQuery query = matchService.planMatch(req);
            assertTrue(
                    query.sql().contains("(SELECT * FROM " + SRC + " t WHERE district_code IN (?) AND (t.age_years > ?)) src"),
                    joinType.name());
            int onAt = query.sql().indexOf(" ON ");
            int whereAt = query.sql().indexOf(" WHERE ", onAt);
            String outerOn = whereAt > onAt
                    ? query.sql().substring(onAt, whereAt)
                    : query.sql().substring(onAt);
            assertFalse(outerOn.contains("age_years"), joinType.name() + " ON must not filter on rules");
        }
    }

    @Test
    void hiddenColumnRefusedBeforeCompile() {
        QualifiedColumn hidden = col("secret_col");
        doThrow(new IllegalArgumentException("hidden"))
                .when(registry).validateColumn(hidden);
        Ast.PredicateSpec rules = new Ast.PredicateSpec(
                new Ast.PredicateNode(hidden, Ast.Operator.EQ, "x"));
        assertThrows(IllegalArgumentException.class, () -> matchService.planMatch(joinRequest(JoinType.INNER, rules, null)));
    }

    @Test
    void singleSourceUsesScopeAndRuleInDerivedTable() {
        when(scopeFrom.planFrom(SOURCE_TABLE)).thenReturn(
                ScopeFilteredFrom.filtered(SRC, "district_code IN (?)", List.of("RJ-JPR")));
        DisplayColumn district = new DisplayColumn("iceberg", "srse", "beneficiary", "district_code");
        Ast.PredicateSpec rules = new Ast.PredicateSpec(
                new Ast.PredicateNode(col("age_years"), Ast.Operator.GT, 18));
        RecordMatchRequest req = new RecordMatchRequest(
                List.of(), List.of(),
                List.of(district), List.of(),
                List.of(), false, null, null, List.of(), false,
                rules, null, true);
        RecordMatchService.MatchQuery query = matchService.planMatch(req);
        assertTrue(query.sql().contains("(SELECT * FROM " + SRC + " t WHERE district_code IN (?) AND (t.age_years > ?)) src"));
        assertFalse(query.sql().contains(" JOIN "));
        assertArrayEquals(new Object[] {"RJ-JPR", 18}, query.params().toArray());
    }

    @Test
    void superAdminSingleSourceHasNoScopeButKeepsRule() {
        when(scopeFrom.planFrom(SOURCE_TABLE)).thenReturn(ScopeFilteredFrom.unfiltered(SRC));
        DisplayColumn district = new DisplayColumn("iceberg", "srse", "beneficiary", "district_code");
        Ast.PredicateSpec rules = new Ast.PredicateSpec(
                new Ast.PredicateNode(col("age_years"), Ast.Operator.GT, 18));
        RecordMatchRequest req = new RecordMatchRequest(
                List.of(), List.of(),
                List.of(district), List.of(),
                List.of(), false, null, null, List.of(), false,
                rules, null, true);
        RecordMatchService.MatchQuery query = matchService.planMatch(req);
        assertTrue(query.sql().contains("(SELECT * FROM " + SRC + " t WHERE t.age_years > ?) src"));
        assertFalse(query.sql().contains("district_code IN"));
    }

    @Test
    void auditedMatchKeepsRuleValueAsPlaceholderOnly() {
        when(scopeFrom.planFrom(SOURCE_TABLE)).thenReturn(ScopeFilteredFrom.unfiltered(SRC));
        when(scopeSummary.summarizeCurrentOfficer()).thenReturn("RJ-JPR");
        when(scopeSummary.currentActorUserId()).thenReturn(7L);
        auditService = new AnalysisAuditService(matchService, auditCapture, scopeSummary);
        DisplayColumn district = new DisplayColumn("iceberg", "srse", "beneficiary", "district_code");
        Ast.PredicateSpec rules = new Ast.PredicateSpec(
                new Ast.PredicateNode(col("age_years"), Ast.Operator.GT, 48000));
        RecordMatchRequest req = new RecordMatchRequest(
                List.of(), List.of(),
                List.of(district), List.of(),
                List.of(), false, null, null, List.of(), false,
                rules, null, true);
        auditService.planMatchAudited(req);
        ArgumentCaptor<AuditEventDraft> captor = ArgumentCaptor.forClass(AuditEventDraft.class);
        verify(auditCapture).recordBestEffort(captor.capture());
        AuditEventDraft draft = captor.getValue();
        assertTrue(draft.queryShape().contains("?"));
        assertFalse(draft.queryShape().contains("48000"));
        assertEqualsOutcome(draft, AuditActionType.QUERY_EXECUTED, AuditOutcome.SUCCESS);
    }

    @Test
    void fieldResolverTypesRemovedFromClasspath() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("gov.rajasthan.smart.srse.metadata.FieldResolver"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("gov.rajasthan.smart.srse.metadata.StubFieldResolver"));
    }

    private static void assertEqualsOutcome(AuditEventDraft draft, AuditActionType type, AuditOutcome outcome) {
        org.junit.jupiter.api.Assertions.assertEquals(type, draft.actionType());
        org.junit.jupiter.api.Assertions.assertEquals(outcome, draft.outcome());
    }

    private void scopeBothSides() {
        lenient().when(scopeFrom.planFrom(new QualifiedTable("iceberg", "srse", "beneficiary")))
                .thenReturn(ScopeFilteredFrom.filtered(SRC, "district_code IN (?)", List.of("RJ-SGN")));
        lenient().when(scopeFrom.planFrom(new QualifiedTable("iceberg_silver", "silver_txn", "tbl_txn_bankdtl")))
                .thenReturn(ScopeFilteredFrom.filtered(TGT, "district_code IN (?)", List.of("RJ-SGN")));
    }

    private static RecordMatchRequest joinRequest(
            JoinType joinType, Ast.PredicateSpec sourceRules, Ast.PredicateSpec targetRules) {
        MatchCriterion src = new MatchCriterion("iceberg", "srse", "beneficiary", "m_id", null);
        MatchCriterion tgt = new MatchCriterion("iceberg_silver", "silver_txn", "tbl_txn_bankdtl", "m_id", null);
        return new RecordMatchRequest(
                List.of(src), List.of(tgt), null, null, List.of(), false, null, joinType,
                List.of(), false, sourceRules, targetRules, false);
    }

    private static QualifiedColumn col(String name) {
        return new QualifiedColumn(SOURCE_TABLE, name);
    }
}
