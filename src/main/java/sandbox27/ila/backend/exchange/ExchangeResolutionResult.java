package sandbox27.ila.backend.exchange;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExchangeResolutionResult {
    private int totalRequests;
    private int fulfilled;
    private int unfulfillable;
    private int rounds;

    public static ExchangeResolutionResult empty() {
        return ExchangeResolutionResult.builder()
                .totalRequests(0)
                .fulfilled(0)
                .unfulfillable(0)
                .rounds(0)
                .build();
    }

    public double getFulfillmentRate() {
        if (totalRequests == 0) return 0;
        return (double) fulfilled / totalRequests * 100;
    }
}