package gov.rajasthan.smart.srse.metadata.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetadataSyncTableResultRepository extends JpaRepository<MetadataSyncTableResult, Long> {

    List<MetadataSyncTableResult> findByRunIdOrderByCatalogNameAscSchemaNameAscTableNameAsc(long runId);
}
