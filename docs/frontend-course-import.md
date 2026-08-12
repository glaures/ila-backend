# Frontend-Integration: Excel-Import der Phasenplanung

Anleitung für das Frontend-Projekt zur Anbindung des Excel-basierten Kurs-Imports.
Backend-Package: `sandbox27.ila.backend.imports`.

## Idee & Ablauf

Der Admin (z.B. Herr Engel) lädt seine Excel-Planungsdatei hoch. Das Backend **validiert** die Datei
und liefert einen Report mit allen Lücken/Fehlern zurück – ohne etwas zu speichern. Der Admin bessert
die Fehler aus (u.a. fehlende Kursleiter anlegen), lädt neu hoch, und **committet** erst, wenn der
Report fehlerfrei ist.

```
┌────────────┐   validate    ┌──────────────────────┐
│  Excel     │ ────────────► │  Report anzeigen     │
│  auswählen │               │  (Fehler/Warnungen)  │
└────────────┘               └──────────┬───────────┘
      ▲                                  │ Fehler vorhanden?
      │                                  ▼
      │        ┌─────────────────────────────────────────┐
      │        │  - Excel korrigieren                     │
      └────────┤  - fehlende Kursleiter via PUT /users    │
   neu hochladen│    anlegen (mit E-Mail)                 │
               └─────────────────────────────────────────┘
                                  │ importable === true
                                  ▼
                          ┌──────────────┐
                          │   commit     │  → Kurse werden geschrieben
                          └──────────────┘
```

Wichtig: **Es gibt keinen Server-State zwischen den Aufrufen.** Jeder `validate`/`commit` verarbeitet
die komplette hochgeladene Datei neu. Der Fix-Workflow ist: Datei korrigieren → erneut hochladen.

## Voraussetzung: Blöcke müssen existieren

