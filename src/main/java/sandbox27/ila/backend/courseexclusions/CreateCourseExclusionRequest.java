package sandbox27.ila.backend.courseexclusions;

public record CreateCourseExclusionRequest(
        Long courseId,
        String userName,
        String reason
) {}
