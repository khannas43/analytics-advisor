package gov.rajasthan.smart.srse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.rajasthan.smart.srse.lakehouse.LakehouseBrowseService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataController;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import gov.rajasthan.smart.srse.security.MockJwtAuthenticationFilter;
import gov.rajasthan.smart.srse.security.MockJwtIssuer;
import gov.rajasthan.smart.srse.security.MockJwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unlike controller slices that disable filters, this runs the real
 * {@link SecurityConfig} filter chain: officer endpoints reject unauthenticated
 * requests and accept a token issued by {@link MockJwtIssuer}.
 */
@WebMvcTest(controllers = {AnalysisColumnMetadataController.class, MockJwtIssuer.class})
@Import({ApiExceptionHandler.class, SecurityConfig.class, MockJwtService.class, MockJwtAuthenticationFilter.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnalysisColumnMetadataRepository analysisColumnMetadataRepository;

    @MockBean
    private LakehouseRegistryService registry;

    @MockBean
    private LakehouseBrowseService browse;

    @Test
    void officerEndpointRejectsRequestsWithoutToken() throws Exception {
        // Spring Security's default anonymous principal lacks STATE_OFFICER,
        // so hasAuthority(...) denies with 403 (not 401 — no custom
        // AuthenticationEntryPoint is configured).
        mockMvc.perform(get("/api/analysis/column-metadata"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mockLoginThenOfficerEndpointSucceeds() throws Exception {
        when(analysisColumnMetadataRepository.findAllByOrderByCatalogNameAscSchemaNameAscTableNameAscColumnNameAsc())
                .thenReturn(List.of());

        String body = mockMvc.perform(post("/api/auth/mock-login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/api/analysis/column-metadata")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
