package gov.rajasthan.smart.srse.datasource;

import gov.rajasthan.smart.srse.audit.AuditActionType;
import gov.rajasthan.smart.srse.audit.AuditOutcome;
import gov.rajasthan.smart.srse.audit.AuditService;
import gov.rajasthan.smart.srse.config.ConnectionTestFailedException;
import gov.rajasthan.smart.srse.identity.AdminAuthorizationService;
import gov.rajasthan.smart.srse.identity.AppUser;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTableRepository;
import gov.rajasthan.smart.srse.lakehouse.LakehouseIdentifiers;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.ConnectionTestView;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.CreateRequest;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.SourceTypeView;
import static gov.rajasthan.smart.srse.datasource.ExternalDataSourceDtos.SourceView;

@Service
public class ExternalDataSourceService {

    private final ExternalDataSourceRepository repository;
    private final ExternalDataSourceSecretCodec secretCodec;
    private final ExternalJdbcAccess jdbcAccess;
    private final AuthenticatedUserService authenticatedUserService;
    private final AdminAuthorizationService authorization;
    private final AuditService auditService;
    private final RegisteredTableRepository registeredTables;

    public ExternalDataSourceService(
            ExternalDataSourceRepository repository,
            ExternalDataSourceSecretCodec secretCodec,
            ExternalJdbcAccess jdbcAccess,
            AuthenticatedUserService authenticatedUserService,
            AdminAuthorizationService authorization,
            AuditService auditService,
            RegisteredTableRepository registeredTables) {
        this.repository = repository;
        this.secretCodec = secretCodec;
        this.jdbcAccess = jdbcAccess;
        this.authenticatedUserService = authenticatedUserService;
        this.authorization = authorization;
        this.auditService = auditService;
        this.registeredTables = registeredTables;
    }

    public List<SourceTypeView> supportedTypes() {
        requireSuperAdmin();
        return Arrays.stream(ExternalDataSourceType.values())
                .map(type -> new SourceTypeView(type.name(), type.label(), type.defaultPort()))
                .toList();
    }

    public List<SourceView> list() {
        requireSuperAdmin();
        return repository.findAllByOrderByNameAsc().stream().map(this::toView).toList();
    }

    @Transactional
    public SourceView create(CreateRequest request) {
        AppUser actor = requireSuperAdmin();
        String name = required("Connection name", request.name());
        if (repository.findByNameIgnoreCase(name).isPresent()) {
            throw new IllegalArgumentException("A data source with this name already exists");
        }
        ExternalDataSourceType type = ExternalDataSourceType.parse(request.databaseType());
        String jdbcUrl = type.jdbcUrl(request.host(), request.port(), request.database(), request.ssl());
        String username = required("Username", request.username());
        String federationCatalog = normaliseOptional(request.federationCatalog());
        if (federationCatalog != null) {
            LakehouseIdentifiers.requireSafe("Presto catalog alias", federationCatalog);
        }
        String encryptedPassword = secretCodec.encrypt(request.password());
        ExternalDataSource source = new ExternalDataSource(
                name, type, jdbcUrl, username, encryptedPassword, federationCatalog);
        ConnectionTestView test = probe(source, request.password());
        source.recordTest(true, test.message());
        source = repository.save(source);
        auditService.recordExternalDataSourceEvent(
                AuditActionType.DATA_SOURCE_CREATED,
                actor,
                AuditOutcome.SUCCESS,
                "name=" + source.getName() + ", type=" + source.getDatabaseType().name());
        return toView(source);
    }

    @Transactional
    public ConnectionTestView test(long id) {
        AppUser actor = requireSuperAdmin();
        ExternalDataSource source = requireSource(id);
        String password = null;
        try {
            password = secretCodec.decrypt(source.getEncryptedPassword());
            ConnectionTestView result = probe(source, password);
            source.recordTest(true, redact(result.message(), password));
            repository.save(source);
            auditService.recordExternalDataSourceEvent(
                    AuditActionType.DATA_SOURCE_TESTED, actor, AuditOutcome.SUCCESS, "name=" + source.getName());
            return new ConnectionTestView(
                    result.success(), redact(result.message(), password), result.productName(), result.productVersion());
        } catch (RuntimeException ex) {
            String failure = redact(safeMessage(ex), password);
            source.recordTest(false, failure);
            repository.save(source);
            auditService.recordExternalDataSourceEvent(
                    AuditActionType.DATA_SOURCE_TESTED, actor, AuditOutcome.FAILURE, "name=" + source.getName());
            return new ConnectionTestView(false, failure, null, null);
        }
    }

