# Frontend-Integration: Anmelden als anderer Nutzer (Impersonation)

Ermöglicht Admins, die Anwendung in der Identität eines Schülers zu benutzen – zum Testen der
Schüler-Sichten und zum Nachvollziehen gemeldeter Probleme. Backend-Klasse:
`sandbox27.ila.backend.user.ImpersonationController`.

## Idee

Die Identität hängt in iLa ausschließlich am **Subject des JWT**. Der Endpoint stellt ein Token aus,
dessen Subject der übernommene Nutzer ist – Backend und Frontend verhalten sich danach exakt so wie
bei dessen eigenem Login. Es gibt keinen Sondermodus, keine abweichenden Codepfade.

```
Admin-Token  ──POST /impersonate {userName}──►  Schüler-Token (1h gültig)  ──►  normale App-Nutzung
```

## Endpoint

```
POST /impersonate
Authorization: Bearer <jwt>   // ADMIN
Content-Type: application/json

{ "userName": "max.mustermann" }
```

Der Login wird vor der Suche getrimmt und kleingeschrieben.

**Antwort:** `200 OK` – dieselbe Struktur wie `POST /login` und `POST /auth`:

```json
{
  "token": "<jwt>",
  "user": {
    "authentication_type": "internal",
    "username": "max.mustermann",
    "preferred_username": "max.mustermann",
    "name": "Max Mustermann",
    "given_name": "Max",
    "family_name": "Mustermann",
    "email": "max.mustermann@jmoosdorf.de",
    "impersonatedBy": "a.krebs"
  },
  "roles": ["STUDENT"]
}
```

`impersonatedBy` ist das einzige zusätzliche Feld – gedacht für einen Hinweisbalken in der UI.

## Fehler

| Situation                       | ErrorCode                  | Meldung                                                  |
|---------------------------------|----------------------------|----------------------------------------------------------|
| kein/abgelaufenes Token         | –                          | `401` vom `JwtFilter`                                     |
| Aufrufer ist kein Admin         | `RoleRequired`             | Du musst die Rolle Admin haben, …                         |
| `userName` fehlt oder ist leer  | `FieldRequired`            | Login ist ein Pflichtfeld                                 |
| Login existiert nicht           | `UserNotFound`             | Nutzer {0} konnte nicht gefunden werden.                  |
| Ziel ist selbst Admin           | `ImpersonationNotAllowed`  | Administrator:innen können nicht übernommen werden.       |

Admins als Ziel sind gesperrt, damit Impersonation kein Weg ist, sich Adminrechte zu verschaffen
oder zu verschleiern, wer eine administrative Aktion ausgelöst hat.

## Was das Frontend tun muss

1. **Auslösen:** Button „Anmelden als" in der Benutzerliste (`GET /users`), nur für Admins sichtbar.
2. **Aktuelles Admin-Token sichern**, bevor das neue gesetzt wird – z.B. unter einem zweiten
   Storage-Key (`adminToken`, `adminUser`). Sonst gibt es keinen Rückweg außer Neu-Login.
3. **Token wie nach einem Login übernehmen**: `token`, `user` und `roles` ersetzen, danach in die
   Startseite navigieren. Vorher sollte alles verworfen werden, was noch zum Admin gehört
   (Caches, Query-Client, In-Memory-Stores) – sonst mischen sich die Sichten.
4. **Hinweisbalken** anzeigen, solange `user.impersonatedBy` gesetzt ist, mit Button „Zurück zum
   Admin-Konto": gesichertes Admin-Token zurückschreiben, Caches leeren, neu laden.
5. **Ablauf behandeln:** Das Impersonation-Token ist **1 Stunde** gültig (reguläre Login-Tokens
   laufen nicht ab). Danach antwortet jeder Request mit `401`. Sinnvoll ist, in diesem Fall
   automatisch auf das gesicherte Admin-Token zurückzufallen statt zum Login-Screen zu springen.

## Hinweise

- Der Pfad liegt bewusst **nicht** unter `/login` oder `/auth` – diese Präfixe überspringt der
  `JwtFilter`, der Endpoint braucht aber ein gültiges Admin-Token.
- Aktionen in der übernommenen Sitzung sind für das Backend nicht von Aktionen des Schülers zu
  unterscheiden – Präferenzen, Wechselwünsche und Abwesenheiten werden **echt** geschrieben. Zum
  Testen von schreibenden Flows auf Produktivdaten ist das mit Bedacht zu benutzen.
- Jede Übernahme wird serverseitig auf `WARN` protokolliert:
  `Impersonation: <admin> übernimmt die Sitzung von <schüler>`.
