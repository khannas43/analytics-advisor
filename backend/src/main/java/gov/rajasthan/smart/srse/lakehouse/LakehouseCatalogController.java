package gov.rajasthan.smart.srse.lakehouse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Officer-facing Catalog → Schema → Table → Column cascade for the Analysis
 * tab's Source/Target pickers.
 *
 * <p>Mirrors the Admin cascade's shape but NOT its reach: every level here is
 * answered from the registry, so an officer is only ever offered what an admin
 * registered. The live lakehouse is consulted only for the column list of an
 * already-registered table (see {@link LakehouseRegistryService#listColumns}).
 */
@RestController
@RequestMapping("/api/analysis/lakehouse")
public class LakehouseCatalogController {

    private final LakehouseRegistryService registry;

    public LakehouseCatalogController(LakehouseRegistryService registry) {
        this.registry = registry;
    }

    @GetMapping("/layers")
    public List<String> layers() {
        return registry.listLayers();
    }

    @GetMapping("/source-systems")
    public List<String> sourceSystems() {
        return registry.listSourceSystems();
    }

    @GetMapping("/table-groups")
    public List<String> tableGroups(@RequestParam(required = false) String sourceSystem) {
        return registry.listTableGroups(sourceSystem);
    }

    @GetMapping("/catalogs")
    public List<String> catalogs(
            @RequestParam(required = false) String layer,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String tableGroup) {
        return registry.listCatalogs(RegistryBrowseFilter.parse(layer, sourceSystem, tableGroup));
    }

    @GetMapping("/catalogs/{catalog}/schemas")
    public List<String> schemas(
            @PathVariable String catalog,
            @RequestParam(required = false) String layer,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String tableGroup) {
        return registry.listSchemas(catalog, RegistryBrowseFilter.parse(layer, sourceSystem, tableGroup));
    }

    @GetMapping("/catalogs/{catalog}/schemas/{schema}/tables")
    public List<TableResponse> tables(
            @PathVariable String catalog,
            @PathVariable String schema,
            @RequestParam(required = false) String layer,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String tableGroup) {
        List<RegisteredTable> rows = registry.listTables(
                catalog, schema, RegistryBrowseFilter.parse(layer, sourceSystem, tableGroup));
        return rows.stream().map(TableResponse::from).toList();
    }

    @GetMapping("/catalogs/{catalog}/schemas/{schema}/tables/{table}/columns")
    public List<LakehouseRegistryService.RegisteredColumn> columns(@PathVariable String catalog,
                                                                   @PathVariable String schema,
                                                                   @PathVariable String table) {
        return registry.listColumns(catalog, schema, table);
    }

    /** Display tags only — never part of match / extract request payloads. */
    public record TableResponse(
            String name,
            String layer,
            String sourceSystem,
            String tableGroup) {
        static TableResponse from(RegisteredTable entity) {
            return new TableResponse(
                    entity.getTableName(),
                    entity.getLayer(),
                    entity.getSourceSystem(),
                    entity.getTableGroup());
        }
    }
}
