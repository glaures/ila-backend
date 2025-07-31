package sandbox27.ila.frontend.marshalling;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Configuration
public class MarshallingConfiguration {

    final public static DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    final public static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    final public static DateTimeFormatter dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN);

    @Bean
    public JavaTimeModule javaTimeModule() {
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(new LocalTimeSerializer(timeFormatter));
        module.addSerializer(new LocalDateSerializer(dateFormatter));
        module.addSerializer(new DayOfWeekSerializer(dayOfWeekFormatter));
        return module;
    }

}
