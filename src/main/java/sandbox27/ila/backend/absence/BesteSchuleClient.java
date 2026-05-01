package sandbox27.ila.backend.absence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import sandbox27.ila.backend.absence.BesteSchuleDto.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * REST-Client für die Beste.Schule API.
 * Verwendet zwei separate Tokens:
 * - apiToken: für lesende Zugriffe (Abwesenheiten abrufen/Sync)
 * - absenceWriteToken: für schreibende Zugriffe (Abwesenheiten eintragen)
 */
@Component
@Slf4j
public class BesteSchuleClient {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiToken;
    private final String absenceWriteToken;

    public BesteSchuleClient(
            @Value("${besteschule.api.url:https://beste.schule/api}") String apiUrl,
            @Value("${besteschule.api.token:}") String apiToken,
            @Value("${besteschule.api.absence-write-token:}") String absenceWriteToken
    ) {
        this.restTemplate = new RestTemplate();
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
        this.absenceWriteToken = absenceWriteToken;
    }

    /**
     * Ruft alle Abwesenheiten ab (mit automatischer Paginierung).
     * Verwendet den Lese-Token (apiToken).
     */
    public List<AbsenceResponse> fetchAllAbsences() {
        return fetchAbsencesWithPagination(null);
    }

    /**
     * Ruft Abwesenheiten ab, die nach einem bestimmten Zeitpunkt erfasst wurden.
     */
    public List<AbsenceResponse> fetchAbsencesWithPagination(String additionalParams) {
        if (apiToken == null || apiToken.isBlank()) {
            log.warn("Beste.Schule API-Token nicht konfiguriert - Sync übersprungen");
            return Collections.emptyList();
        }

        List<AbsenceResponse> allAbsences = new ArrayList<>();
        int page = 1;
        int maxPages = 100;

        try {
            while (page <= maxPages) {
                String url = buildUrl(page, additionalParams);
                log.debug("Rufe Beste.Schule API ab: Seite {}", page);

                ResponseEntity<AbsencePageResponse> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        createReadRequestEntity(),
                        AbsencePageResponse.class
                );

                if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                    log.error("Fehler bei Beste.Schule API: Status {}", response.getStatusCode());
                    break;
                }

                AbsencePageResponse pageResponse = response.getBody();
                if (pageResponse.data() != null) {
                    allAbsences.addAll(pageResponse.data());
                }

                MetaResponse meta = pageResponse.meta();
                if (meta == null || meta.currentPage() >= meta.lastPage()) {
                    break;
                }

                page++;
            }

            log.info("Beste.Schule: {} Abwesenheiten von {} Seiten abgerufen", allAbsences.size(), page);

        } catch (RestClientException e) {
            log.error("Fehler beim Abrufen der Beste.Schule API: {}", e.getMessage(), e);
        }

        return allAbsences;
    }

    /**
     * Trägt eine Abwesenheit in Beste.Schule ein.
     * Verwendet den Schreib-Token (absenceWriteToken).
     *
     * @return Optional mit der von Beste.Schule vergebenen Absence-ID
     *         oder Optional.empty() bei Fehler / fehlendem Token.
     */
    public Optional<Long> createAbsence(CreateAbsenceRequest request) {
        if (absenceWriteToken == null || absenceWriteToken.isBlank()) {
            log.warn("Beste.Schule Absence-Write-Token nicht konfiguriert " +
                    "(besteschule.api.absence-write-token) - Abwesenheit kann nicht eingetragen werden");
            return Optional.empty();
        }

        String url = apiUrl + "/absences";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(absenceWriteToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<CreateAbsenceRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<CreateAbsenceResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    CreateAbsenceResponse.class
            );

            HttpStatusCode status = response.getStatusCode();
            if (status != HttpStatus.OK && status != HttpStatus.CREATED) {
                log.error("Unerwarteter Status beim Eintragen der Abwesenheit in Beste.Schule: {} - Body: {}",
                        status, response.getBody());
                return Optional.empty();
            }

            CreateAbsenceResponse body = response.getBody();
            Long id = body != null ? body.resolvedId() : null;
            if (id == null) {
                log.error("Beste.Schule lieferte erfolgreichen Status, aber keine Absence-ID. " +
                        "Antwort: {} — bitte Response-Struktur prüfen.", body);
                return Optional.empty();
            }

            log.info("Abwesenheit erfolgreich in Beste.Schule eingetragen für Student-ID {} (Absence-ID {})",
                    request.studentId(), id);
            return Optional.of(id);

        } catch (RestClientException e) {
            log.error("Fehler beim Eintragen der Abwesenheit in Beste.Schule für Student-ID {}: {}",
                    request.studentId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Löscht eine Abwesenheit in Beste.Schule.
     * Verwendet den Schreib-Token (absenceWriteToken).
     *
     * @return true bei Erfolg (2xx oder 404 = bereits weg), false sonst.
     */
    public boolean deleteAbsence(long absenceId) {
        if (absenceWriteToken == null || absenceWriteToken.isBlank()) {
            log.warn("Beste.Schule Absence-Write-Token nicht konfiguriert - " +
                    "Abwesenheit {} kann nicht gelöscht werden", absenceId);
            return false;
        }

        String url = apiUrl + "/absences/" + absenceId;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(absenceWriteToken);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    String.class
            );

            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful()) {
                log.info("Abwesenheit {} in Beste.Schule gelöscht", absenceId);
                return true;
            }
            if (status == HttpStatus.NOT_FOUND) {
                log.warn("Abwesenheit {} in Beste.Schule nicht (mehr) vorhanden — gilt als gelöscht", absenceId);
                return true;
            }
            log.error("Unerwarteter Status beim Löschen der Abwesenheit {} in Beste.Schule: {} - Body: {}",
                    absenceId, status, response.getBody());
            return false;

        } catch (RestClientException e) {
            log.error("Fehler beim Löschen der Abwesenheit {} in Beste.Schule: {}",
                    absenceId, e.getMessage(), e);
            return false;
        }
    }

    private String buildUrl(int page, String additionalParams) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(apiUrl + "/absences")
                .queryParam("page", page);

        if (additionalParams != null && !additionalParams.isBlank()) {
            builder.query(additionalParams);
        }

        return builder.toUriString();
    }

    /**
     * Request-Entity für lesende Zugriffe (mit apiToken).
     */
    private HttpEntity<Void> createReadRequestEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

    /**
     * Prüft, ob die API erreichbar ist
     */
    public boolean isApiAvailable() {
        if (apiToken == null || apiToken.isBlank()) {
            return false;
        }

        try {
            String url = apiUrl + "/absences?page=1";
            ResponseEntity<AbsencePageResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    createReadRequestEntity(),
                    AbsencePageResponse.class
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.warn("Beste.Schule API nicht erreichbar: {}", e.getMessage());
            return false;
        }
    }
}