package sandbox27.ila.backend.assignements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.course.Course;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.Role;
import sandbox27.ila.backend.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseUserAssignmentRepository extends JpaRepository<CourseUserAssignment, Long> {

    Optional<CourseUserAssignment> findByUserAndBlock_Id(User user, Long blockId);

    Optional<CourseUserAssignment> findByCourseAndUser(Course course, User user);

    List<CourseUserAssignment> findByUserAndBlock_Period(User user, Period period);

    List<CourseUserAssignment> findByCourse_Period(Period currentPeriod);

    List<CourseUserAssignment> findByCourse_id(Long courseId);

    int deleteAllByPreset(boolean b);

    List<CourseUserAssignment> findByUser_userName(String userName);

    @Query("""
            select new sandbox27.ila.backend.assignements.StudentAssignmentSummary(
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
            @Param("periodId") Long periodId,   // null = „alle Perioden“
            @Param("minCount") long minCount    // z.B. 3
    );
}

