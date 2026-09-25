package gov.rajasthan.smart.srse.analysis;

import gov.rajasthan.smart.srse.execution.GuardrailProperties;
import gov.rajasthan.smart.srse.lakehouse.AnalysisScopeFromService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.compiler.RuleCompiler;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchExportFormatsTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LakehouseRegistryService registry;
    @Mock
    private AnalysisColumnMetadataRepository columnMetadata;
    @Mock
    private AnalysisScopeFromService scopeFrom;
    @Mock
    private AnalysisProperties analysisProperties;
    @Mock
    private RuleCompiler ruleCompiler;

    private RecordMatchService service;

    @BeforeEach
    void setUp() {
        service = new RecordMatchService(
                jdbc,
                registry,
                new GuardrailProperties(1000, 30, 50),
                columnMetadata,
                analysisProperties,
                new ObjectMapper(),
                scopeFrom,
                ruleCompiler);
    }

    @Test
    void jsonStreamsArrayWithoutClosingEarly() throws Exception {
        stubOneRow(Map.of("source_district", "Jaipur"));
        String json = streamToString(service.matchJson(stubQuery()));
        assertTrue(json.startsWith("["), json);
        assertTrue(json.contains("\"source_district\":\"Jaipur\""), json);
        assertTrue(json.endsWith("]"), json.trim());
    }

    @Test
    void xmlStreamsRowsInsideRoot() throws Exception {
        stubOneRow(Map.of("source_district", "Jaipur"));
        String xml = streamToString(service.matchXml(stubQuery()));
        assertTrue(xml.contains("<matchRows>"), xml);
        assertTrue(xml.contains("<row>"), xml);
        assertTrue(xml.contains("<source_district>Jaipur</source_district>"), xml);
        assertTrue(xml.contains("</matchRows>"), xml);
    }

    @Test
    void excelRefusesWhenCountExceedsSheetLimit() {
        when(jdbc.queryForObject(anyString(), any(Object[].class), any(Class.class)))
                .thenReturn(MatchExportLimits.EXCEL_MAX_ROWS_PER_SHEET + 1L);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.assertExcelExportAllowed(stubQuery()));
        assertTrue(ex.getMessage().contains("1048576"), ex.getMessage());
    }

    private static RecordMatchService.MatchQuery stubQuery() {
        return new RecordMatchService.MatchQuery(
                "SELECT 'Jaipur' AS source_district",
                List.of(),
                List.of("source_district"));
    }

    private void stubOneRow(Map<String, Object> row) throws Exception {
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(md.getColumnCount()).thenReturn(row.size());
        List<String> names = List.copyOf(row.keySet());
        for (int i = 0; i < names.size(); i++) {
            when(md.getColumnLabel(i + 1)).thenReturn(names.get(i));
        }
        ResultSet rs = mock(ResultSet.class);
        when(rs.getMetaData()).thenReturn(md);
        for (int i = 0; i < names.size(); i++) {
            when(rs.getObject(i + 1)).thenReturn(row.get(names.get(i)));
        }
        doAnswer(inv -> {
            ((RowCallbackHandler) inv.getArgument(2)).processRow(rs);
            return null;
        }).when(jdbc).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));
    }

    private static String streamToString(StreamingResponseBody body) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
