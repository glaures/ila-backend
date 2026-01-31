package sandbox27.ila.backend.period;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record ExchangePhaseDto(
        boolean active,
        boolean upcoming,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd.MM.yyyy HH:mm")
        LocalDateTime start,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd.MM.yyyy HH:mm")
        LocalDateTime end
) {
    /**
     * Factory-Methode zum Erstellen aus einer Period-Entity
     */
    public static ExchangePhaseDto fromPeriod(Period period) {
        LocalDateTime now = LocalDateTime.now();

        boolean active = period.isExchangePhaseActive();

        boolean upcoming = period.getExchangePhaseStart() != null
                && now.isBefore(period.getExchangePhaseStart());

        return new ExchangePhaseDto(
                active,
                upcoming,
                period.getExchangePhaseStart(),
                period.getExchangePhaseEnd()
        );
    }
}