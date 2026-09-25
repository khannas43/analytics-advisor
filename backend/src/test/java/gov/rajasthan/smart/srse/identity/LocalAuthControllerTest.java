package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.config.ApiExceptionHandler;
import gov.rajasthan.smart.srse.config.SecurityConfig;
import gov.rajasthan.smart.srse.security.MockJwtService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LocalAuthController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class, SessionBearerAuthenticationFilter.class,
        SessionTokenService.class, IdentityConfig.class})
@TestPropertySource(properties = "srse.auth-mode=local")
class LocalAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalAuthenticationService authenticationService;

    @MockBean
    private MockJwtService mockJwtService;

    @MockBean
    private AppUserRepository appUserRepository;

    @MockBean
    private ContactVerificationService contactVerificationService;

    @Test
    void loginReturnsTokenOnSuccess() throws Exception {
        when(authenticationService.login("alice", "pw"))
                .thenReturn(new LocalAuthenticationService.LoginResult(
                        true, "tok", null, true, null, null, null));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"pw"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void loginReturnsSameMessageForFailure() throws Exception {
        when(authenticationService.login("missing", "pw"))
                .thenReturn(LocalAuthenticationService.LoginResult.failure(
                        LocalAuthenticationService.INVALID_CREDENTIALS_MESSAGE));
        when(authenticationService.login("bob", "bad"))
                .thenReturn(LocalAuthenticationService.LoginResult.failure(
                        LocalAuthenticationService.INVALID_CREDENTIALS_MESSAGE));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"missing","password":"pw"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value(LocalAuthenticationService.INVALID_CREDENTIALS_MESSAGE));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","password":"bad"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value(LocalAuthenticationService.INVALID_CREDENTIALS_MESSAGE));
    }
}
