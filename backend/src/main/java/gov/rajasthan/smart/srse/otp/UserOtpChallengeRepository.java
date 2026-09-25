package gov.rajasthan.smart.srse.otp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserOtpChallengeRepository extends JpaRepository<UserOtpChallenge, Long> {

    Optional<UserOtpChallenge> findByPublicChallengeId(String publicChallengeId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update UserOtpChallenge c set c.consumedAt = :now
            where c.user.id = :userId and c.purpose = :purpose and c.consumedAt is null
            """)
    int invalidateActiveForUser(
            @Param("userId") long userId,
            @Param("purpose") OtpPurpose purpose,
            @Param("now") Instant now);
}
