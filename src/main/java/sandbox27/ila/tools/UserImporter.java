package sandbox27.ila.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserImporter {

    private final UserRepository userRepository;

    @Transactional
    public void runImport() throws IOException {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        InputStream inputStream = new ClassPathResource("users.json").getInputStream();
        List<User> users = mapper.readValue(inputStream, new TypeReference<>() {
        });

        int inserted = 0;
        for (User user : users) {
            if (userRepository.findById(user.getId()).isEmpty()) {
                userRepository.save(user);
                inserted++;
            }
        }
        System.out.println("Import abgeschlossen. Neue Nutzer gespeichert: " + inserted);
    }
}
