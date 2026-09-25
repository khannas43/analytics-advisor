package gov.rajasthan.smart.srse.audit;

import gov.rajasthan.smart.srse.audit.admin.AuditPageResponse;
import gov.rajasthan.smart.srse.config.ApiExceptionHandler;
import gov.rajasthan.smart.srse.config.SecurityConfig;
import gov.rajasthan.smart.srse.identity.AuthenticatedUserService;
import gov.rajasthan.smart.srse.security.Authorities;
import gov.rajasthan.smart.srse.security.MockJwtService;
import gov.rajasthan.smart.srse.security.SessionBearerAuthenticationFilter;
import gov.rajasthan.smart.srse.security.SessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditAdminController.class)
@Import({SecurityConfig.class, MockJwtService.class, SessionTokenService.class,
        SessionBearerAuthenticationFilter.class, ApiExceptionHandler.class})
@TestPropertySource(properties = {
        "srse.auth-mode=mock",
        "srse.frontend-origins=http://localhost:3000"
})
class AuditAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MockJwtService jwtService;

    @MockBean
    private AuditViewerService viewerService;
    @MockBean
    private AuditCaptureService auditCapture;
    @MockBean
    private AuditScopeSummaryService scopeSummary;
    @MockBean
    private AuthenticatedUserService authenticatedUserService;

    private String adminWithoutAudit;
    private String auditReader;

    @BeforeEach
    void tokens() {
        adminWithoutAudit = jwtService.issue("admin", List.of(Authorities.SRSE_ADMIN, Authorities.STATE_OFFICER));
        auditReader = jwtService.issue("reader", List.of(Authorities.AUDIT_READ));
        when(viewerService.search(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new AuditPageResponse(List.of(), 0, 50, 0, 0, 365, "note"));
    }

    @Test
    void adminWithoutAuditReadGets403() throws Exception {
        mockMvc.perform(get("/api/admin/audit").header("Authorization", "Bearer " + adminWithoutAudit))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditReaderMayList() throws Exception {
        mockMvc.perform(get("/api/admin/audit").header("Authorization", "Bearer " + auditReader))
                .andExpect(status().isOk());
    }
}
