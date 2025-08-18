package sandbox27.ila.backend.user;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.ila.infrastructure.security.SecUser;

import java.util.List;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User implements SecUser {

    @Id
    String userName;
    String internalId;
    String email;
    String firstName;
    String lastName;
    int grade;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    List<Role> roles;

    @Override
    public String getId() {
        return userName;
    }
}
