package sandbox27.ila.infrastructure.security;

import java.util.Map;
import java.util.Optional;

public interface SecToLocalUserMapper {

    Optional<SecUser> map(Map userInfoAttributes);

}