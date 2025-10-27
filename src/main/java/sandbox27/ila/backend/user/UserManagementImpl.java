package sandbox27.ila.backend.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sandbox27.infrastructure.security.SecUser;
import sandbox27.infrastructure.security.UserManagement;

import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserManagementImpl implements UserManagement {

    final UserRepository userRepository;
    @Value("${ila.admin.user-names}")
    List<String> adminUserNames;

    @Transactional
    public User createUser(Map<String, String> userInfoAttributes){
        final String firstName = (String) userInfoAttributes.get("given_name");
        final String lastName = (String) userInfoAttributes.get("family_name");
        final String email = (String) userInfoAttributes.get("email");
        final String uuid = (String) userInfoAttributes.get("sub");
        return null;
    }

    @Override
    public Optional<?> findUserByPrincipal(String principal) {
        return userRepository.findById(principal);
    }

    @Override
    public boolean hasRole(String principal, String roleName) {
        Optional<User> userOpt = userRepository.findById(principal);
        return userOpt.isPresent() && userOpt.get().hasRole(roleName);
    }

    @Override
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
