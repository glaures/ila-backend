package sandbox27.ila.backend.preference;

import jakarta.persistence.OrderBy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.user.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PreferenceRepository extends JpaRepository<Preference, Long> {

    List<Preference> findByUserAndBlockOrderByPreferenceIndex(User user, Block block);
    void deleteByUserAndBlock(User user, Block block);

    @Query("select p.course.id from Preference p where p.block.period.id=:periodId and p.user=:user")
    List<String> findAllPreferencesCourseIdsByUserAndPeriod_Id(@Param("user") User user, @Param("periodId") long periodId);

    List<Preference> findPreferencesByUserAndBlockOrderByPreferenceIndex(User user, Block block);
}
