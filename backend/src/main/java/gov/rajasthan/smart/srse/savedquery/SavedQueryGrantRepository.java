package gov.rajasthan.smart.srse.savedquery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SavedQueryGrantRepository extends JpaRepository<SavedQueryGrant, Long> {

    boolean existsBySavedQueryIdAndGranteeUserId(long savedQueryId, long granteeUserId);

    Optional<SavedQueryGrant> findBySavedQueryIdAndGranteeUserId(long savedQueryId, long granteeUserId);
}
