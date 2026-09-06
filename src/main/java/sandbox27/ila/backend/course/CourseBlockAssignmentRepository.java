package sandbox27.ila.backend.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.freeseats.CourseGradeSeats;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseBlockAssignmentRepository extends JpaRepository<CourseBlockAssignment, Long> {

    Optional<CourseBlockAssignment> findByBlockAndCourse(Block block, Course course);

    Optional<CourseBlockAssignment> findByCourse(Course course);

    @Query("select cba from CourseBlockAssignment cba where cba.block.period.id=:periodId")
    List<CourseBlockAssignment> findAllByPeriodId(Long periodId);

    List<CourseBlockAssignment> findAllByCourse(Course course);

    List<CourseBlockAssignment> findAllByCourse_Id(long id);

    List<CourseBlockAssignment> findAllByBlock(Block block);

    /**
     * Platzzahlen je Kurs, Block und Klassenstufe für die Übersicht der freien Plätze.
     * <p>
     * Der Join über {@code c.grades} klappt jeden Kurs auf die Stufen auf, die er zulässt – ein
     * Kurs für 5–7 liefert drei Zeilen. Kurse ohne Klassenstufe tauchen deshalb nicht auf.
     * Gezählt werden die Zuweisungen zu genau diesem Kurs in genau diesem Block, damit ein Kurs
     * in zwei Blöcken nicht seine Belegung doppelt meldet.
     * <p>
     * Berücksichtigt werden nur Kurse, die der Vergabealgorithmus auch tatsächlich belegen kann –
     * also weder Platzhalter noch Kurse mit {@code manualAssignmentOnly}. Dieselbe Auswahl trifft
     * {@code CourseAssignmentService.assignCourses}; ohne den zweiten Filter meldet die Übersicht
     * freie Plätze, die dem Algorithmus gar nicht zur Verfügung stehen.
     */
    @Query("""
            select new sandbox27.ila.backend.freeseats.CourseGradeSeats(
                       g,
                       cba.block.id,
                       cba.block.dayOfWeek,
                       cba.block.startTime,
                       cba.block.endTime,
                       c.maxAttendees,
                       (select count(a) from CourseUserAssignment a
                          where a.course = c and a.block = cba.block))
            from CourseBlockAssignment cba
            join cba.course c
            join c.grades g
            where cba.block.period.id = :periodId
              and c.placeholder = false
              and c.manualAssignmentOnly = false
            """)
    List<CourseGradeSeats> findCourseGradeSeatsInPeriod(@Param("periodId") Long periodId);

    void deleteByCourse(Course course);
}
