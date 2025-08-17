package sandbox27.ila.backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import sandbox27.ila.infrastructure.security.SecToLocalUserMapper;
import sandbox27.ila.infrastructure.security.SecUser;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IlaUserMapper implements SecToLocalUserMapper {

    final UserRepository userRepository;

    public Optional<SecUser> map(Map userInfoAttributes) {
        String iServId = (String) userInfoAttributes.get("preferred_username");
        Optional<User> userOpt = userRepository.findById(iServId);
        return userOpt.isPresent()
                ? Optional.of(userOpt.get())
                : Optional.empty();
    }
}
