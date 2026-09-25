package gov.rajasthan.smart.srse.savedquery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SavedQueryRepository extends JpaRepository<SavedQuery, Long> {

    List<SavedQuery> findByOwnerUserIdOrderByUpdatedAtDesc(Long ownerUserId);

    @Query("""
            SELECT q FROM SavedQuery q
            WHERE q.ownerUserId = :userId
               OR q.id IN (
                    SELECT g.savedQueryId FROM SavedQueryGrant g WHERE g.granteeUserId = :userId
               )
            ORDER BY q.updatedAt DESC
            """)
    List<SavedQuery> findVisibleToUser(@Param("userId") long userId);
}
