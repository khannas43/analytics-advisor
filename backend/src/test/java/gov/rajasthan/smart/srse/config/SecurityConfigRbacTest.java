package gov.rajasthan.smart.srse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import gov.rajasthan.smart.srse.datasource.ExternalDataSourceAdminController;
import gov.rajasthan.smart.srse.datasource.ExternalDataSourceMetadataService;
import gov.rajasthan.smart.srse.datasource.ExternalDataSourceService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseAdminController;
import gov.rajasthan.smart.srse.lakehouse.LakehouseBrowseService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseCatalogController;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;
import gov.rajasthan.smart.srse.lakehouse.TableScopeRegistrationService;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadata;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataController;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import gov.rajasthan.smart.srse.security.Authorities;
import gov.rajasthan.smart.srse.security.SessionBearerAuthenticationFilter;
import gov.rajasthan.smart.srse.security.SessionTokenService;
import gov.rajasthan.smart.srse.security.MockJwtIssuer;
import gov.rajasthan.smart.srse.security.MockJwtService;
import gov.rajasthan.smart.srse.web.AdminConfigController;
import gov.rajasthan.smart.srse.web.AdminConfigService;
import gov.rajasthan.smart.srse.web.ConnectionInfoController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end RBAC through {@link SecurityConfig} + {@link SessionBearerAuthenticationFilter}.
 * Controller logic is stubbed; only HTTP status from authority checks matters here.
 */
@WebMvcTest(controllers = {
        MockJwtIssuer.class,
        ExternalDataSourceAdminController.class,
        LakehouseAdminController.class,
        LakehouseCatalogController.class,
        AnalysisColumnMetadataController.class,
        ConnectionInfoController.class,
        AdminConfigController.class
})
@Import({ApiExceptionHandler.class, SecurityConfig.class, MockJwtService.class,
        SessionTokenService.class, SessionBearerAuthenticationFilter.class})
