package sandbox27.ila.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ImportedCourseDto {

    @JsonProperty("Wochentag")
    String Wochentag;
    @JsonProperty("Block")
    String Block;
    @JsonProperty("Kurs")
    String Kurs;
    @JsonProperty("Kategorien")
    String[] Kategorien;
    @JsonProperty("Klassen")
    String[] Klassen;
    @JsonProperty("Beschreibung")
    String Beschreibung;
    @JsonProperty("KursId")
    String KursId;
    int maxAttendees;
    @JsonProperty("Nachname")
    String Nachname;
    @JsonProperty("Vorname")
    String Vorname;
    @JsonProperty("Raum")
    String Raum;
}
