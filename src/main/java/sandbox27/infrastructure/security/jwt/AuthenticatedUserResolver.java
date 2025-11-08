package sandbox27.infrastructure.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import sandbox27.infrastructure.security.AuthenticatedUser;
import sandbox27.infrastructure.security.UserManagement;

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
}