@TestPropertySource(properties = {
        "srse.data-mode=synthetic",
        "srse.datasource.analytical.jdbc-url=jdbc:presto://presto:8080/iceberg/srse",
        "srse.datasource.analytical.username=srse",
        "srse.datasource.analytical.driver-class-name=com.facebook.presto.jdbc.PrestoDriver"
})
class SecurityConfigRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockJwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LakehouseBrowseService browse;

    @MockBean
    private LakehouseRegistryService registry;

    @MockBean
    private TableScopeRegistrationService tableScopeRegistrationService;

    @MockBean
    private AnalysisColumnMetadataRepository analysisColumnMetadataRepository;

    @MockBean
    private HikariDataSource operational;

    @MockBean(name = "prestoJdbcTemplate")
    private JdbcTemplate presto;

    @MockBean
    private AdminConfigService adminConfigService;

    @MockBean
    private ExternalDataSourceService externalDataSourceService;

    @MockBean
    private ExternalDataSourceMetadataService externalDataSourceMetadataService;

    private String officerToken;
    private String adminToken;

    @BeforeEach
    void tokens() {
        officerToken = jwtService.issue("officer", List.of(Authorities.STATE_OFFICER));
        adminToken = jwtService.issue("admin", List.of(Authorities.SRSE_ADMIN, Authorities.STATE_OFFICER));
    }

    @BeforeEach
    void stubHappyPaths() throws Exception {
        when(browse.listCatalogs()).thenReturn(List.of("iceberg"));
        when(registry.listCatalogs()).thenReturn(List.of("iceberg"));
        when(registry.listRegistrations()).thenReturn(List.of());
        when(registry.register(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new RegisteredTable(1L, "c", "s", "t", "GOLD", false, null, null));
        when(analysisColumnMetadataRepository.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAscColumnNameAsc())
                .thenReturn(List.of());
        when(analysisColumnMetadataRepository.findByCatalogNameAndSchemaNameAndTableNameAndColumnName(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(analysisColumnMetadataRepository.save(any(AnalysisColumnMetadata.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Connection conn = org.mockito.Mockito.mock(Connection.class);
        when(operational.getConnection()).thenReturn(conn);
        when(conn.isValid(3)).thenReturn(true);
        when(operational.getJdbcUrl()).thenReturn("jdbc:db2://db2:50000/SRSEDB");
        when(operational.getUsername()).thenReturn("db2inst1");
        when(operational.getDriverClassName()).thenReturn("com.ibm.db2.jcc.DB2Driver");
        when(presto.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    }

    @Test
    void officerForbiddenOnAdminLakehouseBrowse() throws Exception {
        mockMvc.perform(get("/api/admin/lakehouse/browse/catalogs")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void officerForbiddenOnPostLakehouseRegistration() throws Exception {
        mockMvc.perform(post("/api/admin/lakehouse/registrations")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catalog":"c","schema":"s","table":"t","layer":"GOLD"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPermittedOnPostLakehouseRegistration() throws Exception {
        mockMvc.perform(post("/api/admin/lakehouse/registrations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catalog":"c","schema":"s","table":"t","layer":"GOLD"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void officerPermittedOnAnalysisLakehouseCatalogs() throws Exception {
        mockMvc.perform(get("/api/analysis/lakehouse/catalogs")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminPermittedOnAnalysisLakehouseCatalogs() throws Exception {
        mockMvc.perform(get("/api/analysis/lakehouse/catalogs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void officerPermittedOnGetColumnMetadata() throws Exception {
        mockMvc.perform(get("/api/analysis/column-metadata")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk());
    }

    @Test
    void officerForbiddenOnPutColumnMetadata() throws Exception {
        mockMvc.perform(put("/api/analysis/column-metadata")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catalog":"c","schema":"s","table":"t","column":"col",
                                "businessName":"X","fuzzyMatchable":false,"visible":true}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPermittedOnPutColumnMetadata() throws Exception {
        mockMvc.perform(put("/api/analysis/column-metadata")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catalog":"c","schema":"s","table":"t","column":"col",
                                "businessName":"X","fuzzyMatchable":false,"visible":true}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void officerForbiddenOnDeleteColumnMetadata() throws Exception {
        mockMvc.perform(delete("/api/analysis/column-metadata")
                        .header("Authorization", "Bearer " + officerToken)
                        .param("catalog", "c")
                        .param("schema", "s")
                        .param("table", "t")
                        .param("column", "col"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPermittedOnDeleteColumnMetadata() throws Exception {
        mockMvc.perform(delete("/api/analysis/column-metadata")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("catalog", "c")
                        .param("schema", "s")
                        .param("table", "t")
                        .param("column", "col"))
                .andExpect(status().isOk());
    }

    @Test
    void officerForbiddenOnAdminConfigExport() throws Exception {
        mockMvc.perform(get("/api/admin/config/export")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void officerForbiddenOnAdminConfigImport() throws Exception {
        mockMvc.perform(post("/api/admin/config/import")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemaVersion":"2.0","exportedAt":"2026-01-01T00:00:00Z","dataMode":"synthetic"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void officerForbiddenOnExternalDataSources() throws Exception {
        mockMvc.perform(get("/api/admin/data-sources")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/data-sources/3/registrations")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catalog":"app","schema":"public","table":"people"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousForbiddenOnExternalDataSources() throws Exception {
        mockMvc.perform(get("/api/admin/data-sources"))
                .andExpect(status().isForbidden());
    }

    @Test
    void officerForbiddenOnAdminConnections() throws Exception {
        mockMvc.perform(get("/api/admin/connections")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPermittedOnAdminConnections() throws Exception {
        mockMvc.perform(get("/api/admin/connections")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void mfaChallengeUuidDoesNotAuthenticateOfficerEndpoint() throws Exception {
        mockMvc.perform(get("/api/analysis/column-metadata")
                        .header("Authorization", "Bearer " + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void mockLoginAdminRoleIssuesTokenThatPassesAdminBrowse() throws Exception {
        String body = mockMvc.perform(post("/api/auth/mock-login").param("role", "admin"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/api/admin/lakehouse/browse/catalogs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
