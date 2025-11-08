package sandbox27.ila.backend.user;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import sandbox27.infrastructure.security.RequiredRole;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserRestController {

    final UserManagementService userManagementService;
    final ModelMapper modelMapper;

    public record UserPayload(
            String login,
            String firstName,
            String lastName,
            String email,
            String initialRole
    ) {
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @GetMapping
    public List<UserDto> getAllUsers(@RequestParam(name = "intern", defaultValue = "false") boolean internal,
                                     @RequestParam(name = "page", defaultValue = "0") int page,
                                     @RequestParam(name = "count", defaultValue = "9999") Integer count) {
        return userManagementService.getAllUsers(internal, PageRequest.of(page, count)).stream().map(u -> modelMapper.map(u, UserDto.class)).toList();
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @GetMapping("/grades")
    public List<Integer> getAllUserGrades() {
        return userManagementService.getAllUserGrades();
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @PutMapping
    public UserDto createUser(@RequestBody UserPayload userPayload) {
        User user = userManagementService.createUser(
                userPayload.login,
                userPayload.firstName,
                userPayload.lastName,
                userPayload.email,
                null,
                userPayload.initialRole,
                true // interner Nutzer außerhalb von IServ
        );
        return modelMapper.map(user, UserDto.class);
    }


}
