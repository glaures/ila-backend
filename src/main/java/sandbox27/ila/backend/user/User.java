package sandbox27.ila.backend.user;

import jakarta.persistence.*;
import lombok.*;
import sandbox27.infrastructure.security.SecUser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    @Enumerated(EnumType.STRING)
    Gender gender;
    int grade;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    List<Role> roles = new ArrayList<>();
    boolean ilaMember;

    @Override
    public String getId() {
        return userName;
    }

    @Transient
    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(r -> r.getName().equals(roleName));
    }

    @Override
    public List<String> getSecRoles() {
        return this.roles.stream().map(Role::getName).collect(Collectors.toList());
    }
}
