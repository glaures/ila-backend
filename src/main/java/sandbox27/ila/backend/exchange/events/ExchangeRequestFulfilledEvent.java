package sandbox27.ila.backend.exchange.events;

/**
 * Event das ausgelöst wird, wenn ein Wechselwunsch erfolgreich erfüllt wurde.
 */
public record ExchangeRequestFulfilledEvent(
        String studentUserName,
        String studentEmail,
        String studentFirstName,
        String oldCourseName,
        String oldBlockName,
        String oldDayOfWeek,
        String newCourseName,
        String newBlockName,
        String newDayOfWeek
) {}