package sandbox27.ila.backend.block;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.period.ExtendedPeriodDto;
import sandbox27.ila.backend.period.Period;
import sandbox27.ila.backend.period.PeriodDto;
import sandbox27.ila.backend.period.PeriodService;
import sandbox27.ila.infrastructure.error.ErrorCode;
import sandbox27.ila.infrastructure.error.ServiceException;

import java.time.DayOfWeek;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/blocks")
@Transactional
public class BlockService {

    final BlockRepository blockRepository;
    final PeriodService periodService;
    private final ModelMapper modelMapper;

    public List<BlockDto> getAllBlocksOfWorkDay(Long periodId, DayOfWeek dayOfWeek) {
        return blockRepository.findByPeriod_IdAndDayOfWeek(periodId, dayOfWeek)
                .stream()
                .map(b -> modelMapper.map(b, BlockDto.class))
                .toList();
    }

    @GetMapping
    public List<BlockDto> getAllBlocks() {
        return blockRepository.findAll(Sort.by("dayOfWeek", "startTime"))
                .stream().map(block -> modelMapper.map(block, BlockDto.class))
                .toList();
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

    public Block getFirstBlockInCurrentPeriod() {
        PeriodDto period = periodService.getCurrentPeriod();
        return blockRepository.findByPeriod_IdAndDayOfWeek(period.getId(), DayOfWeek.MONDAY).getFirst();
    }
}
