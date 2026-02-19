package sandbox27.ila.backend.exchange.events;

import java.util.List;

/**
 * Event das ausgelöst wird, wenn ein Wechselwunsch nicht erfüllt werden konnte.
 */
public record ExchangeRequestUnfulfillableEvent(
        String studentUserName,
        String studentEmail,
        String studentFirstName,
        String currentCourseName,
        String blockName,
        String dayOfWeek,
        List<String> desiredCourseNames,
        String reason
) {}