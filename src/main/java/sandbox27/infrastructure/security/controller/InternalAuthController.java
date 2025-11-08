package sandbox27.infrastructure.security.controller;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.*;
import sandbox27.infrastructure.security.jwt.JwtGenerator;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/login")
@RequiredArgsConstructor
public class InternalAuthController implements ApplicationContextAware {

    public final static String INTERNAL_AUTH_USERNAME_KEY = "username";
    public final static String INTERNAL_AUTH_PASSWORD_KEY = "password";

    private ApplicationContext applicationContext;
    final JwtGenerator jwtGenerator;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public record Credentials(String username, String password) {};
    final UserManagement userManagement;

    @PostMapping
    public ResponseEntity<?> authenticateInternal(@RequestBody Credentials credentials) {
        Map userInfo = new HashMap();
        userInfo.put(AuthenticationType.USER_INFO_AUTH_TYPE_KEY, AuthenticationType.internal);
        userInfo.put(INTERNAL_AUTH_USERNAME_KEY, credentials.username);
        userInfo.put(INTERNAL_AUTH_PASSWORD_KEY, credentials.password);
        SecUser user = userManagement.map(userInfo).
                orElseThrow(() -> new ServiceException(ErrorCode.UserNotFound, userInfo.get(credentials.username)));

        applicationContext.publishEvent(new AuthenticationEvent(user));
        String jwt = jwtGenerator.createToken(user.getId());
        return ResponseEntity.ok(Map.of("token", jwt, "username", credentials.username, "roles", user.getSecRoles()));
    }
}
