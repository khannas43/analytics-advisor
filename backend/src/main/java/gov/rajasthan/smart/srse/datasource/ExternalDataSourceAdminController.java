package gov.rajasthan.smart.srse.datasource;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;

import java.util.List;

import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.ColumnView;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.ConnectionTestView;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.CreateRequest;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.SourceTypeView;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.SourceView;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.TablePage;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.RegisterTableRequest;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.RegisteredExternalTableView;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.FederationCatalogRequest;

@RestController
@RequestMapping("/api/admin/data-sources")
public class ExternalDataSourceAdminController {

    private final ExternalDataSourceService dataSourceService;
    private final ExternalDataSourceMetadataService metadataService;
    private final LakehouseRegistryService registry;

    public ExternalDataSourceAdminController(
            ExternalDataSourceService dataSourceService,
            ExternalDataSourceMetadataService metadataService,
            LakehouseRegistryService registry) {
        this.dataSourceService = dataSourceService;
        this.metadataService = metadataService;
        this.registry = registry;
    }

    @GetMapping("/types")
    public List<SourceTypeView> supportedTypes() {
        return dataSourceService.supportedTypes();
    }

    @GetMapping
    public List<SourceView> list() {
        return dataSourceService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SourceView create(@Valid @RequestBody CreateRequest request) {
        return dataSourceService.create(request);
    }

    @PostMapping("/{id}/test")
    public ConnectionTestView test(@PathVariable long id) {
        return dataSourceService.test(id);
    }

    @PutMapping("/{id}/federation")
    public SourceView updateFederation(
            @PathVariable long id,
            @Valid @RequestBody FederationCatalogRequest request) {
        return dataSourceService.updateFederationCatalog(id, request.federationCatalog());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        dataSourceService.delete(id);
    }

    @GetMapping("/{id}/catalogs")
    public List<String> catalogs(@PathVariable long id) {
        return metadataService.catalogs(id);
    }

    @GetMapping("/{id}/schemas")
    public List<String> schemas(
            @PathVariable long id,
            @RequestParam(required = false) String catalog) {
        return metadataService.schemas(id, catalog);
    }

    @GetMapping("/{id}/tables")
    public TablePage tables(
            @PathVariable long id,
            @RequestParam(required = false) String catalog,
            @RequestParam(required = false) String schema,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return metadataService.tables(id, catalog, schema, search, page, size);
    }

    @GetMapping("/{id}/columns")
    public List<ColumnView> columns(
            @PathVariable long id,
            @RequestParam(required = false) String catalog,
            @RequestParam(required = false) String schema,
            @RequestParam String table) {
        return metadataService.columns(id, catalog, schema, table);
    }

    @GetMapping("/{id}/registrations")
    public List<RegisteredExternalTableView> registrations(@PathVariable long id) {
        dataSourceService.requireForBrowse(id);
        return registry.listExternalRegistrations(id).stream().map(this::registeredView).toList();
    }

    @PostMapping("/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredExternalTableView register(
            @PathVariable long id,
            @Valid @RequestBody RegisterTableRequest request) {
        return registeredView(registry.registerExternal(
                id,
                request.catalog(),
                request.schema(),
                request.table(),
                request.layer(),
                request.sourceSystem(),
                request.tableGroup()));
    }

    @DeleteMapping("/{sourceId}/registrations/{registrationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@PathVariable long sourceId, @PathVariable long registrationId) {
        dataSourceService.requireForBrowse(sourceId);
        boolean belongs = registry.listExternalRegistrations(sourceId).stream()
                .anyMatch(row -> row.getId() == registrationId);
        if (!belongs) {
            throw new IllegalArgumentException("Registration does not belong to this data source");
        }
        registry.unregister(registrationId);
    }

    private RegisteredExternalTableView registeredView(RegisteredTable row) {
        ExternalDataSource source = dataSourceService.requireForBrowse(row.getExternalDataSourceId());
        return new RegisteredExternalTableView(
                row.getId(),
                row.getCatalogName(),
                row.getSchemaName(),
                row.getTableName(),
                source.getName(),
                row.getExternalCatalog(),
                row.getLayer(),
                row.getSourceSystem(),
                row.getTableGroup());
    }
}
