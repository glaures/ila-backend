package sandbox27.ila.backend.exclusion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.user.User;

import java.util.List;
import java.util.Set;

@Repository
public interface UserBlockExclusionRepository extends JpaRepository<UserBlockExclusion, Long> {

    @Query("SELECT ube FROM UserBlockExclusion ube " +
            "JOIN FETCH ube.user u " +
            "JOIN FETCH ube.block b " +
            "WHERE ube.period.id = :periodId " +
            "ORDER BY u.grade, u.lastName, u.firstName")
    List<UserBlockExclusion> findByPeriodIdWithDetails(@Param("periodId") Long periodId);

    List<UserBlockExclusion> findByUserAndPeriod(User user, Period period);

    List<UserBlockExclusion> findByBlockAndPeriod(Block block, Period period);

    void deleteByUserAndBlockAndPeriod(User user, Block block, Period period);

    boolean existsByUserAndBlockAndPeriod(User user, Block block, Period period);

    @Query("SELECT ube.block.id FROM UserBlockExclusion ube " +
            "WHERE ube.user.userName = :userName AND ube.period.id = :periodId")
    Set<Long> findExcludedBlockIdsByUserAndPeriod(@Param("userName") String userName,
                                                  @Param("periodId") Long periodId);

    void deleteByBlock(Block block);

    void deleteByPeriod(Period period);

    long countByBlockAndPeriod(Block block, Period period);
}