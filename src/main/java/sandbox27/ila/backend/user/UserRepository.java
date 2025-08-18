package sandbox27.ila.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    List<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE CONCAT(u.firstName, ' ', u.lastName) = :fullName")
    Optional<User> findByFullName(@Param("fullName") String fullName);

    Optional<User> findByInternalId(String s);
}
