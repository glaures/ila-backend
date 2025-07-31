package sandbox27.ila.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IlaUserMapper {

    final UserRepository userRepository;

    Optional<User> map(Map userInfoAttributes) {
        String email = (String)userInfoAttributes.get("email");
        List<User> usersWithEmail = userRepository.findByEmail(email);
        if(usersWithEmail.size() == 1)
            return Optional.of(usersWithEmail.get(0));
        return userRepository.findByFullName((String)userInfoAttributes.get("name"));
    }
}
