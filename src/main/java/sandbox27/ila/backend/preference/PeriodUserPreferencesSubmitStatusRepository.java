package sandbox27.ila.backend.preference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.User;

import java.util.Optional;

@Repository
public interface PeriodUserPreferencesSubmitStatusRepository extends JpaRepository<PeriodUserPreferencesSubmitStatus, Long> {

    Optional<PeriodUserPreferencesSubmitStatus> findByUserAndPeriod(User user, Period period);
}
