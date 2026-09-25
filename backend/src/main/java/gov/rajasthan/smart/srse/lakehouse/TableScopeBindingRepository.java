package gov.rajasthan.smart.srse.lakehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TableScopeBindingRepository extends JpaRepository<TableScopeBinding, Long> {

    List<TableScopeBinding> findByRegisteredTableId(long registeredTableId);

    @Query("""
            select b from TableScopeBinding b
            join fetch b.scopeLevel l
            join fetch l.dimension
            where b.registeredTable.id = :registeredTableId
            """)
    List<TableScopeBinding> findByRegisteredTableIdWithLevel(@Param("registeredTableId") long registeredTableId);

    void deleteByRegisteredTableId(long registeredTableId);

    long countByScopeLevelId(long scopeLevelId);

    @Query("""
            select count(b) from TableScopeBinding b
            join b.scopeLevel l
            where l.dimension.id = :dimensionId
            """)
    long countByDimensionId(@Param("dimensionId") long dimensionId);

    @Query("""
            select b from TableScopeBinding b
            join fetch b.scopeLevel l
            join fetch l.dimension
            where b.registeredTable.id in :registrationIds
            """)
    List<TableScopeBinding> findByRegisteredTableIdsWithLevel(@Param("registrationIds") List<Long> registrationIds);
}
