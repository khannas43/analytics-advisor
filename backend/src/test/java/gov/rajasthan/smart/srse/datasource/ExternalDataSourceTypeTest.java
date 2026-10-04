package gov.rajasthan.smart.srse.datasource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalDataSourceTypeTest {

    @Test
    void buildsPostgresqlUrlWithDefaultsAndSsl() {
        assertEquals(
                "jdbc:postgresql://db.internal:5432/egov_db?sslmode=require",
                ExternalDataSourceType.POSTGRESQL.jdbcUrl("db.internal", null, "egov_db", true));
    }

    @Test
    void buildsDb2UrlWithExplicitPort() {
        assertEquals(
                "jdbc:db2://db2.internal:50001/ANALYTICS",
                ExternalDataSourceType.DB2.jdbcUrl("db2.internal", 50001, "ANALYTICS", false));
    }

    @Test
    void buildsBundledAdapterUrls() {
        assertEquals("jdbc:mysql://db.internal:3306/app?sslMode=DISABLED",
                ExternalDataSourceType.MYSQL.jdbcUrl("db.internal", null, "app", false));
        assertEquals("jdbc:mariadb://db.internal:3306/app",
                ExternalDataSourceType.MARIADB.jdbcUrl("db.internal", null, "app", false));
        assertEquals("jdbc:sqlserver://db.internal:1433;databaseName=app;encrypt=false",
                ExternalDataSourceType.SQLSERVER.jdbcUrl("db.internal", null, "app", false));
        assertEquals("jdbc:oracle:thin:@//db.internal:1521/APP",
                ExternalDataSourceType.ORACLE.jdbcUrl("db.internal", null, "APP", false));
    }

    @Test
    void resolveJdbcUrlAcceptsFullUrlInHostField() {
        assertEquals(
                "jdbc:postgresql://10.0.0.5:5432/egov_db",
                ExternalDataSourceType.resolveJdbcUrl(
                        ExternalDataSourceType.POSTGRESQL,
                        "jdbc:postgresql://10.0.0.5:5432/egov_db",
                        999,
                        "ignored",
                        false));
    }

    @Test
    void rejectsUrlFragmentsInHostAndDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> ExternalDataSourceType.POSTGRESQL.jdbcUrl("db/path", 5432, "egov", false));
        assertThrows(IllegalArgumentException.class,
                () -> ExternalDataSourceType.POSTGRESQL.jdbcUrl("db", 5432, "egov?ssl=false", false));
    }
}
