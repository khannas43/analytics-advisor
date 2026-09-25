package gov.rajasthan.smart.srse.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserScopeAssignmentRepository extends JpaRepository<UserScopeAssignment, UserScopeAssignmentId> {

    @Query("""
            select a from UserScopeAssignment a
            join fetch a.scopeNode n
            join fetch n.dimension
            join fetch n.level
            where a.user.id = :userId
            """)
    List<UserScopeAssignment> findAllWithNodeByUserId(@Param("userId") Long userId);

    long countByScopeNodeId(long scopeNodeId);

    void deleteByUserId(long userId);
}
