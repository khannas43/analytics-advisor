package gov.rajasthan.smart.srse.config;

import gov.rajasthan.smart.srse.lakehouse.LakehouseBrowseService;
import gov.rajasthan.smart.srse.lakehouse.LakehouseRegistryService;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataController;
import gov.rajasthan.smart.srse.metadata.AnalysisColumnMetadataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Under srse.auth-mode=rajsewadwar, real SSO payload parsing is still an
 * unimplemented stub ({@code RajSewadwarAuthenticationFilter}) — confirms
 * this fails closed rather than silently permitting everything.
 */
@WebMvcTest(AnalysisColumnMetadataController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "srse.auth-mode=rajsewadwar")
class RajSewadwarAuthModeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalysisColumnMetadataRepository analysisColumnMetadataRepository;

    @MockBean
    private LakehouseRegistryService registry;

    @MockBean
    private LakehouseBrowseService browse;

    @Test
    void officerEndpointRejectsEveryoneUntilRealSsoIsImplemented() throws Exception {
        // 403, not 401 — same default-anonymous-principal behavior as SecurityConfigTest.
        mockMvc.perform(get("/api/analysis/column-metadata"))
                .andExpect(status().isForbidden());
    }
}
