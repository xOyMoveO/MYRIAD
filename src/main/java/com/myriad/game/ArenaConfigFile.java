package com.myriad.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.myriad.Myriad;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

final class ArenaConfigFile {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    private ArenaConfigFile() {}

    static <T> T read(Path path, boolean required, Function<JsonObject, T> decode) throws IOException {
        try {
            if (!required && Files.notExists(path)) return decode.apply(new JsonObject());
            return decode.apply(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            throw new IOException(path.getFileName() + " · " + e.getMessage(), e);
        }
    }

    /** Missing optional 5v5 files use defaults. An unreadable existing file leaves the active model intact. */
    static <T> Optional<T> readOptional(Path path, Function<JsonObject, T> decode) {
        return readForEdit(path, decode, () -> decode.apply(new JsonObject()));
    }

    /** An editor builds on the latest valid copy of its own file, including an external repair. */
    static <T> Optional<T> readForEdit(Path path, Function<JsonObject, T> decode, Supplier<T> whenMissing) {
        if (Files.notExists(path)) return Optional.of(whenMissing.get());
        try {
            return Optional.of(read(path, true, decode));
        } catch (IOException e) {
            Myriad.LOGGER.error("Cannot load arena configuration: {}", path, e);
            return Optional.empty();
        }
    }

    static JsonArray array(JsonObject root, String key) {
        if (!root.has(key)) return new JsonArray();
        if (!root.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        return root.getAsJsonArray(key);
    }

    static JsonObject object(JsonObject root, String key) {
        if (!root.has(key)) return new JsonObject();
        if (!root.get(key).isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        return root.getAsJsonObject(key);
    }

    static boolean save(Path target, JsonObject data) {
        try {
            Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporary, JSON.toJson(data));
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return true;
        } catch (IOException e) {
            Myriad.LOGGER.error("Cannot save arena configuration: {}", target, e);
            return false;
        }
    }
}
