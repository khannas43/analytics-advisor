package gov.rajasthan.smart.srse.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("select ur.role.code from UserRole ur where ur.user.id = :userId")
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
