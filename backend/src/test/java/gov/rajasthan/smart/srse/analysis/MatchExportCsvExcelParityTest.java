package gov.rajasthan.smart.srse.analysis;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchExportCsvExcelParityTest {

    @Test
    void csvAndExcelContainSameDataRowCounts() throws Exception {
        List<String> columns = List.of("district", "count");
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("district", "Jaipur");
        row1.put("count", 3);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("district", "Udaipur");
        row2.put("count", 1);
        RecordMatchService.MatchQuery query =
                new RecordMatchService.MatchQuery("SELECT 1", List.of(), columns);

        ByteArrayOutputStream csvOut = new ByteArrayOutputStream();
        var csvWriter = MatchExportWriters.utf8Writer(csvOut);
        csvWriter.write('\uFEFF');
        MatchExportWriters.writeCsvRow(csvWriter, columns.stream().map(Object.class::cast).toList());
        MatchExportWriters.writeCsvRow(csvWriter, MatchExportStreamer.columnValues(query, row1));
        MatchExportWriters.writeCsvRow(csvWriter, MatchExportStreamer.columnValues(query, row2));
        csvWriter.flush();

        ByteArrayOutputStream xlsxOut = new ByteArrayOutputStream();
        MatchExportWriters.writeExcel(xlsxOut, columns, sink -> {
            try {
                sink.accept(row1);
                sink.accept(row2);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });

        String csv = csvOut.toString(StandardCharsets.UTF_8);
        long csvLines = csv.lines().count();
        assertEquals(3, csvLines, "header + 2 data rows");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxOut.toByteArray()))) {
            int physical = workbook.getSheetAt(0).getPhysicalNumberOfRows();
            assertEquals(3, physical, "header + 2 data rows");
        }
    }
}
