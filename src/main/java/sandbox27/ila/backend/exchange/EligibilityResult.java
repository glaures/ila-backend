package sandbox27.ila.backend.exchange;

import lombok.Getter;

@Getter
public class EligibilityResult {

    private final boolean eligible;
    private final String reason;
    /**
     * Wenn true, soll der Kurs gar nicht in der Liste angezeigt werden
     * (z.B. falsche Klassenstufe, falsches Geschlecht)
     */
    private final boolean excludeFromList;
    /**
     * Optionale Warnung (z.B. "Kurs ist aktuell voll") - Kurs ist trotzdem wählbar
     */
    private final String warning;

    private EligibilityResult(boolean eligible, String reason, boolean excludeFromList, String warning) {
        this.eligible = eligible;
        this.reason = reason;
        this.excludeFromList = excludeFromList;
        this.warning = warning;
    }

    public static EligibilityResult eligible() {
        return new EligibilityResult(true, null, false, null);
    }

    /**
     * Kurs ist wählbar, aber mit einer Warnung (z.B. "Kurs ist aktuell voll")
     */
    public static EligibilityResult eligibleWithWarning(String warning) {
        return new EligibilityResult(true, null, false, warning);
    }

    /**
     * Kurs ist nicht wählbar und wird in der Liste angezeigt mit Grund
     */
    public static EligibilityResult ineligible(String reason) {
        return new EligibilityResult(false, reason, false, null);
    }

    /**
     * Kurs soll gar nicht in der Liste erscheinen (harter Ausschluss)
     */
    public static EligibilityResult excluded(String reason) {
        return new EligibilityResult(false, reason, true, null);
    }

    public boolean isIneligible() {
        return !eligible;
    }

    public boolean hasWarning() {
        return warning != null && !warning.isEmpty();
    }
}