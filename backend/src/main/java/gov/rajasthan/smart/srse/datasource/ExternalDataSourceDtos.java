package gov.rajasthan.smart.srse.datasource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ExternalDataSourceDtos {
    private ExternalDataSourceDtos() {
    }

    public record SourceTypeView(String code, String label, int defaultPort) {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 128) String name,
            @NotBlank String databaseType,
            @NotBlank @Size(max = 255) String host,
            @Min(1) @Max(65535) Integer port,
            @NotBlank @Size(max = 255) String database,
            @NotBlank @Size(max = 256) String username,
            @NotBlank @Size(max = 1024) String password,
            boolean ssl,
            @Size(max = 128) String federationCatalog) {
    }

    public record SourceView(
            long id,
            String name,
            String databaseType,
            String databaseTypeLabel,
            String jdbcUrl,
            String username,
            String federationCatalog,
            boolean active,
            boolean passwordConfigured,
            Instant lastTestedAt,
            String lastTestStatus,
            String lastTestMessage,
            long registrationCount) {
    }

    public record ConnectionTestView(boolean success, String message, String productName, String productVersion) {
    }

    public record FederationCatalogRequest(@Size(max = 128) String federationCatalog) {
    }

    public record TableView(String catalog, String schema, String name, String type, String remarks) {
    }

    public record TablePage(List<TableView> items, int page, int size, long total) {
    }

    public record ColumnView(
            String name,
            String dataType,
            int jdbcType,
            Integer size,
            Integer decimalDigits,
            boolean nullable,
            boolean primaryKey,
            int ordinalPosition,
            String remarks) {
    }

    public record RegisterTableRequest(
            String catalog,
            @Size(max = 128) String schema,
            @NotBlank String table,
            String layer,
            String sourceSystem,
            String tableGroup) {
    }

    public record RegisteredExternalTableView(
            long registrationId,
            String logicalCatalog,
            String schema,
            String table,
            String sourceName,
            String physicalCatalog,
            String layer,
            String sourceSystem,
            String tableGroup) {
    }
}
