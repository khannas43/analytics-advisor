package gov.rajasthan.smart.srse.lakehouse;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Admin API behind the Catalog → Schema → Table → Column cascade.
 *
 * <p>The {@code /browse/**} endpoints read the LIVE lakehouse — everything the
 * current Presto connection can reach — so an admin can discover what exists.
 * The {@code /registrations} endpoints persist the subset officers may use.
 * Officer-facing code never calls anything on this controller; it reads
 * {@link LakehouseCatalogController} instead.
 */
@RestController
@RequestMapping("/api/admin/lakehouse")
public class LakehouseAdminController {

    private final LakehouseBrowseService browse;
    private final LakehouseRegistryService registry;
    private final TableScopeRegistrationService tableScopeRegistrationService;

    public LakehouseAdminController(
            LakehouseBrowseService browse,
            LakehouseRegistryService registry,
            TableScopeRegistrationService tableScopeRegistrationService) {
        this.browse = browse;
        this.registry = registry;
        this.tableScopeRegistrationService = tableScopeRegistrationService;
    }

    // ---- live browse: the cascade ----

    @GetMapping("/browse/catalogs")
    public List<String> catalogs() {
        return browse.listCatalogs();
    }

    @GetMapping("/browse/catalogs/{catalog}/schemas")
    public List<String> schemas(@PathVariable String catalog) {
        return browse.listSchemas(catalog);
    }

    @GetMapping("/browse/catalogs/{catalog}/schemas/{schema}/tables")
    public List<String> tables(@PathVariable String catalog, @PathVariable String schema) {
        return browse.listTables(catalog, schema);
    }

    @GetMapping("/browse/catalogs/{catalog}/schemas/{schema}/tables/{table}/columns")
    public List<LakehouseBrowseService.ColumnInfo> columns(@PathVariable String catalog,
                                                           @PathVariable String schema,
                                                           @PathVariable String table) {
        return browse.listColumns(catalog, schema, table);
    }

    // ---- registrations ----

    /** Known layer tags plus any distinct tags already in use (for register/edit pickers). */
    @GetMapping("/layers")
    public List<String> layers() {
        LinkedHashSet<String> merged = new LinkedHashSet<>(LakehouseLayers.KNOWN);
        registry.listRegistrations().stream()
                .map(RegisteredTable::getLayer)
                .filter(java.util.Objects::nonNull)
                .forEach(merged::add);
        return List.copyOf(merged);
    }

    @GetMapping("/source-systems")
    public List<String> sourceSystems() {
        return registry.listAllSourceSystemLabels();
    }

    @GetMapping("/table-groups")
    public List<String> tableGroups() {
        return registry.listAllTableGroupLabels();
    }

    @PostMapping("/rename-source-system")
    public RenameLabelResponse renameSourceSystem(@RequestBody RenameLabelRequest req) {
        return new RenameLabelResponse(registry.renameSourceSystemLabel(req.from(), req.to()));
    }

    @PostMapping("/rename-table-group")
    public RenameLabelResponse renameTableGroup(@RequestBody RenameLabelRequest req) {
        return new RenameLabelResponse(registry.renameTableGroupLabel(req.from(), req.to()));
    }

    @GetMapping("/registrations")
    public List<RegistrationResponse> registrations() {
        return registry.listRegistrations().stream().map(RegistrationResponse::from).toList();
    }

    @PostMapping("/registrations")
    public RegistrationResponse register(@RequestBody RegisterTableRequest req) {
        return RegistrationResponse.from(registry.register(
                req.catalog(), req.schema(), req.table(), req.layer(),
                req.sourceSystem(), req.tableGroup()));
    }

    /**
     * Edits display tags in place. The catalog/schema/table triple is not editable — see
     * {@link LakehouseRegistryService#updateRegistrationTags}.
     */
    @PutMapping("/registrations/{id}")
    public RegistrationResponse updateRegistration(@PathVariable long id,
                                                   @RequestBody UpdateRegistrationRequest req) {
        return RegistrationResponse.from(registry.updateRegistrationTags(
                id, req.layer(), req.sourceSystem(), req.tableGroup()));
    }

    @DeleteMapping("/registrations/{id}")
    public void unregister(@PathVariable long id) {
        registry.unregister(id);
    }

    @GetMapping("/registrations/{id}/scope")
    public TableScopeRegistrationService.TableScopeConfigView scopeConfig(@PathVariable long id) {
        return tableScopeRegistrationService.getConfig(id);
    }

    @PutMapping("/registrations/{id}/scope")
    public TableScopeRegistrationService.TableScopeConfigView replaceScopeConfig(
            @PathVariable long id,
            @RequestBody TableScopeRegistrationService.TableScopeConfigRequest request) {
        return tableScopeRegistrationService.replaceConfig(id, request);
    }

    public record RegisterTableRequest(
            String catalog, String schema, String table, String layer, String sourceSystem, String tableGroup) {
    }

    public record UpdateRegistrationRequest(String layer, String sourceSystem, String tableGroup) {
    }

    public record RenameLabelRequest(String from, String to) {
    }

    public record RenameLabelResponse(int tablesUpdated) {
    }

    public record RegistrationResponse(
            Long id,
            String catalog,
            String schema,
            String table,
            String layer,
            String sourceSystem,
            String tableGroup,
            String qualifiedName,
            boolean sharedReference) {
        static RegistrationResponse from(RegisteredTable entity) {
            return new RegistrationResponse(
                    entity.getId(),
                    entity.getCatalogName(),
                    entity.getSchemaName(),
                    entity.getTableName(),
                    entity.getLayer(),
                    entity.getSourceSystem(),
                    entity.getTableGroup(),
                    entity.toQualifiedTable().qualifiedName(),
                    entity.isSharedReference());
        }
    }
}
