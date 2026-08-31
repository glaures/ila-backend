# Frontend-Integration: Excel-Export der Kurse einer Phase

Gegenstück zum [Excel-Import](frontend-course-import.md). Backend-Package:
`sandbox27.ila.backend.exports`.

## Idee

Aus den Kursen einer bestehenden Phase wird eine **Startdatei für die nächste Phase** erzeugt. Die
Datei kann ohne Nachbearbeitung des Spaltenlayouts wieder über `POST /imports/courses/validate`
eingelesen werden – Kopfzeile und Schreibweise der Werte stammen aus derselben Definition, die der
Import-Parser zum Erkennen der Spalten benutzt (`CourseColumn.EXPORT_COLUMNS`).

```
Phase A  ──export──►  kurs-vorlage-<phase>.xlsx  ──(in Excel anpassen)──►  validate/commit auf Phase B
```

## Endpoint

```
GET /exports/courses?period-id={periodId}
Authorization: Bearer <jwt>   // ADMIN
```

| Param                  | Typ     | Pflicht | Default | Bedeutung                                             |
|------------------------|---------|---------|---------|-------------------------------------------------------|
| `period-id`            | number  | ja      | –       | Quellphase, deren Kurse exportiert werden              |
| `exclude-placeholders` | boolean | nein    | `false` | `true` = Kurse mit `placeholder=true` weglassen        |
| `only-with-block`      | boolean | nein    | `false` | `true` = nur Kurse exportieren, die an einem Block hängen |

**Antwort:** `200 OK`

```
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="kurs-vorlage-<phase>.xlsx"
```

Body = die `.xlsx`-Datei. Der Dateiname wird aus dem Phasennamen abgeleitet
(`1. Quartal 25/26` → `kurs-vorlage-1-quartal-25-26.xlsx`); das Frontend darf die Datei beim
Blob-Download auch selbst benennen.

### Fehler

| Status | code                  | Ursache                     |
|--------|-----------------------|-----------------------------|
| 401    | (Plaintext)           | Kein / ungültiges Token     |
| 403    | `RoleRequired`        | Nutzer ist kein Admin       |
| 404    | `NotFound`            | `period-id` existiert nicht |
| 500    | `InternalServerError` | Unerwarteter Fehler         |

## Inhalt der Datei

Ein Arbeitsblatt `Kursangebot`, Zeile 1 = Kopfzeile, danach eine Zeile je Kurs – sortiert nach Block
(Wochentag, Startzeit), dann Kurs-ID. Kurse ohne Block stehen am Ende.

Optik wie die Planungsdateien der Schule: grüne Kopfzeile (fett, mit Autofilter-Dropdowns über die
ganze Tabelle, beim Scrollen fixiert), abwechselnd weiß/hellgrün gestreifte Zeilen, weiße
Trennlinien, gesetzte Spaltenbreiten.

| Spalte                   | Quelle                        | Schreibweise                                  |
|--------------------------|-------------------------------|-----------------------------------------------|
| `Kurs-ID`                | `course.courseId`             | unverändert (trägt beim Re-Import die `CREATE`/`UPDATE`-Entscheidung) |
| `Titel`                  | `course.name`                 |                                               |
| `Beschreibung`           | `course.description`          | inkl. der beim Import angehängten Zielstellung |
| `Kategorie`              | `course.courseCategories`     | Codes, kommagetrennt: `iLa, KuP, BuE, FuF, SOL` |
| `Klassenstufen`          | `course.grades`               | aufsteigend, kommagetrennt; `99` → `VK`        |
| `maximale Teilnehmerzahl`| `course.maxAttendees`         | Zahl; leer, wenn `0`                           |
| `Raum`                   | `course.room`                 |                                               |
| `Wochentag`              | `block.dayOfWeek`             | `Montag`, `Dienstag`, …                        |
| `Zeitschiene`            | `block.startTime`             | `11:20:00`                                     |
| `Vorname` / `Nachname`   | `course.instructor`           | getrennte Spalten, keine E-Mail                |

Kurse **ohne Block** bzw. **ohne Kursleiter** werden mit leeren Zellen exportiert. Das ist Absicht:
beim Re-Import erscheinen sie als Fehlerzeile, die Lücke bleibt in der Vorlage sichtbar.

**Nicht enthalten:** Zuweisungen, Präferenzen, Teilnehmerlisten – die Vorlage beschreibt
ausschließlich das Kursangebot. Für die Belegungssicht gibt es `/admin/belegungen`.

## Re-Import in die nächste Phase

Die Zielphase braucht ihre **Blöcke** (Wochentag + Uhrzeit) bereits, sonst meldet der Import je Zeile
„Kein Block für Montag 11:20 …". Blöcke lassen sich mit `POST /blocks/copy-from-period` aus der
Quellphase übernehmen; dann passen Wochentag und Zeitschiene der Vorlage unverändert.

## Beispiel (fetch)

```ts
async function exportCourses(periodId: number, token: string): Promise<Blob> {
  const res = await fetch(`/exports/courses?period-id=${periodId}&exclude-placeholders=true`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({ code: "Unknown", message: res.statusText }));
    throw new Error(`${err.code}: ${err.message}`);
  }
  return res.blob();
}
```
