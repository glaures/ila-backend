package sandbox27.infrastructure.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import sandbox27.infrastructure.security.AuthenticatedUser;
import sandbox27.infrastructure.security.SecUser;
import sandbox27.infrastructure.security.UserManagement;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuthenticatedUserResolver implements HandlerMethodArgumentResolver {

    private final AuthorizationTokenReader authorizationTokenReader;
    private final UserManagement userManagement;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(AuthenticatedUser.class) != null;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        final String authHeader = webRequest.getHeader("Authorization");
        final String subject = authorizationTokenReader.retrievePrincipalFromAuthorizationHeader(authHeader);
        return userManagement.findUserByPrincipal(subject).orElse(null);
    }

    public Optional<String> resolveWebInformationIfPossible() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
            String requestUri = request.getRequestURI();
            String httpMethod = request.getMethod();
            String user = "not authenticated";
            final String authHeader = request.getHeader("Authorization");
            if(authHeader != null && authHeader.startsWith("Bearer ")) {
                user = authorizationTokenReader.retrievePrincipalFromAuthorizationHeader(authHeader);
            }
            return Optional.of(httpMethod + " " + requestUri + " [" + user + "]");
        }
        return Optional.empty();
    }
}
