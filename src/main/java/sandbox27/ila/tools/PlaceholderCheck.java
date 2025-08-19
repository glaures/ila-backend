package sandbox27.ila.tools;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import sandbox27.ila.backend.block.Block;
import sandbox27.ila.backend.block.BlockRepository;
import sandbox27.ila.backend.course.*;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PlaceholderCheck {

    final PeriodRepository periodRepository;
    final BlockRepository blockRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    final CourseRepository courseRepository;

    @Transactional
    public void ensurePlaceholdersInEveryBlockOfCurrentPeriod(){
        Period currentPeriod = periodRepository.findByCurrent(true).orElseThrow();
        List<Block> blocksOfPeriod = blockRepository.findAllByPeriod_id(currentPeriod.getId());
        for(Block block : blocksOfPeriod){
            Optional<CourseBlockAssignment> courseBlockAssignmentOptional = courseBlockAssignmentRepository.findAllByBlock(block).stream()
                    .filter(courseBlockAssignment -> courseBlockAssignment.getCourse().isPlaceholder())
                    .findFirst();
            if(courseBlockAssignmentOptional.isEmpty()){
                Course placeHolderCourse = Course.builder()
                        .period(currentPeriod)
                        .name("Platzhalter-Kurs")
                        .description("Dieser Kurs dient dazu, für diesen Block einen Platzhalter zu wählen, der dann erst in der Folge mit einem noch zu erstellenden Kurs ersetzt wird.")
                        .maxAttendees(9999)
                        .courseCategories(Set.of(CourseCategory.iLa))
                        .grades(List.of(5, 6, 7, 8, 9, 10, 11, 12))
                        .placeholder(true)
                        .build();
                courseRepository.save(placeHolderCourse);
                CourseBlockAssignment courseBlockAssignment = new CourseBlockAssignment();
                courseBlockAssignment.setBlock(block);
                courseBlockAssignment.setCourse(placeHolderCourse);
                courseBlockAssignmentRepository.save(courseBlockAssignment);
            }
        }
    }
}
