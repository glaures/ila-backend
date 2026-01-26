package sandbox27.ila.backend.imports.besteschule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sandbox27.ila.backend.user.Gender;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
public class BesteSchuleImportService {
    
    private static final Logger log = LoggerFactory.getLogger(BesteSchuleImportService.class);
    
    private final UserRepository userRepository;
    
    public BesteSchuleImportService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Transactional
    public ImportResult importUsers(List<BesteSchuleUserDto> users) {
        ImportResult result = new ImportResult();
        result.setTotal(users.size());
        
        for (BesteSchuleUserDto dto : users) {
            try {
                processUser(dto, result);
            } catch (Exception e) {
                log.error("Error processing user with importID {}: {}", dto.importID(), e.getMessage(), e);
                result.incrementErrors();
                result.addErrorMessage("Fehler bei User " + dto.importID() + ": " + e.getMessage());
            }
        }
        
        log.info("Import completed: {} total, {} updated, {} not found, {} errors", 
                result.getTotal(), result.getUpdated(), result.getNotFound(), result.getErrors());
        
        return result;
    }
    
    private void processUser(BesteSchuleUserDto dto, ImportResult result) {
        if (dto.importID() == null || dto.importID().isEmpty()) {
            log.warn("Skipping user without importID: {} {}", dto.firstName(), dto.lastName());
            result.incrementErrors();
            result.addErrorMessage("User ohne importID übersprungen: " + dto.firstName() + " " + dto.lastName());
            return;
        }
        
        Optional<User> userOptional = userRepository.findByInternalId(dto.importID());
        
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            Gender gender = parseGender(dto.gender());
            user.setGender(gender);
            user = userRepository.save(user);
            result.incrementUpdated();
            log.debug("Updated gender for user {}: {}", user.getUserName(), gender);
        } else {
            result.incrementNotFound();
            log.debug("User not found with importID: {}", dto.importID());
        }
    }
    
    private Gender parseGender(String genderString) {
        if (genderString == null || genderString.isEmpty()) {
            return Gender.unknown;
        }
        
        return switch (genderString.toLowerCase()) {
            case "male" -> Gender.male;
            case "female" -> Gender.female;
            case "diverse" -> Gender.diverse;
            default -> {
                log.warn("Unknown gender value: {}, defaulting to unknown", genderString);
                yield Gender.unknown;
            }
        };
    }
}
