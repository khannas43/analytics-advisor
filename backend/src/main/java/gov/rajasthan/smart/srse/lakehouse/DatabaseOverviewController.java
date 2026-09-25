package gov.rajasthan.smart.srse.lakehouse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only Database Overview (§3.1.4): source system → table group → table → columns.
 *
 * <p>Uses {@link LakehouseRegistryService} officer-visible paths only — never reads
 * {@code registered_table} directly. Metadata browsing is intentionally not audited (§7.3 A7).
 */
@RestController
@RequestMapping("/api/analysis/database-overview")
public class DatabaseOverviewController {

    private final LakehouseRegistryService registry;

    public DatabaseOverviewController(LakehouseRegistryService registry) {
        this.registry = registry;
    }

    @GetMapping("/source-systems")
    public List<String> sourceSystems() {
        return registry.listSourceSystems();
    }

    @GetMapping("/table-groups")
    public List<String> tableGroups(@RequestParam(required = false) String sourceSystem) {
        return registry.listTableGroups(sourceSystem);
    }

    @GetMapping("/tables")
    public List<LakehouseRegistryService.OverviewTableSummary> tables(
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String tableGroup) {
        return registry.listOverviewTables(sourceSystem, tableGroup);
    }

    /** Lazy column load — delegates to the same gate as the Analysis cascade. */
    @GetMapping("/catalogs/{catalog}/schemas/{schema}/tables/{table}/columns")
    public List<LakehouseRegistryService.RegisteredColumn> columns(
            @PathVariable String catalog,
            @PathVariable String schema,
            @PathVariable String table) {
        return registry.listColumns(catalog, schema, table);
    }
}
