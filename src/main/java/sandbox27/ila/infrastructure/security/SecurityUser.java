package sandbox27.ila.infrastructure.security;

import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SecurityUser implements UserDetails {

    final String password = null;
    String username;
    List<String> rights = new ArrayList<>();

    public boolean hasRight(String right) {
        return this.rights.stream().anyMatch(a -> a.equals(right));
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return rights.stream().map(r -> new SimpleGrantedAuthority(r)).collect(Collectors.toList());
    }

    public boolean isAccountNonExpired() {
        return true;
    }

    public boolean isAccountNonLocked() {
        return true;
    }

    public boolean isCredentialsNonExpired() {
        return true;
    }

    public boolean isEnabled() {
        return true;
    }
}
