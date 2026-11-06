package com.ikedi.pos.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gson type adapters for the three types that do not have sensible defaults.
 *
 * <ul>
 *   <li>BigDecimal: stored as a JSON string to preserve precision and honour D1 / D2.
 *       Gson's default serialises it as a number, which may silently lose scale.</li>
 *   <li>LocalDate / LocalDateTime: stored as ISO strings; Gson has no built-in support
 *       for java.time.</li>
 * </ul>
 */
public final class GsonAdapters {

    private GsonAdapters() {
    }

    public static final class BigDecimalAdapter
            implements JsonSerializer<BigDecimal>, JsonDeserializer<BigDecimal> {

        @Override
        public JsonElement serialize(BigDecimal src, Type type, JsonSerializationContext ctx) {
            // Always a quoted string so "100.00" and "100" are distinct.
            return new JsonPrimitive(src.toPlainString());
        }

        @Override
        public BigDecimal deserialize(JsonElement json, Type type, JsonDeserializationContext ctx)
                throws JsonParseException {
            try {
                return new BigDecimal(json.getAsString());
            } catch (NumberFormatException e) {
                throw new JsonParseException("Cannot parse BigDecimal: " + json.getAsString(), e);
            }
        }
    }

    public static final class LocalDateAdapter
            implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {

        @Override
        public JsonElement serialize(LocalDate src, Type type, JsonSerializationContext ctx) {
            return new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type type, JsonDeserializationContext ctx)
                throws JsonParseException {
            try {
                return LocalDate.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                throw new JsonParseException("Cannot parse LocalDate: " + json.getAsString(), e);
            }
        }
    }

    public static final class LocalDateTimeAdapter
            implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {

        @Override
        public JsonElement serialize(LocalDateTime src, Type type, JsonSerializationContext ctx) {
            return new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type type, JsonDeserializationContext ctx)
                throws JsonParseException {
            try {
                return LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                throw new JsonParseException("Cannot parse LocalDateTime: " + json.getAsString(), e);
            }
        }
    }
}

