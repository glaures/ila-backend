package sandbox27.ila.backend.user;

import lombok.*;

import java.util.List;

@Setter
@Getter
@Builder
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

    public static UserDto map(User user) {
        // Null-safe: return null if user is null
        if (user == null) {
            return null;
        }

        return UserDto.builder()
                .userName(user.getUserName())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .grade(user.getGrade())
                .gender(user.getGender())
                .roles(user.getRoles().stream().map(Enum::name).toList())
                .ilaMember(user.isIlaMember())
                .build();
    }
}