package com.myriad.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.myriad.Myriad;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/** 客户端本地小地图位置配置；使用可用屏幕区域的归一化坐标适配分辨率变化。 */
public final class MinimapConfig {
    private MinimapConfig() {}

    private static final int MARGIN = 6;
    private static boolean loaded;
    private static float horizontal = 1.0F;
    private static float vertical = 0.0F;

    public static int x(int screenWidth, int mapSize) {
        ensureLoaded();
        int available = Math.max(0, screenWidth - mapSize - MARGIN * 2);
        return MARGIN + Math.round(available * horizontal);
    }

    public static int y(int screenHeight, int mapSize) {
        ensureLoaded();
        int available = Math.max(0, screenHeight - mapSize - MARGIN * 2);
        return MARGIN + Math.round(available * vertical);
    }

    public static void placeAt(int centerX, int centerY, int screenWidth, int screenHeight, int mapSize) {
        int availableX = Math.max(1, screenWidth - mapSize - MARGIN * 2);
        int availableY = Math.max(1, screenHeight - mapSize - MARGIN * 2);
        horizontal = clamp((centerX - mapSize * 0.5F - MARGIN) / availableX);
        vertical = clamp((centerY - mapSize * 0.5F - MARGIN) / availableY);
        save();
    }

    public static void reset() {
        horizontal = 1.0F;
        vertical = 0.0F;
        loaded = true;
        save();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = path();
        if (!Files.isRegularFile(path)) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            horizontal = clamp(json.has("horizontal") ? json.get("horizontal").getAsFloat() : 1.0F);
            vertical = clamp(json.has("vertical") ? json.get("vertical").getAsFloat() : 0.0F);
        } catch (Exception e) {
            Myriad.LOGGER.warn("[minimap] failed to load client position", e);
        }
    }

    private static void save() {
        JsonObject json = new JsonObject();
        json.addProperty("horizontal", horizontal);
        json.addProperty("vertical", vertical);
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (IOException e) {
            Myriad.LOGGER.warn("[minimap] failed to save client position", e);
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config")
                .resolve("myriad_minimap.json");
    }
}
