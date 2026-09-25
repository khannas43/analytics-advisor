package gov.rajasthan.smart.srse.compiler;

import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService.RegisteredColumn;
import gov.rajasthan.smart.srse.lakehouse.QualifiedColumn;
import gov.rajasthan.smart.srse.lakehouse.QualifiedTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleCompilerTest {

    private static final QualifiedTable TABLE =
            new QualifiedTable("iceberg", "srse", "beneficiary");

    @Mock
    private LakehouseRegistryService registry;

    private RuleCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new RuleCompiler(new RuleColumnResolver(registry));
        lenient().when(registry.describeColumns(eq(TABLE), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<String> cols = invocation.getArgument(1);
                    java.util.Map<String, RegisteredColumn> map = new java.util.HashMap<>();
                    for (String c : cols) {
                        map.put(c, column(c, "varchar"));
                    }
                    return map;
                });
    }

    @Test
    void compilesParameterisedPredicate() {
        var income = new Ast.PredicateNode(
                col("annual_income_total"), Ast.Operator.LT, 48000);
        CompiledQuery q = compiler.compile(new Ast.PredicateSpec(income), TABLE);
        assertTrue(q.predicateSql().contains("t.annual_income_total"));
        assertTrue(q.predicateSql().contains("?"));
        assertEquals(List.of(48000), q.params());
    }

    @Test
    void numberTypedValueUsesTryCastOnVarcharColumn() {
        when(registry.describeColumns(eq(TABLE), eq(List.of("annual_income_total"))))
                .thenReturn(Map.of("annual_income_total", column("annual_income_total", "varchar")));
        var node = new Ast.PredicateNode(col("annual_income_total"), Ast.Operator.LT, 48000);
        CompiledQuery q = compiler.compile(new Ast.PredicateSpec(node), TABLE);
        assertTrue(q.predicateSql().contains("TRY_CAST(t.annual_income_total AS DOUBLE)"));
    }

    @Test
    void booleanColumnIsNotCoerced() {
        when(registry.describeColumns(eq(TABLE), eq(List.of("is_active"))))
                .thenReturn(Map.of("is_active", column("is_active", "boolean")));
        var node = new Ast.PredicateNode(col("is_active"), Ast.Operator.IS_TRUE);
        CompiledQuery q = compiler.compile(new Ast.PredicateSpec(node), TABLE);
        assertEquals("t.is_active = TRUE", q.predicateSql());
    }

    @Test
    void temporalColumnIsNotCoerced() {
        when(registry.describeColumns(eq(TABLE), eq(List.of("dob"))))
                .thenReturn(Map.of("dob", column("dob", "date")));
        var node = new Ast.PredicateNode(col("dob"), Ast.Operator.LT, "2000-01-01");
        CompiledQuery q = compiler.compile(new Ast.PredicateSpec(node), TABLE);
        assertEquals("t.dob < ?", q.predicateSql());
        assertEquals(List.of("2000-01-01"), q.params());
        assertFalse(q.predicateSql().contains("TRY_CAST"));
    }

    @Test
    void wrongTableRefused() {
        QualifiedColumn other = new QualifiedColumn("iceberg", "srse", "other", "x");
        var node = new Ast.PredicateNode(other, Ast.Operator.EQ, "a");
        assertThrows(IllegalArgumentException.class,
                () -> compiler.compile(new Ast.PredicateSpec(node), TABLE));
    }

    private static QualifiedColumn col(String name) {
        return new QualifiedColumn(TABLE, name);
    }

    private static RegisteredColumn column(String name, String type) {
        return new RegisteredColumn(name, type, null, false, true);
    }
}