    @Transactional
    public void delete(long id) {
        AppUser actor = requireSuperAdmin();
        ExternalDataSource source = requireSource(id);
        long registrations = registeredTables.countByExternalDataSourceId(id);
        if (registrations > 0) {
            throw new IllegalStateException(
                    "Remove the " + registrations + " registered table(s) from this source first");
        }
        repository.delete(source);
        auditService.recordExternalDataSourceEvent(
                AuditActionType.DATA_SOURCE_DELETED,
                actor,
                AuditOutcome.SUCCESS,
                "name=" + source.getName() + ", type=" + source.getDatabaseType().name());
    }

    @Transactional
    public SourceView updateFederationCatalog(long id, String requestedCatalog) {
        AppUser actor = requireSuperAdmin();
        ExternalDataSource source = requireSource(id);
        String catalog = normaliseOptional(requestedCatalog);
        if (catalog != null) {
            LakehouseIdentifiers.requireSafe("Presto catalog alias", catalog);
        }
        source.setFederationCatalog(catalog);
        source = repository.save(source);
        auditService.recordExternalDataSourceEvent(
                AuditActionType.DATA_SOURCE_UPDATED,
                actor,
                AuditOutcome.SUCCESS,
                "name=" + source.getName() + ", federation=" + (catalog == null ? "disabled" : catalog));
        return toView(source);
    }

    public ExternalDataSource requireForBrowse(long id) {
        requireSuperAdmin();
        ExternalDataSource source = requireSource(id);
        if (!source.isActive()) {
            throw new IllegalStateException("Data source is inactive");
        }
        return source;
    }

    public Connection openForBrowse(ExternalDataSource source) throws SQLException {
        return jdbcAccess.open(source, secretCodec.decrypt(source.getEncryptedPassword()));
    }

    /** Internal execution/registry path; caller must have already passed the registry/scope gate. */
    public ExternalDataSource requireActiveInternal(long id) {
        ExternalDataSource source = requireSource(id);
        if (!source.isActive()) {
            throw new IllegalStateException("Data source is inactive: " + source.getName());
        }
        return source;
    }

    public Connection openInternal(ExternalDataSource source) throws SQLException {
        return jdbcAccess.open(source, secretCodec.decrypt(source.getEncryptedPassword()));
    }

    /** A non-pooled, read-only template; each request connection is closed by Spring JDBC. */
    public JdbcTemplate jdbcTemplateForExecution(long id) {
        ExternalDataSource source = requireActiveInternal(id);
        String password = secretCodec.decrypt(source.getEncryptedPassword());
        DataSource dataSource = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return jdbcAccess.open(source, password);
            }

            @Override
            public Connection getConnection(String username, String suppliedPassword) throws SQLException {
                if (!source.getUsername().equals(username) || !password.equals(suppliedPassword)) {
                    throw new SQLException("Credentials are managed by Analytics Advisor");
                }
                return jdbcAccess.open(source, password);
            }
        };
        return new JdbcTemplate(dataSource);
    }

    private ConnectionTestView probe(ExternalDataSource source, String password) {
        try (Connection connection = jdbcAccess.open(source, password)) {
            if (!connection.isValid(8)) {
                throw new ConnectionTestFailedException(
                        "external data source", "Database did not validate the connection");
            }
            DatabaseMetaData metadata = connection.getMetaData();
            String productName = metadata.getDatabaseProductName();
            String productVersion = metadata.getDatabaseProductVersion();
            return new ConnectionTestView(
                    true,
                    "Connected to " + productName + " " + productVersion,
                    productName,
                    productVersion);
        } catch (SQLException ex) {
            throw new ConnectionTestFailedException("external data source", safeMessage(ex));
        }
    }

    private ExternalDataSource requireSource(long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Data source not found"));
    }

    private AppUser requireSuperAdmin() {
        AppUser actor = authenticatedUserService.requireCurrentUser();
        authorization.assertSuperAdminOnly(actor);
        return actor;
    }

    private SourceView toView(ExternalDataSource source) {
        long registrationCount = source.getId() == null
                ? 0
                : registeredTables.countByExternalDataSourceId(source.getId());
        return new SourceView(
                source.getId(),
                source.getName(),
                source.getDatabaseType().name(),
                source.getDatabaseType().label(),
                source.getJdbcUrl(),
                source.getUsername(),
                source.getFederationCatalog(),
                source.isActive(),
                source.getEncryptedPassword() != null && !source.getEncryptedPassword().isBlank(),
                source.getLastTestedAt(),
                source.getLastTestStatus(),
                source.getLastTestMessage(),
                registrationCount);
    }

    private static String required(String label, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String normaliseOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String redact(String message, String secret) {
        if (message == null || message.isBlank()) {
            return message;
        }
        if (secret == null || secret.isBlank()) {
            return message;
        }
        return message.replace(secret, "[redacted]");
    }

    private static String safeMessage(Throwable error) {
        Throwable root = error;
        for (int depth = 0; root.getCause() != null && root.getCause() != root && depth < 20; depth++) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        String safe = message.replaceAll("[\\r\\n]+", " ");
        return safe.substring(0, Math.min(safe.length(), 900));
    }
}
