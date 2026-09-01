# Frontend-Integration: Vergabelauf verwerfen

Gegenstück zum Start des Zuweisungsalgorithmus. Backend-Klassen:
`sandbox27.ila.backend.assignments.algorithm.CourseAssignmentController` /
`CourseAssignmentService.deleteAlgorithmicAssignments`.

## Idee

Ein versehentlich oder mit falschen Vorgaben gestarteter Vergabelauf soll rückgängig gemacht werden
können, **ohne die von Hand gesetzten Zuweisungen zu verlieren**.

Die Unterscheidung steckt im Feld `preset` der Zuweisung:

| Herkunft                                                        | `preset` | wird gelöscht |
|-----------------------------------------------------------------|----------|---------------|
| Zuweisungsalgorithmus (`POST /periods/{id}/assign-courses`)      | `false`  | ja            |
| Handzuweisung (`POST /assignments`)                              | `true`   | **nein**      |
| Übernahme aus anderem Kurs (`POST /assignments/copy-assignments`)| `true`   | **nein**      |

Gelöscht wird also exakt die Menge, die ein erneuter Lauf ohnehin ersetzen würde.

```
POST   /periods/7/assign-courses   ──►  Lauf erzeugt Zuweisungen (preset=false)
DELETE /periods/7/assign-courses   ──►  genau diese wieder weg, Handzuweisungen bleiben
```

## Endpoint

```
DELETE /periods/{periodId}/assign-courses
Authorization: Bearer <jwt>   // ADMIN
```

Kein Request-Body, keine Query-Parameter.

**Antwort:** `200 OK`, Struktur `Feedback` – dieselbe wie bei den `/assignments`-Endpunkten:

```json
{
  "info": ["412 automatisch erzeugte Zuweisungen gelöscht. Manuell gesetzte Zuweisungen bleiben bestehen."],
  "warnings": [],
  "errors": []
}
```

Die Zahl in `info` ist die einzige Rückmeldung über den Umfang – gab es nichts zu löschen, steht
dort `0`, der Aufruf ist trotzdem erfolgreich (idempotent).

## Fehler

Fehlerantworten haben durchgehend die Form `{ "code": "...", "message": "..." }`. Der
`message`-Text ist bereits deutsch und anzeigbar.

| Situation                       | HTTP | `code`                       |
|---------------------------------|------|------------------------------|
| kein/abgelaufenes Token         | 401  | –                            |
| Aufrufer ist kein Admin         | 403  | `RoleRequired`               |
| Phase existiert nicht           | 404  | `NotFound`                   |
| Lauf ist bereits final          | 409  | `AssignmentsAlreadyFinalized`|

`AssignmentsAlreadyFinalized` ist der einzige fachliche Sonderfall: Sobald ein Lauf der Phase als
final markiert wurde, haben die Schüler ihre Kurse per Mail bekommen – ein stilles Entfernen wäre
nicht nachvollziehbar. Der Button sollte in diesem Zustand gar nicht erst anklickbar sein
(siehe unten), die 409 ist die Absicherung dahinter.

Generell gilt für dieses Backend: **auf `code` prüfen, nicht auf den HTTP-Status.** Viele fachliche
Fehler kommen aus historischen Gründen mit `500` zurück; die vier Fälle oben sind korrekt gemappt.

## Was das Frontend tun muss

1. **Platzierung:** In der Vergabe-Ansicht der Phase, neben dem Button, der den Lauf startet – am
   besten in der Lauf-Historie (`GET /periods/{periodId}/assign-courses/history`).
2. **Sichtbarkeit:** Nur für Admins, und nur wenn die Historie mindestens einen Eintrag hat.
   Ausgrauen, sobald ein Eintrag `finalized: true` ist.
3. **Bestätigungsdialog – zwingend.** Der Aufruf ist nicht umkehrbar. Der Dialog sollte klar sagen,
   was passiert und was nicht, z.B.:
   > Alle automatisch erzeugten Zuweisungen dieser Phase werden gelöscht. Von Hand gesetzte
   > Zuweisungen bleiben erhalten. Offene Wechselwünsche zu den gelöschten Zuweisungen entfallen.
   > Dieser Schritt kann nicht rückgängig gemacht werden.
4. **Nach Erfolg:** `info[0]` als Bestätigung anzeigen und die Ansicht neu laden – mindestens die
   Zuweisungslisten und, falls sichtbar, die Problemliste (`GET /problems?period-id=…`).
5. **Historie nicht automatisch mit aufräumen.** Die Einträge der Läufe bleiben stehen; ihre Zahlen
   beziehen sich dann auf einen Stand, den es nicht mehr gibt. Wenn das stört, kann der Eintrag
   separat über die Historie gelöscht werden – das ist ein eigener Aufruf, kein Automatismus.

## Hinweise

- **Wechselwünsche werden mitgelöscht.** Ein Wechselwunsch zeigt auf die Zuweisung, die er ersetzen
  soll; verschwindet sie, verschwindet auch der Wunsch. Deshalb steht das im Bestätigungstext.
  Handzuweisungen und die daran hängenden Wechselwünsche sind nicht betroffen.
- **Anwesenheiten bleiben unberührt** – sie hängen am Kurs, nicht an der Zuweisung.
- Ein erneuter Lauf (`POST` auf denselben Pfad) löscht die automatischen Zuweisungen ohnehin selbst.
  Das Löschen vorweg ist also nur nötig, wenn man die Phase *ohne* neue Vergabe zurücksetzen will.
- Das Ergebnis eines erneuten Laufs ist **nicht** identisch mit dem vorherigen: Die
  Optimierungsphase arbeitet mit Zufallstauschen. Wer im ersten Lauf welchen Kurs hatte, ist danach
  hinfällig – auch das gehört in den Bestätigungstext, wenn der Button „löschen und neu rechnen"
  kombiniert wird.
