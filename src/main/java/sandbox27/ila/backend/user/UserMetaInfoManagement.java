package sandbox27.ila.backend.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import sandbox27.infrastructure.security.UserManagement;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserMetaInfoManagement {

    @Value("${iserv.api.username}")
    String apiUsername;
    @Value("${iserv.api.key}")
    String apiKey;
    @Value("${iserv.groups.students.id}")
    String studentsGroupId;
    @Value("${iserv.groups.teachers.id}")
    String teachersGroupId;
    @Value("${iserv.groups.ila_members.id}")
    String ilaMembersGroupId;
    @Value("${iserv.groups.ila_instructors.id}")
    String ilaInstructorsGroupId;

    private static final String ISERV_API_BASE_URL = "https://idm.jmoosdorf.de/iserv/idm/api/v1";
    
    final UserManagement userManagement;
    final UserRepository userRepository;

    @Transactional
    public void syncUsers() {
        log.info("Starting user synchronization from IServ");
        
        try {
            // Sync students
            syncGroupUsers(ilaMembersGroupId, "students");
            syncGroupUsers(ilaInstructorsGroupId, "instructors");
            log.info("User synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during user synchronization", e);
            throw new RuntimeException("Failed to sync users from IServ", e);
        }
    }
    
    private void syncGroupUsers(String groupId, String groupName) {
        boolean instructors = groupName.contains("instructors");
        log.info("Syncing users from group: {} (ID: {})", groupName, groupId);
        List<IServUser> iservUsers = fetchUsersFromIServ(groupId);
        log.info("Found {} users in group {}", iservUsers.size(), groupName);
        
        int updatedCount = 0;
        int notFoundCount = 0;
        
        for (IServUser iservUser : iservUsers) {
            try {
                Optional<User> userOptional = userRepository.findById(iservUser.user());
                
                if (userOptional.isPresent()) {
                    User user = userOptional.get();
                    boolean updated = false;

                    // Update ila role
                    Role role = instructors ? Role.COURSE_INSTRUCTOR : Role.STUDENT;
                    if(!user.getRoles().contains(role)) {
                        user.getRoles().add(role);
                        updated = true;
                    }

                    // Update first name
                    if (!iservUser.firstname().equals(user.getFirstName())) {
                        log.debug("Updating first name for user {}: {} -> {}", 
                                user.getUserName(), user.getFirstName(), iservUser.firstname());
                        user.setFirstName(iservUser.firstname());
                        updated = true;
                    }
                    
                    // Update last name
                    if (!iservUser.lastname().equals(user.getLastName())) {
                        log.debug("Updating last name for user {}: {} -> {}", 
                                user.getUserName(), user.getLastName(), iservUser.lastname());
                        user.setLastName(iservUser.lastname());
                        updated = true;
                    }
                    
                    // Update internal ID
                    if (iservUser.importId() != null && !iservUser.importId().equals(user.getInternalId())) {
                        log.debug("Updating internal ID for user {}: {} -> {}", 
                                user.getUserName(), user.getInternalId(), iservUser.importId());
                        user.setInternalId(iservUser.importId());
                        updated = true;
                    }
                    
                    // Extract and update grade from auxInfo
                    Integer grade = extractGradeFromAuxInfo(iservUser.auxInfo());
                    if (grade != null && !grade.equals(user.getGrade())) {
                        log.debug("Updating grade for user {}: {} -> {}", 
                                user.getUserName(), user.getGrade(), grade);
                        user.setGrade(grade);
                        updated = true;
                    }

                    if (updated) {
                        userRepository.save(user);
                        updatedCount++;
                        log.info("Updated user: {}", user.getUserName());
                    }
                } else {
                    notFoundCount++;
                    log.warn("User {} from IServ not found in local database", iservUser.user());
                }
            } catch (Exception e) {
                log.error("Error processing user {}", iservUser.user(), e);
            }
        }
        
        log.info("Group {} sync completed: {} users updated, {} users not found", 
                groupName, updatedCount, notFoundCount);
    }
    
    private List<IServUser> fetchUsersFromIServ(String groupId) {
        RestTemplate restTemplate = new RestTemplate();
        
        String url = String.format("%s/roles/%s/users?_attributes=importId,firstname,lastname,user,auxInfo", 
                ISERV_API_BASE_URL, groupId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-IServ-Authentication", apiKey);
        headers.set(HttpHeaders.ACCEPT, "application/json");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<List<IServUser>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<IServUser>>() {}
            );
            
            List<IServUser> body = response.getBody();
            return body != null ? body : List.of();
        } catch (RestClientException e) {
            log.error("Error fetching users from IServ for group {}", groupId, e);
            return List.of();
        }
    }
    
    /**
     * Extracts the grade (Klassenstufe) from the auxInfo field.
     * The auxInfo format starts with the grade number, e.g., "10_1", "10a", "11" for grade 10, 11.
     * This method extracts all leading digits from the string.
     * 
     * @param auxInfo the auxInfo string from IServ
     * @return the extracted grade, or null if extraction fails
     */
    private Integer extractGradeFromAuxInfo(String auxInfo) {
        if (auxInfo == null || auxInfo.isEmpty()) {
            return null;
        }
        
        try {
            // Extract all leading digits
            StringBuilder gradeStr = new StringBuilder();
            for (char c : auxInfo.toCharArray()) {
                if (Character.isDigit(c)) {
                    gradeStr.append(c);
                } else {
                    break; // Stop at first non-digit character
                }
            }
            
            if (gradeStr.length() > 0) {
                return Integer.parseInt(gradeStr.toString());
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to extract grade from auxInfo: {}", auxInfo);
        }
        
        return null;
    }
    
    // Inner class for JSON mapping
    
    private record IServUser(
            String user,
            String firstname,
            String lastname,
            String importId,
            String auxInfo
    ) {}
}