Die Zielphase muss ihre **Blöcke** (Wochentag + Uhrzeit) bereits besitzen. Der Import ordnet jede
Kurszeile über `Wochentag` + `Zeitschiene` (Startzeit) einem existierenden Block zu. Fehlt ein
passender Block, erscheint das als Fehler in der Zeile („Kein Block für Montag 11:20 …"). Blöcke können
vorab z.B. über `POST /blocks/copy-from-period` oder `POST /blocks` angelegt werden.

## Authentifizierung & CORS

- Alle Endpoints erfordern einen **Bearer-Token** im `Authorization`-Header (`Authorization: Bearer <jwt>`).
- Rolle **ADMIN** erforderlich.
- CORS ist für `http://localhost:3000` und die konfigurierte `cors.origin` freigegeben (Credentials erlaubt).
- Max. Upload-Größe: **10 MB**.

---

## Endpoint 1 – Validieren (Dry-Run, keine Schreibvorgänge)

```
POST /imports/courses/validate?period-id={periodId}
Content-Type: multipart/form-data
```

| Teil / Param | Ort            | Typ    | Beschreibung                          |
|--------------|----------------|--------|---------------------------------------|
| `file`       | multipart part | File   | Die `.xlsx`-Planungsdatei             |
| `period-id`  | Query-Param    | number | ID der Zielphase (Period)             |

**Antwort:** `200 OK` mit `CourseImportReport` (siehe unten). Auch bei Validierungsfehlern kommt
`200` – die Fehler stehen im Report, nicht im HTTP-Status.

## Endpoint 2 – Importieren (schreibt)

```
POST /imports/courses/commit?period-id={periodId}
Content-Type: multipart/form-data
```

Gleiche Parameter wie `validate`. Das Backend validiert erneut und schreibt **nur dann**, wenn
`importable === true` (kein einziger blockierender Fehler). Andernfalls wird nichts geschrieben und
derselbe Report mit `committed: false` zurückgegeben.

**Antwort:** `200 OK` mit `CourseImportReport`, zusätzlich gefüllt: `committed`, `createdCount`,
`updatedCount`.

---

## TypeScript-Interfaces (Response)

```ts
interface CourseImportReport {
  fileName: string | null;
  sheetName: string | null;        // Name des erkannten Datenblatts
  periodId: number;
  periodName: string;

  totalRows: number;               // Anzahl verarbeiteter Kurszeilen (ohne Pausen)
  importableRows: number;          // Zeilen ohne Fehler
  skippedRows: number;             // übersprungen (Hofpause/Mittagessen, leere/Rest-Zeilen)
  errorCount: number;              // Summe aller ERROR-Issues (Zeilen + global)
  warningCount: number;            // Summe aller WARNING-Issues

  importable: boolean;             // true = commit ist erlaubt
  committed: boolean;              // nur bei commit true, wenn geschrieben wurde
  createdCount: number;            // neu angelegte Kurse (nach commit)
  updatedCount: number;            // aktualisierte Kurse (nach commit)

  rows: CourseImportRow[];
  missingInstructors: MissingInstructor[];
  globalIssues: ImportIssue[];     // dateiweite Meldungen (z.B. "kein Datenblatt gefunden")
}

interface CourseImportRow {
  rowNumber: number;               // 1-basierte Excel-Zeilennummer (für Anzeige)
  courseId: string;
  name: string;
  description: string;
  categories: CourseCategory[];    // Enum-Codes, siehe Tabelle unten
  grades: number[];                // VK wird als 99 abgebildet
  maxAttendees: number | null;
  room: string;
  weekday: string;                 // wie im Excel ("Montag")
  timeSlot: string;                // wie im Excel ("11:20:00")
  instructorFirstName: string;
  instructorLastName: string;
  instructorUserName: string | null; // gesetzt, wenn Kursleiter gefunden wurde
  action: "CREATE" | "UPDATE" | "SKIP"; // was commit täte; SKIP = Zeile hat Fehler
  issues: ImportIssue[];
}

interface ImportIssue {
  severity: "ERROR" | "WARNING";   // ERROR blockiert den Import, WARNING nicht
  field: string;                   // z.B. "courseId", "block", "instructor", "categories"
  message: string;                 // fertige deutsche Meldung zum Anzeigen
}

interface MissingInstructor {
  firstName: string;
  lastName: string;
  courseIds: string[];             // betroffene Kurs-IDs
}

type CourseCategory = "iLa" | "KuP" | "BuE" | "FuF" | "SOL";
```

### Kategorie-Codes → Anzeigename

| Code  | Anzeigename                 |
|-------|-----------------------------|
| `iLa` | iLa                         |
| `KuP` | Kreativität und Praxis      |
| `BuE` | Bewegung und Entspannung    |
| `FuF` | Fordern und Fördern         |
| `SOL` | Selbstorganisiertes Lernen  |

---

## Fehlende Kursleiter anlegen (Teil-Flow)

Ein nicht gefundener Kursleiter ist ein **blockierender Fehler** (die Zeile bekommt ein
`issues`-Element mit `field: "instructor"`), und der Kursleiter erscheint zusätzlich gesammelt in
`report.missingInstructors`. Die Planungs-Excel enthält **keine E-Mail-Adressen**, daher muss der
Admin sie beim Anlegen eingeben.

Für jeden Eintrag in `missingInstructors` einen User anlegen über den bestehenden Endpoint:

```
PUT /users
Content-Type: application/json
Authorization: Bearer <jwt>   // ADMIN
```

```ts
interface UserPayload {
  login: string | null;        // darf null sein -> Backend leitet einen Namen ab
  firstName: string;
  lastName: string;
  email: string;               // vom Admin einzugeben
  initialRole: string;         // "COURSE_INSTRUCTOR"
}
```

Beispiel-Body:

```json
{ "login": null, "firstName": "Isa", "lastName": "Altner",
  "email": "i.altner@schule.de", "initialRole": "COURSE_INSTRUCTOR" }
```

Danach die Excel-Datei erneut an `validate` (oder direkt `commit`) senden – der Kursleiter wird nun
per Vor-/Nachname gefunden.

---

## Fehler-Antworten (HTTP ≠ 200)

Validierungsfehler kommen **im Report** (HTTP 200). Echte HTTP-Fehler haben folgendes Format:

```ts
interface ErrorDto { code: string; message: string; }
```

| Status | code                  | Ursache                                   |
|--------|-----------------------|-------------------------------------------|
| 401    | (Plaintext)           | Kein / ungültiges Token                    |
| 403    | `RoleRequired`        | Nutzer ist kein Admin                      |
| 404    | `NotFound`            | `period-id` existiert nicht                |
| 500    | `InternalServerError` | Unerwarteter Fehler                        |

Eine nicht lesbare / falsche Datei (kein `.xlsx`, kein Datenblatt) ist **kein** HTTP-Fehler, sondern
kommt als `globalIssues`-Eintrag mit `severity: "ERROR"` im Report (HTTP 200).

---

## Beispiel (fetch)

```ts
async function validateImport(file: File, periodId: number, token: string): Promise<CourseImportReport> {
  const form = new FormData();
  form.append("file", file);

  const res = await fetch(`/imports/courses/validate?period-id=${periodId}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` }, // KEIN Content-Type setzen – der Browser setzt den multipart-Boundary
    body: form,
  });

  if (!res.ok) {
    const err: ErrorDto = await res.json().catch(() => ({ code: "Unknown", message: res.statusText }));
    throw new Error(`${err.code}: ${err.message}`);
  }
  return res.json();
}

// commit ist identisch, nur der Pfad ändert sich auf /imports/courses/commit
```

> Hinweis: Bei `FormData` den `Content-Type`-Header **nicht** manuell setzen – der Browser ergänzt
> automatisch den korrekten `multipart/form-data; boundary=…`.

---

## Empfohlene UI-Zustände

1. **Phase wählen** (Dropdown der Perioden) → liefert `period-id`.
2. **Datei wählen** → `validate` aufrufen, Ladeindikator.
3. **Report anzeigen:**
   - Kopf: `sheetName`, Zähler (`totalRows`, `importableRows`, `errorCount`, `warningCount`).
   - `globalIssues` als Banner oben.
   - Tabelle der `rows`: pro Zeile `rowNumber`, `courseId`, `name`, `action`; Issues farblich
     (ERROR rot, WARNING gelb) je Feld.
   - Falls `missingInstructors` nicht leer: eigener Abschnitt mit Formular je Person (E-Mail-Eingabe →
     `PUT /users`).
4. **Commit-Button** nur aktiv, wenn `importable === true`. Nach Erfolg `committed`, `createdCount`,
   `updatedCount` anzeigen.
