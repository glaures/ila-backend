package sandbox27.ila.backend.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.AuthenticatedUser;
import sandbox27.infrastructure.security.AuthenticationType;
import sandbox27.infrastructure.security.RequiredRole;
import sandbox27.infrastructure.security.jwt.JwtGenerator;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Erlaubt Admins, die Anwendung in der Identität eines anderen Nutzers zu benutzen –
 * gedacht zum Testen von Schüler-Sichten und zum Nachvollziehen gemeldeter Probleme.
 * <p>
 * Das ausgestellte Token unterscheidet sich von einem regulären Login nur durch die
 * Laufzeit und den {@code impersonator}-Claim; Subject ist der übernommene Nutzer,
 * damit sich Backend und Frontend exakt so verhalten wie bei dessen eigenem Login.
 * <p>
 * Der Pfad liegt bewusst <strong>nicht</strong> unter {@code /login} oder {@code /auth}:
 * diese Präfixe überspringt der {@code JwtFilter}, der Endpoint braucht aber ein
 * gültiges Admin-Token.
 */
@Slf4j
@RestController
@RequestMapping("/impersonate")
@RequiredArgsConstructor
public class ImpersonationController {

    final UserRepository userRepository;
    final JwtGenerator jwtGenerator;
    final MessageSource messageSource;

    public record ImpersonationRequest(String userName) {
    }

    @RequiredRole(Role.ADMIN_ROLE_NAME)
    @PostMapping
    public ResponseEntity<?> impersonate(@AuthenticatedUser User admin,
                                         @RequestBody ImpersonationRequest request) {
        if (request == null || request.userName() == null || request.userName().isBlank())
            throw new ServiceException(ErrorCode.FieldRequired,
                    messageSource.getMessage("username", null, Locale.GERMAN));

        final String userName = request.userName().trim().toLowerCase();
        User target = userRepository.findById(userName)
                .orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound, request.userName()));

        // Sonst wäre Impersonation ein Weg, sich Adminrechte zu verschaffen oder die
        // Spur zu verwischen, wer eine administrative Aktion ausgelöst hat.
        if (target.hasRole(Role.ADMIN_ROLE_NAME))
            throw new ServiceException(ErrorCode.ImpersonationNotAllowed);

        log.warn("Impersonation: {} übernimmt die Sitzung von {}", admin.getUserName(), target.getUserName());

        String jwt = jwtGenerator.createImpersonationToken(target.getUserName(), admin.getUserName());
        return ResponseEntity.ok(Map.of(
                "token", jwt,
                "user", userInfo(target, admin),
                "roles", target.getSecRoles()));
    }

    /**
     * Baut dieselbe Struktur wie der reguläre Login, ergänzt um {@code impersonatedBy},
     * damit das Frontend einen Hinweis einblenden kann.
     */
    private Map<String, Object> userInfo(User target, User admin) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(AuthenticationType.USER_INFO_AUTH_TYPE_KEY, AuthenticationType.internal);
        userInfo.put("username", target.getUserName());
        userInfo.put("preferred_username", target.getUserName());
        userInfo.put("name", (target.getFirstName() + " " + target.getLastName()).trim());
        userInfo.put("given_name", target.getFirstName());
        userInfo.put("family_name", target.getLastName());
        userInfo.put("email", target.getEmail());
        userInfo.put("impersonatedBy", admin.getUserName());
        return userInfo;
    }
}
