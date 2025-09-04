package sandbox27.infrastructure.security;

import java.util.Map;
import java.util.Optional;

public interface UserManagement {

    Optional<?> findUserByPrincipal(String principal);
    boolean hasRole(String principal, String roleName);
    Optional<SecUser> map(Map userInfoAttributes);

}