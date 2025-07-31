package sandbox27.ila.frontend.marshalling;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class LocalTimeSerializer extends StdSerializer<LocalTime> {

    final DateTimeFormatter timeFormatter;

    public LocalTimeSerializer(DateTimeFormatter timeFormatter) {
        super(LocalTime.class);
        this.timeFormatter = timeFormatter;
    }

    @Override
    public void serialize(LocalTime localTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(timeFormatter.format(localTime));
    }
}
