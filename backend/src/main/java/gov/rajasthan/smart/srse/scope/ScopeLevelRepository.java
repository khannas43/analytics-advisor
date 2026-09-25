package gov.rajasthan.smart.srse.scope;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScopeLevelRepository extends JpaRepository<ScopeLevel, Long> {

    List<ScopeLevel> findByDimensionIdOrderByDepthAsc(long dimensionId);

    Optional<ScopeLevel> findByDimensionIdAndDepth(long dimensionId, int depth);
}
