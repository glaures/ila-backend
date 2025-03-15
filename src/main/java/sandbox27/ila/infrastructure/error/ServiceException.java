package sandbox27.ila.infrastructure.error;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class ServiceException extends RuntimeException {

    private final ErrorCode errorCode;
    private Object[] args;

    public ServiceException(ErrorCode errorCode, Object... args) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.args = args;
    }

    public ServiceException(String error) {
        super(error);
        this.errorCode = ErrorCode.UnknownError;
    }


    @Override
    public String toString() {
        if (this.errorCode == ErrorCode.UnknownError)
            return this.getMessage();
        StringBuffer res = new StringBuffer(errorCode.name());
        if (this.args != null) {
            for (Object arg : this.args) {
                res.append(" / " + arg);
            }
        }
        return res.toString();
    }

}
