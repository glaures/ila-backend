package sandbox27.ila.backend.user;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserRestController {

    final UserRepository userRepository;
    final ModelMapper modelMapper;

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @GetMapping
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream().map(u -> modelMapper.map(u, UserDto.class)).toList();
    }
}
