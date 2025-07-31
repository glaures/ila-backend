package sandbox27.ila.infrastructure.error;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Arrays;
import java.util.Locale;

@ControllerAdvice
public class ExceptionController {

    private static final Log log = LogFactory.getLog(ExceptionController.class);
    final MessageSource messageSource;
    final ErrorHandlingService errorHandlingService;

    public ExceptionController(MessageSource messageSource, ErrorHandlingService errorHandlingService) {
        this.messageSource = messageSource;
        this.errorHandlingService = errorHandlingService;
    }

    @ExceptionHandler({ServiceException.class})
    public ResponseEntity<ErrorDto> handleServiceException(ServiceException serviceException, Locale locale) {
        String msg;
        try {
            msg = messageSource.getMessage("error." + serviceException.getErrorCode(), serviceException.getArgs(), locale);
        } catch(Throwable t){
            msg = "[Message zum Code " + serviceException.getErrorCode() + " konnte nicht geladen werden]";
            msg += serviceException.getErrorCode() + "(" + Arrays.toString(serviceException.getArgs()) + ")";
        }
        errorHandlingService.handleError(serviceException, msg);
        ErrorDto error = new ErrorDto();
        error.setCode(serviceException.getErrorCode());
        error.setMessage(msg);
        ResponseEntity<ErrorDto> res = new ResponseEntity<ErrorDto>(error, HttpStatus.BAD_REQUEST);
        return res;
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ErrorDto> handleServiceException(Throwable t, Locale locale) {
        errorHandlingService.handleError(t);
        ErrorDto error = new ErrorDto();
        error.setCode(ErrorCode.InternalServerError);
        String message = messageSource.getMessage("" + error.getCode(), null, "internal Error", locale);
        error.setMessage(message);
        ResponseEntity<ErrorDto> res = new ResponseEntity<ErrorDto>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        return res;
    }

    /**
    @ExceptionHandler({UnauthorizedException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorDto> handeInvalidOrExpiredToken(Locale locale){
        ErrorDto error = new ErrorDto();
        error.setCode(ErrorCode.Unauthorized);
        error.setMessage(messageSource.getMessage(ErrorCode.Unauthorized.name(), null, locale));
        ResponseEntity<ErrorDto> res = new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        return res;
    }
    */

}
