package gov.rajasthan.smart.srse.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    long count();

    List<AppUser> findAllByOrderByUsernameAsc();
}
