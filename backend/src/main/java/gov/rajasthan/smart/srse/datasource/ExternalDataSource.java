package gov.rajasthan.smart.srse.datasource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "external_data_source")
public class ExternalDataSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "database_type", nullable = false, length = 32)
    private ExternalDataSourceType databaseType;

    @Column(name = "jdbc_url", nullable = false, length = 1024)
    private String jdbcUrl;

    @Column(nullable = false, length = 256)
    private String username;

    /** Optional Presto catalog exposing this same database for federated/cross-source queries. */
    @Column(name = "federation_catalog", length = 128)
    private String federationCatalog;

    @Column(name = "encrypted_password", nullable = false, length = 4096)
    private String encryptedPassword;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "last_test_status", length = 16)
    private String lastTestStatus;

    @Column(name = "last_test_message", length = 1024)
    private String lastTestMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExternalDataSource() {
    }

    public ExternalDataSource(
            String name,
            ExternalDataSourceType databaseType,
            String jdbcUrl,
            String username,
            String encryptedPassword,
            String federationCatalog) {
        this.name = name;
        this.databaseType = databaseType;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.federationCatalog = normaliseOptional(federationCatalog);
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public ExternalDataSourceType getDatabaseType() { return databaseType; }
    public String getJdbcUrl() { return jdbcUrl; }
    public String getUsername() { return username; }
    public String getFederationCatalog() { return federationCatalog; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public boolean isActive() { return active; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public String getLastTestStatus() { return lastTestStatus; }
    public String getLastTestMessage() { return lastTestMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(
            String name,
            ExternalDataSourceType databaseType,
            String jdbcUrl,
            String username,
            String encryptedPassword,
            String federationCatalog,
            boolean active) {
        this.name = name;
        this.databaseType = databaseType;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        if (encryptedPassword != null) {
            this.encryptedPassword = encryptedPassword;
        }
        this.federationCatalog = normaliseOptional(federationCatalog);
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void recordTest(boolean success, String message) {
        this.lastTestedAt = Instant.now();
        this.lastTestStatus = success ? "UP" : "DOWN";
        this.lastTestMessage = message == null ? null : message.substring(0, Math.min(message.length(), 1024));
        this.updatedAt = Instant.now();
    }

    public void setFederationCatalog(String federationCatalog) {
        this.federationCatalog = normaliseOptional(federationCatalog);
        this.updatedAt = Instant.now();
    }

    private static String normaliseOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
