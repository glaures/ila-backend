package sandbox27.ila.backend.block;

import jakarta.persistence.OrderBy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.period.Period;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {
    Optional<Block> findByPeriodAndDayOfWeekAndStartTimeAndEndTime(Period period, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime);

    @OrderBy("startTime asc")
    List<Block> findByPeriod_IdAndDayOfWeek(long periodId, DayOfWeek dayOfWeek);

    List<Block> findAllByPeriod_idOrderByDayOfWeekAscStartTimeAsc(long periodId);

}
