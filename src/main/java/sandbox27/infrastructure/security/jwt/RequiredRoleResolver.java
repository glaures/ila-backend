package sandbox27.infrastructure.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import sandbox27.infrastructure.error.ErrorCode;
import sandbox27.infrastructure.error.ServiceException;
import sandbox27.infrastructure.security.RequiredRole;
import sandbox27.infrastructure.security.UserManagement;

import java.lang.reflect.Method;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
public class RequiredRoleResolver {

    private final UserManagement userManagement;
    private final AuthorizationTokenReader authorizationTokenReader;

    @Around("@annotation(sandbox27.infrastructure.security.RequiredRole) || @within(sandbox27.infrastructure.security.RequiredRole)")
    public Object checkRequiredRole(ProceedingJoinPoint pjp) throws Throwable {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes)) {
            return pjp.proceed();
        }
        RequiredRole annotation = resolveAnnotation(pjp);
        if (annotation != null) {
            Optional<String> principal = getPrincipalFromRequest();
            String[] requiredRoles = annotation.value();

            if (principal.isEmpty() || !hasAnyRole(principal.get(), requiredRoles)) {
                throw new ServiceException(ErrorCode.RoleRequired, String.join(" oder ", requiredRoles));
            }
        }
        return pjp.proceed();
    }

    private boolean hasAnyRole(String username, String[] roles) {
        for (String role : roles) {
            if (userManagement.hasRole(username, role)) {
                return true;
            }
        }
        return false;
    }

    private RequiredRole resolveAnnotation(ProceedingJoinPoint pjp) {
        // 1) Methodensignatur prüfen
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        RequiredRole ann = AnnotationUtils.findAnnotation(method, RequiredRole.class);
        if (ann != null) return ann;

        // 2) Zielklasse prüfen (auch bei Proxies korrekt auflösen)
        Class<?> targetClass = AopUtils.getTargetClass(pjp.getTarget());
        return AnnotationUtils.findAnnotation(targetClass, RequiredRole.class);
    }

    private Optional<String> getPrincipalFromRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest request = sra.getRequest();
            return Optional.of(authorizationTokenReader.retrievePrincipalFromAuthorizationHeader(request.getHeader("Authorization")));
        }
        return Optional.empty();
    }


}

