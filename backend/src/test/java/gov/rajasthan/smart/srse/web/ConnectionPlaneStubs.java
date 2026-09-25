package gov.rajasthan.smart.srse.web;

import java.sql.Connection;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared stubs for the connection-panel slices.
 *
 * <p>These were static nested classes inside a single {@code ConnectionInfoControllerTest}
 * until it was noticed they had <b>never run</b>: Surefire matches test classes by name,
 * and a nested class is {@code ConnectionInfoControllerTest$SyntheticMode}, which no
 * {@code *Test} pattern matches. Three slices sat in the repository since before the fork
 * being counted as coverage and never executing. Splitting them into top-level classes is
 * what makes them real; this holds the setup they used to share.
 */
final class ConnectionPlaneStubs {

    private ConnectionPlaneStubs() {
    }

    static void bothPlanesUp(HikariDataSource operational, JdbcTemplate presto) throws Exception {
        Connection conn = mock(Connection.class);
        when(operational.getConnection()).thenReturn(conn);
        when(conn.isValid(3)).thenReturn(true);
        when(operational.getJdbcUrl()).thenReturn("jdbc:db2://db2:50000/SRSEDB");
        when(operational.getUsername()).thenReturn("db2inst1");
        when(operational.getDriverClassName()).thenReturn("com.ibm.db2.jcc.DB2Driver");
        when(presto.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    }
}
