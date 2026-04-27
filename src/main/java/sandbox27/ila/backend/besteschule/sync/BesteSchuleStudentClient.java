package sandbox27.ila.backend.besteschule.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import sandbox27.ila.backend.besteschule.sync.BesteSchuleStudentDto.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * REST-Client für den Beste.Schule /students Endpunkt.
 * Verwendet den Lese-Token (besteschule.api.token).
 */
@Component
@Slf4j
public class BesteSchuleStudentClient {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiToken;

    public BesteSchuleStudentClient(
            @Value("${besteschule.api.url:https://beste.schule/api}") String apiUrl,
            @Value("${besteschule.api.token:}") String apiToken
    ) {
        this.restTemplate = new RestTemplate();
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
    }

    /**
     * Ruft alle Schüler von Beste.Schule ab (mit automatischer Paginierung).
     */
    public List<StudentResponse> fetchAllStudents() {
        if (apiToken == null || apiToken.isBlank()) {
            log.warn("Beste.Schule API-Token nicht konfiguriert - Student-Sync übersprungen");
            return Collections.emptyList();
        }

        List<StudentResponse> allStudents = new ArrayList<>();
        int page = 1;
        int maxPages = 100;

        try {
            while (page <= maxPages) {
                String url = apiUrl + "/students?page=" + page;
                log.debug("Rufe Beste.Schule Students API ab: Seite {}", page);

                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(apiToken);
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<StudentPageResponse> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        StudentPageResponse.class
                );

                if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                    log.error("Fehler bei Beste.Schule Students API: Status {}", response.getStatusCode());
                    break;
                }

                StudentPageResponse pageResponse = response.getBody();
                if (pageResponse.data() != null) {
                    allStudents.addAll(pageResponse.data());
                }

                MetaResponse meta = pageResponse.meta();
                if (meta == null || meta.currentPage() >= meta.lastPage()) {
                    break;
                }

                page++;
            }

            log.info("Beste.Schule: {} Schüler von {} Seiten abgerufen", allStudents.size(), page);

        } catch (RestClientException e) {
            log.error("Fehler beim Abrufen der Beste.Schule Students API: {}", e.getMessage(), e);
        }

        return allStudents;
    }
}
