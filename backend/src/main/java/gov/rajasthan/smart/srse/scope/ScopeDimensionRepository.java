package gov.rajasthan.smart.srse.scope;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScopeDimensionRepository extends JpaRepository<ScopeDimension, Long> {

    Optional<ScopeDimension> findByCodeIgnoreCase(String code);
}
