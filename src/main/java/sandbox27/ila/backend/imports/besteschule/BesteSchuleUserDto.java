package sandbox27.ila.backend.imports.besteschule;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BesteSchuleUserDto(
    @JsonProperty("importID") String importID,
    @JsonProperty("firstName") String firstName,
    @JsonProperty("lastName") String lastName,
    @JsonProperty("gender") String gender
) {
}
