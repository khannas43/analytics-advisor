package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.config.ApiExceptionHandler;
import gov.rajasthan.smart.srse.config.SecurityConfig;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataController;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import gov.rajasthan.smart.srse.security.SessionBearerAuthenticationFilter;
import gov.rajasthan.smart.srse.security.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AA-10 Part 0 — MFA challenge must not authenticate anything except verify-otp. */
@WebMvcTest(controllers = {LocalAuthController.class, AnalysisColumnMetadataController.class})
@Import({ApiExceptionHandler.class, SecurityConfig.class, SessionBearerAuthenticationFilter.class,
        SessionTokenService.class, IdentityConfig.class})
@TestPropertySource(properties = "srse.auth-mode=local")
class MfaChallengeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalAuthenticationService authenticationService;

    @MockBean
    private ContactVerificationService contactVerificationService;

    @MockBean
    private AppUserRepository appUserRepository;

    @MockBean
    private AnalysisColumnMetadataRepository columnMetadataRepository;

    @Test
    void challengeBearerRejectedByOfficerEndpoint() throws Exception {
        String challenge = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/analysis/column-metadata")
                        .header("Authorization", "Bearer " + challenge))
                .andExpect(status().isForbidden());
    }

    @Test
    void challengeBearerRejectedByChangePassword() throws Exception {
        String challenge = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + challenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"a","newPassword":"b"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyOtpEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"%s","code":"123456"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }
}
