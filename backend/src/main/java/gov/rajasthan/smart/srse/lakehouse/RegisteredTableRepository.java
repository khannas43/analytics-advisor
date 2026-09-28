package gov.rajasthan.smart.srse.lakehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RegisteredTableRepository extends JpaRepository<RegisteredTable, Long> {

    Optional<RegisteredTable> findByCatalogNameAndSchemaNameAndTableName(
            String catalogName, String schemaName, String tableName);

    boolean existsByCatalogNameAndSchemaNameAndTableName(
            String catalogName, String schemaName, String tableName);

    List<RegisteredTable> findAllByOrderByCatalogNameAscSchemaNameAscTableNameAsc();

    /** Distinct registered catalogs — the first rung of the officer-facing cascade. */
    @Query("SELECT DISTINCT r.catalogName FROM RegisteredTable r ORDER BY r.catalogName")
    List<String> findDistinctCatalogNames();

    /** Distinct registered schemas within one catalog — the second rung. */
    @Query("SELECT DISTINCT r.schemaName FROM RegisteredTable r "
            + "WHERE r.catalogName = :catalogName ORDER BY r.schemaName")
    List<String> findDistinctSchemaNames(String catalogName);

    List<RegisteredTable> findByCatalogNameAndSchemaNameOrderByTableName(
            String catalogName, String schemaName);

    long countByExternalDataSourceId(long externalDataSourceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RegisteredTable r SET r.sourceSystem = :to WHERE r.sourceSystem = :from")
    int bulkRenameSourceSystem(@Param("from") String from, @Param("to") String to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RegisteredTable r SET r.tableGroup = :to WHERE r.tableGroup = :from")
    int bulkRenameTableGroup(@Param("from") String from, @Param("to") String to);
}
