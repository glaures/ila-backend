package sandbox27.ila.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findUserByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByName(String name);

    Optional<User> findUserByName(String name);

}
