package sandbox27.ila.backend.courseexclusions;

import sandbox27.ila.backend.user.UserDto;

import java.time.LocalDateTime;

public record CourseExclusionDto(
        Long id,
        Long courseId,
        String courseName,
        UserDto user,
        String reason,
        UserDto createdBy,
        LocalDateTime createdAt
) {}
