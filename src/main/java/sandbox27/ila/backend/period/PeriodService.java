package sandbox27.ila.backend.period;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import sandbox27.ila.backend.course.CourseRepository;
import sandbox27.ila.infrastructure.error.ErrorCode;
import sandbox27.ila.infrastructure.error.ServiceException;

import java.util.List;

@RestController
@Transactional
@RequestMapping("/periods")
@RequiredArgsConstructor
public class PeriodService {

    final PeriodRepository periodRepository;
    final CourseRepository courseRepository;
    private final ModelMapper modelMapper;

    @GetMapping
    public List<ExtendedPeriodDto> getAllPeriods() {
        return periodRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(p -> {
                    ExtendedPeriodDto extendedPeriodDto = modelMapper.map(p, ExtendedPeriodDto.class);
                    extendedPeriodDto.setCourseCount(courseRepository.countByPeriod_Id(p.id));
                    return extendedPeriodDto;
                })
                .toList();
    }

    @PostMapping
    public ExtendedPeriodDto createPeriod(@RequestBody PeriodDto periodDto) {
        validatePeriod(periodDto);
        Period period = modelMapper.map(periodDto, Period.class);
        period.id = null;
        periodRepository.save(period);
        ExtendedPeriodDto result = modelMapper.map(period, ExtendedPeriodDto.class);
        result.setCourseCount(courseRepository.countByPeriod_Id(period.id));
        return result;
    }

    @PutMapping("/{id}")
    public ExtendedPeriodDto updatePeriod(
            @PathVariable("id") Long id,
            @RequestBody ExtendedPeriodDto extendedPeriodDto) {
        validatePeriod(extendedPeriodDto);
        Period period = periodRepository.findById(id).orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
        modelMapper.map(extendedPeriodDto, period);
        periodRepository.saveAndFlush(period);
        if (period.isCurrent())
            periodRepository.findAll().stream()
                    .filter(p -> p.isCurrent() && !p.getId().equals(period.id))
                    .forEach(p -> p.setCurrent(false));
        ExtendedPeriodDto result = modelMapper.map(period, ExtendedPeriodDto.class);
        result.setCourseCount(courseRepository.countByPeriod_Id(period.id));
        return result;
    }

    @DeleteMapping("/{id}")
    public void deletePeriod(@PathVariable("id") Long id) {
        throw new ServiceException(ErrorCode.NotImplemented);
    }

    @GetMapping("/current")
    public PeriodDto getCurrentPeriod() {
        return modelMapper.map(periodRepository.findByCurrent(true).get(), PeriodDto.class);
    }

    private void validatePeriod(PeriodDto period) throws ServiceException {
        if(period.getStartDate() == null) {
            throw new ServiceException(ErrorCode.FieldRequired, "Startdatum");
        }
        if(period.getEndDate() == null) {
            throw new ServiceException(ErrorCode.FieldRequired, "Startdatum");
        }
    }


}
