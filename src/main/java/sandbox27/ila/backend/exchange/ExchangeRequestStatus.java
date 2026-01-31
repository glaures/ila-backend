package sandbox27.ila.backend.exchange;

public enum ExchangeRequestStatus {
    /**
     * Wunsch wurde abgegeben, wartet auf Batch-Auflösung
     */
    PENDING,

    /**
     * Wunsch wurde erfüllt (einer der gewünschten Kurse wurde zugewiesen)
     */
    FULFILLED,

    /**
     * Keiner der gewünschten Kurse konnte zugewiesen werden
     */
    UNFULFILLABLE,

    /**
     * Wechselphase ist abgelaufen ohne Auflösung
     */
    EXPIRED,

    /**
     * Vom Schüler zurückgezogen
     */
    WITHDRAWN
}