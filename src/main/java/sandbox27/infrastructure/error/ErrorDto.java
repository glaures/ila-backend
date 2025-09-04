package sandbox27.infrastructure.error;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ErrorDto {

    ErrorCode code;
    String message;

    public ErrorDto(ErrorCode code, String message) {
        this.code = code;
        this.message = message;
    }
}
