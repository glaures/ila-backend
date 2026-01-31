package sandbox27.ila.backend.assignments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.User;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseUserAssignmentRepository extends JpaRepository<CourseUserAssignment, Long> {

    List<CourseUserAssignment> findByUserAndBlock_Id(User user, Long blockId);

    Optional<CourseUserAssignment> findByCourseAndUser(Course course, User user);

    List<CourseUserAssignment> findByUserAndBlock_Period(User user, Period period);

    List<CourseUserAssignment> findByCourse_Period(Period currentPeriod);

    List<CourseUserAssignment> findByCourse_idOrderByUser_LastName(Long courseId);

    int deleteAllByPreset(boolean b);

    List<CourseUserAssignment> findByUser_userName(String userName);

    @Query("""
            select new sandbox27.ila.backend.assignments.StudentAssignmentSummary(
                          u.userName,
                          (select count(a) from CourseUserAssignment a
                             where a.user = u
                               and a.block.period.id = :periodId
                          ),
                          (:minCount - (select count(a2) from CourseUserAssignment a2
                             where a2.user = u
                               and a2.block.period.id = :periodId
                          ))
                        )
                        from User u
                        where :role member of u.roles
                          and (select count(a3) from CourseUserAssignment a3
                                 where a3.user = u
                                   and a3.block.period.id = :periodId
                              ) < :minCount
                        order by u.userName asc
            """)
    List<StudentAssignmentSummary> findStudentsWithLessThanInPeriod(
            @Param("role") Role role,
            @Param("periodId") Long periodId,
            @Param("minCount") long minCount
    );

    /**
     * Findet alle Studenten, die mehr als einen Kurs am gleichen Wochentag zugewiesen haben.
     */
    @Query("""
            SELECT new sandbox27.ila.backend.assignments.StudentDuplicateDayOfWeek(
                u.userName,
                u.firstName,
                u.lastName,
                b.dayOfWeek,
                COUNT(cua)
            )
            FROM CourseUserAssignment cua
            JOIN cua.user u
            JOIN cua.block b
            WHERE :role MEMBER OF u.roles
              AND b.period.id = :periodId
            GROUP BY u.userName, u.firstName, u.lastName, b.dayOfWeek
            HAVING COUNT(cua) > 1
            ORDER BY u.lastName, u.firstName, b.dayOfWeek
            """)
    List<StudentDuplicateDayOfWeek> findStudentsWithMultipleCoursesOnSameDayInPeriod(
            @Param("role") Role role,
            @Param("periodId") Long periodId
    );

    void deleteByCourse(Course course);

    List<CourseUserAssignment> findByUser_userNameAndCourse_Period_Id(String userName, Long periodId);

    List<CourseUserAssignment> findByUserAndCourse_Period(User user, Period course_period);

    Optional<CourseUserAssignment> findByUserAndBlock_DayOfWeekAndBlock_Period(User user, DayOfWeek dayOfWeek, Period period);
}