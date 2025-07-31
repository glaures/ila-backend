package sandbox27.ila.frontend.marshalling;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;

public class DayOfWeekSerializer extends StdSerializer<DayOfWeek> {

    final DateTimeFormatter dayOfWeekFormatter;

    public DayOfWeekSerializer(DateTimeFormatter dayOfWeekFormatter) {
        super(DayOfWeek.class);
        this.dayOfWeekFormatter = dayOfWeekFormatter;
    }

    @Override
    public void serialize(DayOfWeek dayOfWeek, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(dayOfWeekFormatter.format(dayOfWeek));
    }
}
