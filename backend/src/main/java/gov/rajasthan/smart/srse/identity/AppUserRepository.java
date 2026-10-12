package gov.rajasthan.smart.srse.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    long count();

    List<AppUser> findAllByOrderByUsernameAsc();

    /**
     * Stamps activity only when the stored value is missing or older than {@code staleBefore},
     * so concurrent requests collapse into one write. A bulk update: it does not touch
     * {@code updated_at} or the session version.
     */
    @Modifying
    @Transactional
    @Query("update AppUser u set u.lastActivityAt = :now "
            + "where u.id = :id and (u.lastActivityAt is null or u.lastActivityAt < :staleBefore)")
    int touchActivity(@Param("id") long id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
