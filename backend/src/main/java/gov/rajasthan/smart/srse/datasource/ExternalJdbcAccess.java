package gov.rajasthan.smart.srse.datasource;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;

@Component
public class ExternalJdbcAccess {

    public Connection open(ExternalDataSource source, String password) throws SQLException {
        try {
            Class.forName(source.getDatabaseType().driverClassName());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(
                    "JDBC driver is not installed for " + source.getDatabaseType().label(), ex);
        }
        Properties properties = new Properties();
        properties.setProperty("user", source.getUsername());
        properties.setProperty("password", password);
        properties.setProperty("ApplicationName", "Analytics Advisor metadata browser");
        Connection connection = DriverManager.getConnection(source.getJdbcUrl(), properties);
        try {
            connection.setReadOnly(true);
            return connection;
        } catch (SQLFeatureNotSupportedException ignored) {
            // Metadata browsing still issues no DML. Some older DB2 drivers do not expose this hint.
            return connection;
        } catch (RuntimeException | SQLException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }
}
