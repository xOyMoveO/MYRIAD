package com.myriad.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import java.util.Map;

final class SnapshotJson {
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();

    private SnapshotJson() {}

    static JsonElement json(Object value) {
        return COMPACT.toJsonTree(value);
    }

    static JsonObject field(Object value, String unit, String source, String condition, String sampledAt, String clock) {
        String status = value instanceof Number number && number.doubleValue() == 0 ? "zero" : "defined";
        return field(status, json(value), unit, source, condition, sampledAt, clock);
    }

    static JsonObject gap(String status, String unit, String source, String condition, String sampledAt, String clock, String reason) {
        JsonObject result = field(status, JsonNull.INSTANCE, unit, source, condition, sampledAt, clock);
        result.addProperty("reason", reason);
        return result;
    }

    private static JsonObject field(String status, JsonElement value, String unit, String source,
            String condition, String sampledAt, String clock) {
        JsonObject result = new JsonObject();
        result.addProperty("status", status);
        result.add("value", value);
        result.addProperty("unit", unit);
        result.addProperty("source", source);
        result.addProperty("condition", condition);
        result.addProperty("sampledAt", sampledAt);
        result.addProperty("clock", clock);
        return result;
    }

    static String source(Class<?> owner, String member) {
        return "src/main/java/" + owner.getName().replace('.', '/') + ".java#" + member;
    }

    static String canonical(JsonElement value) {
        return COMPACT.toJson(ordered(value));
    }

    static String pretty(JsonElement value) {
        return PRETTY.toJson(ordered(value)) + "\n";
    }

    private static JsonElement ordered(JsonElement value) {
        if (value instanceof JsonObject object) {
            JsonObject result = new JsonObject();
            object.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.add(entry.getKey(), ordered(entry.getValue())));
            return result;
        }
        if (value instanceof JsonArray array) {
            JsonArray result = new JsonArray(array.size());
            array.forEach(item -> result.add(ordered(item)));
            return result;
        }
        return value;
    }
}
