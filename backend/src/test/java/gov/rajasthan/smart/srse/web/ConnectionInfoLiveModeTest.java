package gov.rajasthan.smart.srse.web;

import com.zaxxer.hikari.HikariDataSource;
import gov.rajasthan.smart.srse.security.MockJwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Live mode derives its own label when none is configured. */
@WebMvcTest(ConnectionInfoController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "srse.datasource.analytical.jdbc-url=jdbc:presto://presto:8080/iceberg/srse",
        "srse.datasource.analytical.username=srse",
        "srse.datasource.analytical.driver-class-name=com.facebook.presto.jdbc.PrestoDriver",
        "srse.data-mode=live"
})
class ConnectionInfoLiveModeTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private HikariDataSource operational;
    @MockBean(name = "prestoJdbcTemplate")
    private JdbcTemplate presto;
    @MockBean
    private MockJwtService mockJwtService;

    @Test
    void defaultEnvironmentLabelIsProductionLive() throws Exception {
        ConnectionPlaneStubs.bothPlanesUp(operational, presto);
        mockMvc.perform(get("/api/admin/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataMode").value("live"))
                .andExpect(jsonPath("$.environmentLabel").value("Production (Live)"));
    }
}
