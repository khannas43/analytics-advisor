package gov.rajasthan.smart.srse.analysis;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchExportWritersExcelTest {

    @Test
    void excelFreezesHeaderRowAndAppliesAutofilter() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<String> columns = List.of("source_district", "target_district");
        MatchExportWriters.writeExcel(out, columns, sink -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source_district", "Jaipur");
            row.put("target_district", "Jaipur");
            try {
                sink.accept(row);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertNotNull(sheet.getPaneInformation());
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitTopRow());
            XSSFSheet xSheet = (XSSFSheet) sheet;
            assertNotNull(xSheet.getCTWorksheet().getAutoFilter());
            Row header = sheet.getRow(0);
            CellStyle style = header.getCell(0).getCellStyle();
            Font font = workbook.getFontAt(style.getFontIndexAsInt());
            assertTrue(font.getBold());
            assertEquals("source_district", header.getCell(0).getStringCellValue());
            assertEquals(2, sheet.getPhysicalNumberOfRows());
        }
    }
}
