package gov.rajasthan.smart.srse.metadata.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TableColumnSnapshotRepository extends JpaRepository<TableColumnSnapshot, Long> {

    List<TableColumnSnapshot> findByCatalogNameAndSchemaNameAndTableNameOrderByOrdinalPositionAsc(
            String catalogName, String schemaName, String tableName);

    @Modifying
    @Query("""
            delete from TableColumnSnapshot s
            where s.catalogName = :catalog and s.schemaName = :schema and s.tableName = :table
            """)
    void deleteAllForTable(
            @Param("catalog") String catalog,
            @Param("schema") String schema,
            @Param("table") String table);

    Optional<TableColumnSnapshot> findFirstByCatalogNameAndSchemaNameAndTableNameOrderBySyncedAtDesc(
            String catalogName, String schemaName, String tableName);
}
