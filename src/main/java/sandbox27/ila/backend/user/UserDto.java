package sandbox27.ila.backend.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    String userName;
    String firstName;
    String lastName;
    int grade;

}
