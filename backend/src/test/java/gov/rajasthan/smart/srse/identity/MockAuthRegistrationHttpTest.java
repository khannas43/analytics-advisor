package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.audit.AuditService;
import gov.rajasthan.smart.srse.config.ApiExceptionHandler;
import gov.rajasthan.smart.srse.lakehouse.LakehouseAdminController;
import gov.rajasthan.smart.srse.lakehouse.LakehouseBrowseService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.lakehouse.RegisteredTable;
import gov.rajasthan.smart.srse.lakehouse.TableScopeRegistrationService;
import gov.rajasthan.smart.srse.security.Authorities;
import gov.rajasthan.smart.srse.security.MockJwtIssuer;
import gov.rajasthan.smart.srse.security.MockJwtService;
import gov.rajasthan.smart.srse.security.SessionBearerAuthenticationFilter;
import gov.rajasthan.smart.srse.security.SessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mock JWT principals used by {@link MockJwtIssuer} must resolve through
 * {@link AuthenticatedUserService} for audited admin mutations.
 */
@WebMvcTest(controllers = {MockJwtIssuer.class, LakehouseAdminController.class})
@Import({
    ApiExceptionHandler.class,
    gov.rajasthan.smart.srse.config.SecurityConfig.class,
    MockJwtService.class,
    SessionTokenService.class,
    SessionBearerAuthenticationFilter.class
})
@TestPropertySource(properties = {
        "srse.auth-mode=mock",
        "srse.data-mode=synthetic",
        "srse.datasource.analytical.jdbc-url=jdbc:presto://presto:8080/iceberg/srse",
        "srse.datasource.analytical.username=srse",
        "srse.datasource.analytical.driver-class-name=com.facebook.presto.jdbc.PrestoDriver"
})
class MockAuthRegistrationHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockJwtService jwtService;

    @MockBean
    private LakehouseBrowseService browse;

    @MockBean
    private LakehouseRegistryService registry;

    @MockBean
    private TableScopeRegistrationService tableScopeRegistrationService;

    @MockBean
    private AuthenticatedUserService authenticatedUserService;

    @MockBean
    private AuditService auditService;

    @BeforeEach
    void stubRegistry() {
        when(registry.register(anyString(), anyString(), anyString(), anyString(), isNull(), isNull()))
                .thenReturn(new RegisteredTable(1L, "iceberg", "srse", "beneficiary", null));
    }

    @Test
    void mockAdminPostRegistrationWhenActorResolvable() throws Exception {
        AppUser actor = new AppUser(MockAuthUserProvisioner.MOCK_ADMIN_USERNAME, "hash");
        when(authenticatedUserService.requireCurrentUser()).thenReturn(actor);

        String token = jwtService.issue(
                MockAuthUserProvisioner.MOCK_ADMIN_USERNAME,
                List.of(Authorities.SRSE_ADMIN, Authorities.STATE_OFFICER));
        mockMvc.perform(post("/api/admin/lakehouse/registrations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catalog":"iceberg","schema":"srse","table":"beneficiary","layer":""}
                                """))
                .andExpect(status().isOk());

        verify(registry).register(eq("iceberg"), eq("srse"), eq("beneficiary"), eq(""), isNull(), isNull());
    }

    @Test
    void mockOfficerForbiddenOnPostRegistration() throws Exception {
        String token = jwtService.issue(MockAuthUserProvisioner.MOCK_OFFICER_USERNAME);
        mockMvc.perform(post("/api/admin/lakehouse/registrations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catalog":"iceberg","schema":"srse","table":"beneficiary","layer":""}
                                """))
                .andExpect(status().isForbidden());
    }
}
