package gov.rajasthan.smart.srse.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the guard. {@link OperationalStoreLiquibaseDb2IT} is off by default, so
 * nothing in an ordinary build would notice if its safety check stopped working
 * — and the check exists because of real damage, not a hypothetical.
 *
 * <p>The DB2 integration test applies this product's changelog to whatever URL
 * it is given, and changeset 002 renames columns. It once defaulted to
 * {@code SRSEDB}, the database SRSE serves from in the shared local DB2
 * container. One run renamed {@code BUSINESSNAME} to {@code BUSINESS_NAME}
 * there, and every SRSE read of {@code analysis_column_metadata} began failing
 * with SQLCODE=-206 against a still-healthy-looking application.
 *
 * <p>This test is ungated on purpose: it runs in every build, so the refusal
 * cannot quietly rot.
 */
class OperationalStoreDb2GuardTest {

    @Test
    void refusesADatabaseOwnedByAnotherProduct() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> OperationalStoreLiquibaseDb2IT.refuseSharedDatabase("jdbc:db2://localhost:50000/SRSEDB"));
        assertTrue(thrown.getMessage().contains("SRSEDB"),
                "the refusal must name the database, or nobody can tell what it objected to");
    }

    @Test
    void refusalIsCaseInsensitive() {
        assertThrows(IllegalStateException.class,
                () -> OperationalStoreLiquibaseDb2IT.refuseSharedDatabase("jdbc:db2://localhost:50000/srsedb"));
    }

    @Test
    void allowsADedicatedDatabase() {
        assertDoesNotThrow(
                () -> OperationalStoreLiquibaseDb2IT.refuseSharedDatabase("jdbc:db2://localhost:50000/AADB"));
    }

    @Test
    void ignoresTrailingDriverProperties() {
        // jdbc:db2 URLs carry properties after the database name; the check must
        // read the database, not everything to the right of the last slash.
        assertThrows(IllegalStateException.class,
                () -> OperationalStoreLiquibaseDb2IT.refuseSharedDatabase(
                        "jdbc:db2://localhost:50000/SRSEDB:currentSchema=DB2INST1;"));
    }

    @Test
    void toleratesNothingConfigured() {
        // An absent URL is the ordinary "test not requested" path, not an error
        // to raise here — the test itself skips on it.
        assertDoesNotThrow(() -> OperationalStoreLiquibaseDb2IT.refuseSharedDatabase(null));
        assertDoesNotThrow(() -> OperationalStoreLiquibaseDb2IT.refuseSharedDatabase(""));
    }
}
