package gov.rajasthan.smart.srse.datasource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExternalDataSourceRepository extends JpaRepository<ExternalDataSource, Long> {
    List<ExternalDataSource> findAllByOrderByNameAsc();
    Optional<ExternalDataSource> findByNameIgnoreCase(String name);
}
