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
 * Ruft Abwesenheitsmeldungen ab und handhabt die Paginierung automatisch.
 */
@Component
@Slf4j
public class BesteSchuleClient {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiToken;

    public BesteSchuleClient(
            @Value("${besteschule.api.url:https://beste.schule/api}") String apiUrl,
            @Value("${besteschule.api.token:}") String apiToken
    ) {
        this.restTemplate = new RestTemplate();
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
    }

    /**
     * Ruft alle Abwesenheiten ab (mit automatischer Paginierung).
     * Achtung: Kann viele Seiten abrufen - nur für initiale Imports verwenden.
     */
    public List<AbsenceResponse> fetchAllAbsences() {
        return fetchAbsencesWithPagination(null);
    }

    /**
     * Ruft Abwesenheiten ab, die nach einem bestimmten Zeitpunkt erfasst wurden.
     * Die Beste.Schule API unterstützt möglicherweise Filter-Parameter.
     * Falls nicht, werden alle abgerufen und lokal gefiltert.
     */
    public List<AbsenceResponse> fetchAbsencesWithPagination(String additionalParams) {
        if (apiToken == null || apiToken.isBlank()) {
            log.warn("Beste.Schule API-Token nicht konfiguriert - Sync übersprungen");
            return Collections.emptyList();
        }

        List<AbsenceResponse> allAbsences = new ArrayList<>();
        int page = 1;
        int maxPages = 100; // Sicherheitslimit

        try {
            while (page <= maxPages) {
                String url = buildUrl(page, additionalParams);
                log.debug("Rufe Beste.Schule API ab: Seite {}", page);

                ResponseEntity<AbsencePageResponse> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        createRequestEntity(),
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

                // Paginierung prüfen
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

    private String buildUrl(int page, String additionalParams) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(apiUrl + "/absences")
                .queryParam("page", page);

        if (additionalParams != null && !additionalParams.isBlank()) {
            // Zusätzliche Parameter anhängen (z.B. Filter)
            builder.query(additionalParams);
        }

        return builder.toUriString();
    }

    private HttpEntity<Void> createRequestEntity() {
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
                    createRequestEntity(),
                    AbsencePageResponse.class
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.warn("Beste.Schule API nicht erreichbar: {}", e.getMessage());
            return false;
        }
    }
}
