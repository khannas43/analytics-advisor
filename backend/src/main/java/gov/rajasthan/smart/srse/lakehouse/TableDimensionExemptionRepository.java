package gov.rajasthan.smart.srse.lakehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TableDimensionExemptionRepository
        extends JpaRepository<TableDimensionExemption, TableDimensionExemptionId> {

    List<TableDimensionExemption> findByRegisteredTableId(long registeredTableId);

    @Query("""
            select e from TableDimensionExemption e
            join fetch e.scopeDimension
            where e.registeredTable.id = :registeredTableId
            """)
    List<TableDimensionExemption> findByRegisteredTableIdWithDimension(
            @Param("registeredTableId") long registeredTableId);

    void deleteByRegisteredTableId(long registeredTableId);

    @Query("""
            select e from TableDimensionExemption e
            join fetch e.scopeDimension
            where e.registeredTable.id in :registrationIds
            """)
    List<TableDimensionExemption> findByRegisteredTableIdsWithDimension(
            @Param("registrationIds") List<Long> registrationIds);
}
