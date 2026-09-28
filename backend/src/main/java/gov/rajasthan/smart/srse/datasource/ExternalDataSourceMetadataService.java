package gov.rajasthan.smart.srse.datasource;

import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.ColumnView;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.TablePage;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.TableView;

@Service
public class ExternalDataSourceMetadataService {

    private final ExternalDataSourceService dataSourceService;

    public ExternalDataSourceMetadataService(ExternalDataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    public List<String> catalogs(long sourceId) {
        ExternalDataSource source = dataSourceService.requireForBrowse(sourceId);
        try (Connection connection = dataSourceService.openForBrowse(source);
             ResultSet rows = connection.getMetaData().getCatalogs()) {
            Set<String> values = new LinkedHashSet<>();
            while (rows.next()) {
                addNonBlank(values, rows.getString("TABLE_CAT"));
            }
            addNonBlank(values, connection.getCatalog());
            return values.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } catch (SQLException ex) {
            throw metadataFailure(ex);
        }
    }

    public List<String> schemas(long sourceId, String catalog) {
        ExternalDataSource source = dataSourceService.requireForBrowse(sourceId);
        try (Connection connection = dataSourceService.openForBrowse(source);
             ResultSet rows = connection.getMetaData().getSchemas(blankToNull(catalog), null)) {
            Set<String> values = new LinkedHashSet<>();
            while (rows.next()) {
                addNonBlank(values, rows.getString("TABLE_SCHEM"));
            }
            return values.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } catch (SQLException ex) {
            throw metadataFailure(ex);
        }
    }

    public TablePage tables(long sourceId, String catalog, String schema, String search, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page cannot be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        ExternalDataSource source = dataSourceService.requireForBrowse(sourceId);
        List<TableView> all = new ArrayList<>();
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        try (Connection connection = dataSourceService.openForBrowse(source);
             ResultSet rows = connection.getMetaData().getTables(
                     blankToNull(catalog), blankToNull(schema), "%", new String[]{"TABLE", "VIEW"})) {
            while (rows.next()) {
                String name = rows.getString("TABLE_NAME");
                if (!needle.isEmpty() && (name == null || !name.toLowerCase(Locale.ROOT).contains(needle))) {
                    continue;
                }
                all.add(new TableView(
                        rows.getString("TABLE_CAT"),
                        rows.getString("TABLE_SCHEM"),
                        name,
                        rows.getString("TABLE_TYPE"),
                        rows.getString("REMARKS")));
            }
        } catch (SQLException ex) {
            throw metadataFailure(ex);
        }
        all.sort(Comparator.comparing(TableView::name, String.CASE_INSENSITIVE_ORDER));
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new TablePage(List.copyOf(all.subList(from, to)), page, size, all.size());
    }

    public List<ColumnView> columns(long sourceId, String catalog, String schema, String table) {
        ExternalDataSource source = dataSourceService.requireForBrowse(sourceId);
        return columns(source, catalog, schema, table);
    }

    /** Registry path after officer visibility has been checked; does not require Super Admin. */
    public List<ColumnView> registeredColumns(long sourceId, String catalog, String schema, String table) {
        ExternalDataSource source = dataSourceService.requireActiveInternal(sourceId);
        return columns(source, catalog, schema, table);
    }

    /** Admin registration gate: the selected physical table must still exist. */
    public void validateRegisteredTableCandidate(long sourceId, String catalog, String schema, String table) {
        ExternalDataSource source = dataSourceService.requireForBrowse(sourceId);
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("Table is required");
        }
        try (Connection connection = dataSourceService.openForBrowse(source);
             ResultSet rows = connection.getMetaData().getTables(
                     blankToNull(catalog), blankToNull(schema), table, new String[]{"TABLE", "VIEW"})) {
            while (rows.next()) {
                if (table.equals(rows.getString("TABLE_NAME"))) {
                    return;
                }
            }
        } catch (SQLException ex) {
            throw metadataFailure(ex);
        }
        throw new IllegalArgumentException("Table no longer exists on the selected data source");
    }

    private List<ColumnView> columns(
            ExternalDataSource source, String catalog, String schema, String table) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("Table is required");
        }
        List<ColumnView> result = new ArrayList<>();
        try (Connection connection = dataSourceService.openInternal(source)) {
            DatabaseMetaData metadata = connection.getMetaData();
            Set<String> primaryKeys = primaryKeys(metadata, catalog, schema, table);
            try (ResultSet rows = metadata.getColumns(
                    blankToNull(catalog), blankToNull(schema), table, "%")) {
                while (rows.next()) {
                    String name = rows.getString("COLUMN_NAME");
                    int nullable = rows.getInt("NULLABLE");
                    result.add(new ColumnView(
                            name,
                            rows.getString("TYPE_NAME"),
                            rows.getInt("DATA_TYPE"),
                            nullableInt(rows, "COLUMN_SIZE"),
                            nullableInt(rows, "DECIMAL_DIGITS"),
                            nullable != DatabaseMetaData.columnNoNulls,
                            primaryKeys.contains(normalize(name)),
                            rows.getInt("ORDINAL_POSITION"),
                            rows.getString("REMARKS")));
                }
            }
        } catch (SQLException ex) {
            throw metadataFailure(ex);
        }
        result.sort(Comparator.comparingInt(ColumnView::ordinalPosition));
        return List.copyOf(result);
    }

    private static Set<String> primaryKeys(
            DatabaseMetaData metadata, String catalog, String schema, String table) throws SQLException {
        Set<String> keys = new HashSet<>();
        try (ResultSet rows = metadata.getPrimaryKeys(blankToNull(catalog), blankToNull(schema), table)) {
            while (rows.next()) {
                keys.add(normalize(rows.getString("COLUMN_NAME")));
            }
        }
        return keys;
    }

    private static Integer nullableInt(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    private static void addNonBlank(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static IllegalStateException metadataFailure(SQLException ex) {
        String message = ex.getMessage();
        return new IllegalStateException(
                "Could not read database metadata: "
                        + (message == null || message.isBlank() ? ex.getClass().getSimpleName() : message),
                ex);
    }
}
