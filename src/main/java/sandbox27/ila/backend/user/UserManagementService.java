package sandbox27.ila.backend.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import sandbox27.ila.backend.user.events.UserCreatedEvent;
import sandbox27.ila.backend.user.events.UserPasswordResetEvent;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.AuthenticationType;
import sandbox27.infrastructure.security.PasswordUtils;
import sandbox27.infrastructure.security.SecUser;
import sandbox27.infrastructure.security.UserManagement;
import sandbox27.infrastructure.security.controller.InternalAuthController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserManagementService implements UserManagement {

    final UserRepository userRepository;
    final MessageSource messageSource;
    final ApplicationEventPublisher eventPublisher;
    @Value("${ila.admin.user-names}")
    List<String> adminUserNames;

    @Transactional
    public List<User> getAllStudents() {
        return userRepository.findAllByRole(Role.STUDENT);
    }

    @Transactional
    public User createUser(String firstName, String lastName, String email, @Nullable String internalId, String initialRole, boolean internal) {
        if (!Role.isValidRole(initialRole)) {
            throw new ServiceException(ErrorCode.InvalidRole, initialRole);
        }
        if (firstName == null)
            throw new ServiceException(ErrorCode.FieldRequired, messageSource.getMessage("firstName", null, Locale.GERMAN));
        if (lastName == null)
            throw new ServiceException(ErrorCode.FieldRequired, messageSource.getMessage("lastName", null, Locale.GERMAN));
        String username = createUniqueUserPrincipal(firstName, lastName);
        if (userRepository.existsById(username))
            throw new ServiceException(ErrorCode.UserAlreadyExists, username);
        final String randomPassword = PasswordUtils.generateRandomPassword();
        User user = User.builder()
                .userName(username)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .passwordHash(PasswordUtils.hashPassword(randomPassword))
                .internalId(internalId)
                .internal(internal)
                .build();
        user.getRoles().add(Role.valueOf(initialRole));
        user = userRepository.save(user);
        eventPublisher.publishEvent(new UserCreatedEvent(username, firstName, lastName, email, randomPassword));
        return user;
    }

    public Page<User> getAllUsers(boolean internalOnly, Pageable page) {
        if (internalOnly)
            return userRepository.findAllByInternal(true, page);
        return userRepository.findAll(page);
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
        Optional<User> userOpt = Optional.empty();
        switch ((AuthenticationType) userInfoAttributes.get(AuthenticationType.USER_INFO_AUTH_TYPE_KEY)) {
            case AuthenticationType.internal -> {
                final String username = (String) userInfoAttributes.get(InternalAuthController.INTERNAL_AUTH_USERNAME_KEY);
                final String password = (String) userInfoAttributes.get(InternalAuthController.INTERNAL_AUTH_PASSWORD_KEY);
                User user = userRepository.findById(username)
                        .orElse(userRepository.findByEmail(username).stream().findFirst()
                                .orElseThrow(() -> new ServiceException(ErrorCode.InvalidCredentials)));
                if (!PasswordUtils.verifyPassword(password, user.getPasswordHash()))
                    throw new ServiceException(ErrorCode.InvalidCredentials);
                userOpt = Optional.of(user);
            }
            case AuthenticationType.external -> {
                String iServId = (String) userInfoAttributes.get("preferred_username");
                userOpt = userRepository.findById(iServId);
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
                    adminUser = userRepository.save(adminUser);
                    return Optional.of(adminUser);
                }
            }
        }
        return userOpt.isPresent()
                ? Optional.of(userOpt.get())
                : Optional.empty();
    }

    public List<Integer> getAllUserGrades() {
        return userRepository.findAllDistinctGrades();
    }

    @Transactional
    public User resetPassword(String email) {
        User user = userRepository.findByEmail(email)
                .stream().findFirst()
                .orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound));
        if (!user.isInternal())
            throw new ServiceException(ErrorCode.UserNotInternal);
        final String randomPassword = PasswordUtils.generateRandomPassword();
        user.setPasswordHash(PasswordUtils.hashPassword(randomPassword));
        user = userRepository.save(user);
        eventPublisher.publishEvent(new UserPasswordResetEvent(user.getUserName(), user.getFirstName(), user.getEmail(), randomPassword));
        return user;
    }

    public String createDefaultUserPrincipal(String firstName, String lastName) {
        return firstName.trim().toLowerCase() + "." + lastName.trim().toLowerCase();
    }

    public String createUniqueUserPrincipal(String firstName, String lastName) {
        String principal = firstName.trim().toLowerCase() + "." + lastName.trim().toLowerCase();
        int number = 1;
        while (userRepository.existsById(principal))
            if (Character.isDigit(principal.charAt(principal.length() - 1)))
                principal = principal.substring(0, principal.length() - 1) + (++number);
            else principal = principal + (++number);
        return principal;
    }
}
