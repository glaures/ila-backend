package sandbox27.ila.backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Optional<User> findByFirstNameAndLastName(String firstName, String lastName);

    Optional<User> findUserByInternalId(String internalId);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r = :role")
    long countByRole(@Param("role") Role role);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r = :role")
    List<User> findAllByRole(Role role);

    Page<User> findAllByInternal(boolean b, Pageable page);

    List<User> findAllByGrade(int grade);

    @Query("SELECT DISTINCT u.grade FROM User u where u.grade>0")
    List<Integer> findAllDistinctGrades();
}
