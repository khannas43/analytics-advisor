package gov.rajasthan.smart.srse.scope;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScopeNodeRepository extends JpaRepository<ScopeNode, Long> {

    long countByParentId(Long parentId);

    List<ScopeNode> findByDimensionIdOrderByPathAsc(long dimensionId);

    @Query("""
            select n from ScopeNode n
            join fetch n.dimension
            join fetch n.level
            left join fetch n.parent
            order by n.path
            """)
    List<ScopeNode> findAllWithDimensionOrderByPath();

    @Query("""
            select n from ScopeNode n
            join fetch n.dimension d
            join fetch n.level
            where d.id = :dimensionId
            order by n.path
            """)
    List<ScopeNode> findByDimensionWithFetchOrderByPath(@Param("dimensionId") long dimensionId);
}
