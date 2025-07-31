package sandbox27.ila.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.user.GTSRole;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
@Profile("dev")
@RequiredArgsConstructor
@Order(2)
public class UserImporter {

    private final UserRepository userRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() throws IOException {
        runImport();
    }

    @Transactional
    public void runImport() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("gts_users.json").getInputStream();
        List<User> users = mapper.readValue(inputStream, new TypeReference<>() {});

        // testuser
        User testUser = new User();
        testUser.setEmail("testschueler@jmoosdorf.de");
        testUser.setFirstName("Test");
        testUser.setLastName("Schüler");
        testUser.setGrade(8);
        testUser.setGtsRoles(List.of(GTSRole.student));
        testUser.setGtsId("jm--1");
        users.add(testUser);

        int inserted = 0;
        for (User user : users) {
            if (userRepository.findByGtsId(user.getGtsId()).isEmpty()) {
                userRepository.save(user);
                inserted++;
            }
        }
        System.out.println("Import abgeschlossen. Neue Nutzer gespeichert: " + inserted);
    }
}
