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
     * @param request die Abwesenheitsdaten
     * @return true wenn erfolgreich (HTTP 200 oder 201), false bei Fehler
     */
    public boolean createAbsence(CreateAbsenceRequest request) {
        if (absenceWriteToken == null || absenceWriteToken.isBlank()) {
            log.warn("Beste.Schule Absence-Write-Token nicht konfiguriert " +
                    "(besteschule.api.absence-write-token) - Abwesenheit kann nicht eingetragen werden");
            return false;
        }

        String url = apiUrl + "/absences";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(absenceWriteToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<CreateAbsenceRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                log.info("Abwesenheit erfolgreich in Beste.Schule eingetragen für Student-ID {}",
                        request.studentId());
                return true;
            } else {
                log.error("Unerwarteter Status beim Eintragen der Abwesenheit in Beste.Schule: {} - Body: {}",
                        response.getStatusCode(), response.getBody());
                return false;
            }

        } catch (RestClientException e) {
            log.error("Fehler beim Eintragen der Abwesenheit in Beste.Schule für Student-ID {}: {}",
                    request.studentId(), e.getMessage(), e);
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