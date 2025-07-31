package sandbox27.ila.frontend.marshalling;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LocalDateSerializer extends StdSerializer<LocalDate> {

    final DateTimeFormatter dateFormatter;

    public LocalDateSerializer(DateTimeFormatter dateFormatter) {
        super(LocalDate.class);
        this.dateFormatter = dateFormatter;
    }

    @Override
    public void serialize(LocalDate localDate, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(dateFormatter.format(localDate));
    }
}
