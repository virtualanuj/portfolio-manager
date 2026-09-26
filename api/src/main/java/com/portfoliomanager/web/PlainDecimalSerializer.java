package com.portfoliomanager.web;

import java.math.BigDecimal;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Writes every decimal as a JSON string in plain notation, keeping its scale ("12.50000000"). The
 * default number output would lose the scale, and {@code toString()} can produce "1E-8".
 */
@JacksonComponent
public class PlainDecimalSerializer extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
        generator.writeString(value.toPlainString());
    }

    @Override
    public Class<BigDecimal> handledType() {
        return BigDecimal.class;
    }
}
