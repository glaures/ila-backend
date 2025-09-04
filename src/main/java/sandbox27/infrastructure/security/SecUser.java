package sandbox27.infrastructure.security;

import java.util.List;

public interface SecUser {

    String getId();
    List<String> getSecRoles();
}
