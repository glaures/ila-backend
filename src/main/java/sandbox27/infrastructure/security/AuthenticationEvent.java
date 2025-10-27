package sandbox27.infrastructure.security;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthenticationEvent {
    
    SecUser loggedInUser;

}
