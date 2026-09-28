package gov.rajasthan.smart.srse.analysis;

import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Streaming encoders for match export formats (AA-18). */
final class MatchExportWriters {

    private MatchExportWriters() {
    }

    static MatchExportStreamer.RowSink jsonRowSink(List<String> columns, JsonGenerator gen) {
        return row -> {
            gen.writeStartObject();
            for (String col : columns) {
                gen.writeFieldName(col);
                writeJsonValue(gen, row.get(col));
            }
            gen.writeEndObject();
        };
    }

    private static void writeJsonValue(JsonGenerator gen, Object v) throws IOException {
        if (v == null) {
            gen.writeNull();
        } else if (v instanceof Number n) {
            gen.writeNumber(n.toString());
        } else if (v instanceof Boolean b) {
            gen.writeBoolean(b);
        } else {
            gen.writeString(String.valueOf(v));
        }
    }

    static MatchExportStreamer.RowSink xmlRowSink(Writer writer, List<String> columns) {
        return row -> {
            writer.write("  <row>\n");
            for (String col : columns) {
                String tag = xmlElementName(col);
                writer.write("    <");
                writer.write(tag);
                writer.write(">");
                writer.write(xmlText(row.get(col)));
                writer.write("</");
                writer.write(tag);
                writer.write(">\n");
            }
            writer.write("  </row>\n");
        };
    }

    private static String xmlElementName(String column) {
        String safe = column.replaceAll("[^A-Za-z0-9_\\-]", "_");
        if (safe.isEmpty() || Character.isDigit(safe.charAt(0))) {
            return "c_" + safe;
        }
        return safe;
    }

    private static String xmlText(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static void writeCsvRow(Writer writer, List<Object> values) throws IOException {
        StringJoiner line = new StringJoiner(",");
        for (Object value : values) {
            line.add(csvField(value));
        }
        writer.write(line.toString());
        writer.write("\r\n");
    }

    private static String csvField(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.indexOf('"') < 0 && text.indexOf(',') < 0
                && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    static Writer utf8Writer(OutputStream outputStream) {
        return new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
    }

    static void writeExcel(
            OutputStream outputStream,
            List<String> columns,
            java.util.function.Consumer<MatchExportStreamer.RowSink> streamRows) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            SXSSFSheet sheet = workbook.createSheet("match");
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
            }
            if (!columns.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, columns.size() - 1));
            }
            sheet.createFreezePane(0, 1);
            int[] rowIndex = {1};
            MatchExportStreamer.RowSink body = row -> {
                if (rowIndex[0] >= MatchExportLimits.EXCEL_MAX_ROWS_PER_SHEET) {
                    throw new IllegalStateException("Excel row limit exceeded during write");
                }
                Row xRow = sheet.createRow(rowIndex[0]++);
                for (int i = 0; i < columns.size(); i++) {
                    setCell(xRow.createCell(i), row.get(columns.get(i)));
                }
            };
            streamRows.accept(body);
            workbook.write(outputStream);
        }
    }

    private static void setCell(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }
}
