package sandbox27.ila.infrastructure.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersistedKeyRepository extends JpaRepository<PersistedKey, String> {
}
