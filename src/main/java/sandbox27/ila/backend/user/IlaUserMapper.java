package sandbox27.ila.backend.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sandbox27.ila.infrastructure.error.ErrorCode;
import sandbox27.ila.infrastructure.security.SecToLocalUserMapper;
import sandbox27.ila.infrastructure.security.SecUser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IlaUserMapper implements SecToLocalUserMapper {

    final UserRepository userRepository;
    @Value("${ila.admin.user-names}")
    List<String> adminUserNames;

    @Transactional
    public Optional<SecUser> map(Map userInfoAttributes) {
        String iServId = (String) userInfoAttributes.get("preferred_username");
        Optional<User> userOpt = userRepository.findById(iServId);
        if (adminUserNames.contains(iServId)) {
            final String firstName = (String) userInfoAttributes.get("given_name");
            final String lastName = (String) userInfoAttributes.get("family_name");
            User adminUser = userOpt.orElseGet(() -> {
                // create user account for admin
                User res = new User();
                res.setUserName(iServId);
                res.setFirstName(firstName != null ? firstName : "");
                res.setLastName(lastName != null ? lastName : "");
                return res;
            });
            if (!adminUser.getRoles().contains(Role.ADMIN))
                adminUser.getRoles().add(Role.ADMIN);
            userRepository.save(adminUser);
            return Optional.of(adminUser);
        }
        ;
        return userOpt.isPresent()
                ? Optional.of(userOpt.get())
                : Optional.empty();
    }
}
