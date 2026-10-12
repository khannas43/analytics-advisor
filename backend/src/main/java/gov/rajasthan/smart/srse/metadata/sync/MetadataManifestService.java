package gov.rajasthan.smart.srse.metadata.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTableRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetadataManifestService {

    private final RegisteredTableRepository registrations;
    private final TableColumnSnapshotRepository snapshots;
    private final MetadataSyncRunRepository runs;
    private final ObjectMapper objectMapper;

    public MetadataManifestService(
            RegisteredTableRepository registrations,
            TableColumnSnapshotRepository snapshots,
            MetadataSyncRunRepository runs,
            ObjectMapper objectMapper) {
        this.registrations = registrations;
        this.snapshots = snapshots;
        this.runs = runs;
        this.objectMapper = objectMapper;
    }

    public String buildManifestJson() {
        Long syncRunId = runs.findFirstByOrderByStartedAtDesc().map(MetadataSyncRun::getId).orElse(null);
        List<Map<String, Object>> tables = new ArrayList<>();
        registrations.findAll().stream()
                .sorted(java.util.Comparator
                        .comparing((gov.rajasthan.smart.srse.lakehouse.RegisteredTable r) -> r.getCatalogName())
                        .thenComparing(gov.rajasthan.smart.srse.lakehouse.RegisteredTable::getSchemaName)
                        .thenComparing(gov.rajasthan.smart.srse.lakehouse.RegisteredTable::getTableName))
                .forEach(reg -> {
                    List<TableColumnSnapshot> cols = snapshots
                            .findByCatalogNameAndSchemaNameAndTableNameOrderByOrdinalPositionAsc(
                                    reg.getCatalogName(), reg.getSchemaName(), reg.getTableName());
                    if (cols.isEmpty()) {
                        return;
                    }
                    List<Map<String, String>> columnMaps = cols.stream()
                            .map(c -> Map.of("name", c.getColumnName(), "dataType", c.getDataType()))
                            .toList();
                    Map<String, Object> table = new LinkedHashMap<>();
                    table.put("catalog", reg.getCatalogName());
                    table.put("schema", reg.getSchemaName());
                    table.put("table", reg.getTableName());
                    table.put("columns", columnMaps);
                    tables.add(table);
                });
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAt", Instant.now().toString());
        root.put("syncRunId", syncRunId);
        root.put("tables", tables);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Manifest serialisation failed", ex);
        }
    }
}
