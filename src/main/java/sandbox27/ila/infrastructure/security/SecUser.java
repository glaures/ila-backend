package sandbox27.ila.infrastructure.security;

import java.util.List;

public interface SecUser {

    String getId();
    List<String> getSecRoles();
}
