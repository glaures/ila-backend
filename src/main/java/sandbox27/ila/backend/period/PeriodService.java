package sandbox27.ila.backend.period;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;

@Service
@RequiredArgsConstructor
public class PeriodService {

    final PeriodRepository periodRepository;

    public Period getPeriodById(long periodId) {
        return periodRepository.findById(periodId).orElseThrow(() -> new ServiceException(ErrorCode.NotFound));
    }
}
