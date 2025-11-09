package sandbox27.infrastructure.error;

import lombok.RequiredArgsConstructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import sandbox27.infrastructure.security.AuthenticatedUser;
import sandbox27.infrastructure.security.SecUser;

import java.util.Arrays;
import java.util.Locale;

@ControllerAdvice
@RequiredArgsConstructor
public class ExceptionController {

    private static final Log log = LogFactory.getLog(ExceptionController.class);
    final MessageSource messageSource;
    final ErrorHandlingService errorHandlingService;
    final ApplicationEventPublisher applicationEventPublisher;

    @ExceptionHandler({ServiceException.class})
    public ResponseEntity<ErrorDto> handleServiceException(ServiceException serviceException, Locale locale) {
        String msg;
        try {
            msg = messageSource.getMessage("error." + serviceException.getErrorCode(), serviceException.getArgs(), locale);
        } catch (Throwable t) {
            msg = "[Message zum Code " + serviceException.getErrorCode() + " konnte nicht geladen werden]";
            msg += serviceException.getErrorCode() + "(" + Arrays.toString(serviceException.getArgs()) + ")";
        }
        ErrorDto error = new ErrorDto();
        error.setCode(serviceException.getErrorCode());
        error.setMessage(msg);
        errorHandlingService.handleError(serviceException);
        return new ResponseEntity<ErrorDto>(error, error.getHttpStatus());
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ErrorDto> handleServiceException(Throwable t, Locale locale) {
        ErrorDto error = new ErrorDto();
        error.setCode(ErrorCode.InternalServerError);
        String message = messageSource.getMessage("" + error.getCode(), null, "internal Error", locale);
        error.setMessage(message);
        errorHandlingService.handleError(t);
        return new ResponseEntity<ErrorDto>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
