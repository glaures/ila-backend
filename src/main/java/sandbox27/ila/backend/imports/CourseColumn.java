package sandbox27.ila.backend.imports;

import java.util.List;

/**
 * Canonical columns we recognize in a planning sheet. The concrete spreadsheets from the
 * schools use varying header texts (e.g. "Angebot 1: Titel" vs. "Titel" vs. "Name"), so the
 * parser maps each real header onto one of these via {@link #match(String)}.
 * <p>
 * Columns that the export writes back out carry their canonical header text in
 * {@link #getExportHeader()}; {@link #EXPORT_COLUMNS} defines the layout of an exported sheet.
 * Import and export therefore share one definition and cannot drift apart.
 */
public enum CourseColumn {
    NACHNAME("Nachname"),
    VORNAME("Vorname"),
    KURSLEITER(null),   // combined "Vorname Nachname" used by the clean import template
    KURS_ID("Kurs-ID"),
    TITEL("Titel"),
    BESCHREIBUNG("Beschreibung"),
    ZIELSTELLUNG(null), // merged into the description on import, so never written separately
    MAX_ATTENDEES("maximale Teilnehmerzahl"),
    MIN_ATTENDEES(null),
    KLASSEN("Klassenstufen"),
    KATEGORIE("Kategorie"),
    RAUM("Raum"),
    WOCHENTAG("Wochentag"),
    ZEITSCHIENE("Zeitschiene"), // start time of the slot
    ENDE(null);                 // explicit end time (only the clean template has it)

    private final String exportHeader;

    CourseColumn(String exportHeader) {
        this.exportHeader = exportHeader;
    }

    /**
     * Header text written by the export, or {@code null} for columns that are only ever read.
     * Every non-null value must map back onto its own column via {@link #match(String)} –
     * that is what keeps a round-trip export → import working.
     */
    public String getExportHeader() {
        return exportHeader;
    }

    /**
     * Column layout of an exported sheet, in order. Only columns the import actually consumes are
     * written, so an exported file can be re-imported without touching the layout.
     */
    public static final List<CourseColumn> EXPORT_COLUMNS = List.of(
            KURS_ID, TITEL, BESCHREIBUNG, KATEGORIE, KLASSEN, MAX_ATTENDEES,
            RAUM, WOCHENTAG, ZEITSCHIENE, VORNAME, NACHNAME);

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
