package sandbox27.ila.infrastructure.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.user.User;
import sandbox27.ila.backend.user.UserRepository;
import sandbox27.ila.infrastructure.error.ErrorCode;
import sandbox27.ila.infrastructure.error.ServiceException;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findUserByEmail(email).orElseThrow(() ->
                new ServiceException(ErrorCode.UserNotFound, email));
        return SecurityUser.builder()
                .username(user.getEmail())
                .rights(List.of("USER_WRITE_" + user.getId()))
                .build();
    }
}
