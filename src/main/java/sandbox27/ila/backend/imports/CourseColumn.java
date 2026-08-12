package sandbox27.ila.backend.imports;

/**
 * Canonical columns we recognize in a planning sheet. The concrete spreadsheets from the
 * schools use varying header texts (e.g. "Angebot 1: Titel" vs. "Titel" vs. "Name"), so the
 * parser maps each real header onto one of these via {@link #match(String)}.
 */
public enum CourseColumn {
    NACHNAME,
    VORNAME,
    KURSLEITER,     // combined "Vorname Nachname" used by the clean import template
    KURS_ID,
    TITEL,
    BESCHREIBUNG,
    ZIELSTELLUNG,
    MAX_ATTENDEES,
    MIN_ATTENDEES,
    KLASSEN,
    KATEGORIE,
    RAUM,
    WOCHENTAG,
    ZEITSCHIENE,    // start time of the slot
    ENDE;           // explicit end time (only the clean template has it)

    /**
     * Maps a raw header cell onto a canonical column, or {@code null} if it is not one we care about.
     * Input should already be normalized via {@link #normalizeHeader(String)}.
     */
    public static CourseColumn match(String normalized) {
        if (normalized == null || normalized.isBlank()) return null;
        String h = normalized;

        // Order matters: check the more specific headers before the generic ones.
        if (h.contains("nachname")) return NACHNAME;
        if (h.contains("vorname")) return VORNAME;
        if (h.contains("kursleiter") || h.contains("leitung")) return KURSLEITER;
        if (h.equals("id") || h.contains("kurs id") || h.contains("kursid")) return KURS_ID;
        if (h.contains("maximale teilnehmer") || h.contains("max teilnehmer")) return MAX_ATTENDEES;
        if (h.contains("minimale teilnehmer") || h.contains("min teilnehmer")) return MIN_ATTENDEES;
        if (h.contains("teilnehmer")) return MAX_ATTENDEES; // bare "Teilnehmerzahl" -> treat as max
        if (h.contains("titel") || h.equals("name")) return TITEL;
        if (h.contains("zielstellung")) return ZIELSTELLUNG;
        if (h.contains("beschreibung")) return BESCHREIBUNG;
        if (h.contains("klassenstufe") || h.equals("klassen") || h.equals("klasse")) return KLASSEN;
        if (h.contains("kategorie")) return KATEGORIE;
        if (h.contains("raum")) return RAUM;
        if (h.contains("wochentag")) return WOCHENTAG;
        if (h.contains("zeitschiene") || h.contains("zeitschine")
                || h.equals("start") || h.contains("startzeit") || h.equals("block")) return ZEITSCHIENE;
        if (h.equals("ende") || h.contains("endzeit")) return ENDE;
        return null;
    }

    /**
     * Lower-cases, strips a leading "Angebot N:" prefix and collapses any non-alphanumeric run to a
     * single space so header matching is robust against punctuation and the offer-number prefix.
     */
    public static String normalizeHeader(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase().trim();
        s = s.replaceFirst("^angebot\\s*\\d*\\s*:?\\s*", "");
        s = s.replaceAll("[^a-z0-9äöüß]+", " ").trim();
        return s;
    }
}
