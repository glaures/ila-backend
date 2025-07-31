package sandbox27.ila.backend.user;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

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
    String email;
    String firstName;
    String lastName;
    int grade;
    @Column(unique = true)
    String gtsId;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_gts_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "gts_role")
    @Enumerated(EnumType.STRING)
    List<GTSRole> gtsRoles;

}
