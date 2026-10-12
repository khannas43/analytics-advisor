package gov.rajasthan.smart.srse.metadata.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface MetadataSyncRunRepository extends JpaRepository<MetadataSyncRun, Long> {

    Optional<MetadataSyncRun> findFirstByOverallOutcomeOrderByStartedAtDesc(MetadataSyncOutcome outcome);

    Optional<MetadataSyncRun> findFirstByOrderByStartedAtDesc();
}
