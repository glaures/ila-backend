package sandbox27.ila.backend.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;
    @JsonIgnore
    String password;
    @Column(unique = true)
    String email;
    @Column(unique = true)
    String name;

}
