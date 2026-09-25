package gov.rajasthan.smart.srse.analysis;

import gov.rajasthan.smart.srse.lakehouse.AnalysisScopeFromService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.QualifiedTable;
import gov.rajasthan.smart.srse.lakehouse.ScopeFilteredFrom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** AA-16 §5.1 — scoped distinct value lists with explicit truncation. */
@ExtendWith(MockitoExtension.class)
class ColumnDistinctValuesServiceTest {

    private static final String TABLE = "iceberg.srse.beneficiary";

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LakehouseRegistryService registry;
    @Mock
    private AnalysisScopeFromService scopeFrom;

    private ColumnDistinctValuesService service;

    @BeforeEach
    void setUp() {
        service = new ColumnDistinctValuesService(
                jdbc,
                registry,
                scopeFrom,
                new AnalysisProperties(5, 120, 4, 2, 10, 3, 50_000_000L, 10, 2));
    }

    @Test
    void planUsesScopedDerivedTableAndLimitPlusOne() {
        when(scopeFrom.planFrom(new QualifiedTable("iceberg", "srse", "beneficiary")))
                .thenReturn(ScopeFilteredFrom.filtered(TABLE, "district_code IN (?)", List.of("RJ-JPR")));
        ColumnDistinctValuesService.PlannedColumnValues planned = service.plan(
                new ColumnDistinctValuesService.ColumnValuesRequest(
                        "iceberg", "srse", "beneficiary", "district", null));
        assertTrue(planned.sql().contains("(SELECT * FROM " + TABLE + " t WHERE district_code IN (?)) scoped"));
        assertTrue(planned.sql().contains("LIMIT 3"));
        assertEquals(List.of("RJ-JPR"), planned.params());
    }

    @Test
    void buildResponseReportsTruncationAndNullValue() {
        ColumnDistinctValuesService.ColumnValuesResponse response = ColumnDistinctValuesService.buildResponse(
                new ArrayList<>(Arrays.asList("Jaipur", null, "Udaipur")), 2);
        assertTrue(response.truncated());
        assertEquals("Jaipur", response.values().get(0));
        assertNull(response.values().get(1));
        assertTrue(response.includesNull());
    }

    @Test
    void searchAddsBoundLikePredicate() {
        when(scopeFrom.planFrom(any())).thenReturn(ScopeFilteredFrom.unfiltered(TABLE));
        ColumnDistinctValuesService.PlannedColumnValues planned = service.plan(
                new ColumnDistinctValuesService.ColumnValuesRequest(
                        "iceberg", "srse", "beneficiary", "district", "Jai"));
        assertTrue(planned.sql().contains("LIKE lower(?)"));
        assertEquals(List.of("%Jai%"), planned.params());
    }

    @Test
    void searchIsCaseInsensitiveOnBothSides() {
        // The box only appears once the list is truncated, so it is the officer's
        // only route to a value beyond the cap. Matching case-sensitively means
        // typing "jaipur" returns nothing and they conclude the value is absent —
        // exactly what reporting truncation exists to prevent. Observed: "jai"
        // found nothing while "Jai" found Jaipur.
        when(scopeFrom.planFrom(any())).thenReturn(ScopeFilteredFrom.unfiltered(TABLE));
        ColumnDistinctValuesService.PlannedColumnValues planned = service.plan(
                new ColumnDistinctValuesService.ColumnValuesRequest(
                        "iceberg", "srse", "beneficiary", "district", "jai"));
        assertTrue(planned.sql().contains("lower(CAST(scoped.district AS VARCHAR))"), planned.sql());
        assertTrue(planned.sql().contains("LIKE lower(?)"), planned.sql());
        // Still bound, never interpolated.
        assertEquals(List.of("%jai%"), planned.params());
    }
}
