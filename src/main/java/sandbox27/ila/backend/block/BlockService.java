package sandbox27.ila.backend.block;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.course.CourseBlockAssignment;
import sandbox27.ila.backend.course.CourseBlockAssignmentRepository;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodRepository;
import sandbox27.ila.backend.user.Role;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ErrorHandlingService;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/blocks")
@Transactional
public class BlockService {

    final BlockRepository blockRepository;
    final CourseBlockAssignmentRepository courseBlockAssignmentRepository;
    final ErrorHandlingService errorHandlingService;
    private final ModelMapper modelMapper;
    private final PeriodRepository periodRepository;

    @GetMapping
    public List<BlockDto> getAllBlocks(@RequestParam(value = "period-id", required = false) Long periodId) {
        Period period = periodId == null
                ? periodRepository.findByCurrent(true).orElseThrow(() -> new ServiceException(ErrorCode.NotFound))
                : periodRepository.findById(periodId).orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        return blockRepository.findAllByPeriod_idOrderByDayOfWeekAscStartTimeAsc(period.getId())
                .stream()
                .map(block -> modelMapper.map(block, BlockDto.class))
                .toList();
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @PutMapping("/{id}")
    public BlockDto updateBlock(@PathVariable Long id,
                                @RequestBody BlockDto blockDto) {
        Block block = blockRepository.findById(id).orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        modelMapper.map(blockDto, block);
        block = blockRepository.saveAndFlush(block);
        return modelMapper.map(block, BlockDto.class);
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @PostMapping
    public BlockDto createBlock(@RequestBody BlockDto blockDto) {
        Block block = modelMapper.map(blockDto, Block.class);
        block = blockRepository.saveAndFlush(block);
        return modelMapper.map(block, BlockDto.class);
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @DeleteMapping("/{id}")
    public void deleteBlock(@PathVariable("id") Long id) {
        Block block = blockRepository.findById(id).orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        courseBlockAssignmentRepository.deleteAll(courseBlockAssignmentRepository.findAllByBlock(block));
        blockRepository.delete(block);
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @PostMapping("/copy-from-period")
    public List<BlockDto> copyBlocksFromPeriod(
            @RequestParam("source-period-id") Long sourcePeriodId,
            @RequestParam("target-period-id") Long targetPeriodId) {
        Period targetPeriod = periodRepository.findById(targetPeriodId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        List<Block> sourceBlocks = blockRepository.findAllByPeriod_idOrderByDayOfWeekAscStartTimeAsc(sourcePeriodId);
        List<Block> copiedBlocks = sourceBlocks.stream()
                .map(oldBlock ->
                        Block.builder()
                                .period(targetPeriod)
                                .endTime(oldBlock.getEndTime())
                                .startTime(oldBlock.getStartTime())
                                .dayOfWeek(oldBlock.getDayOfWeek())
                                .build())
                .toList();
        blockRepository.saveAll(copiedBlocks);
        return copiedBlocks.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
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

    private BlockDto toDto(Block block) {
        return modelMapper.map(block, BlockDto.class);
    }

}
