package sandbox27.ila.backend.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    String userName;
    String firstName;
    String lastName;
    String email;
    int grade;
    Gender gender;
    boolean ilaMember;
    List<String> roles;
}
