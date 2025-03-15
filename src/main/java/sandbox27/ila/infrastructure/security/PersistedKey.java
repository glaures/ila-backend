package sandbox27.ila.infrastructure.security;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import javax.crypto.SecretKey;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PersistedKey implements SecretKey {

    public final static String ID = "SecretJWTKey";

    @Id
    String id = ID;
    String algorithm;
    String format;
    byte[] encoded;
}
