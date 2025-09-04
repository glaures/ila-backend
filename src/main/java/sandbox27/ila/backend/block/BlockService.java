package sandbox27.ila.backend.block;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.period.PeriodService;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ErrorHandlingService;
import sandbox27.infrastructure.error.ServiceException;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/blocks")
@Transactional
public class BlockService {

    final BlockRepository blockRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    final PeriodService periodService;
    final ErrorHandlingService errorHandlingService;
    private final ModelMapper modelMapper;

    @GetMapping
    public List<BlockDto> getAllBlocks() {
        return blockRepository.findAll(Sort.by("dayOfWeek", "startTime"))
                .stream()
                .map(block -> modelMapper.map(block, BlockDto.class))
                .toList();
    }

    public Optional<BlockDto> getBlockByCourseId(long courseId) {
        List<CourseBlockAssignment> courseBlockAssignments = courseBlockAssignmentRepository.findAllByCourse_Id(courseId);
        // assuming only one assignment for now
        if (courseBlockAssignments.size() > 1)
            errorHandlingService.handleWarning(courseId + " Kurs hat mehr als einen Block assigned. Nehme den ersten.");
        return courseBlockAssignments.isEmpty()
                ? Optional.empty()
                : Optional.of(modelMapper.map(courseBlockAssignments.get(0).getBlock(), BlockDto.class));
    }

    @PutMapping("/{id}")
    public BlockDto updateBlock(@PathVariable Long id,
                                @RequestBody BlockDto blockDto) {
        Block block = blockRepository.findById(id).orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        modelMapper.map(blockDto, block);
        blockRepository.saveAndFlush(block);
        return modelMapper.map(block, BlockDto.class);
    }

    @PostMapping
    public BlockDto createBlock(@RequestBody BlockDto blockDto) {
        Block block = modelMapper.map(blockDto, Block.class);
        blockRepository.saveAndFlush(block);
        return modelMapper.map(block, BlockDto.class);
    }

}
