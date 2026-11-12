package com.myriad.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.myriad.Myriad;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 每个模组 HUD 分组的本地位置、缩放与显隐配置。 */
public final class HudLayoutConfig {
    public enum Element {
        INFO("info", .02F, .04F, true),
        STATUS("status", .50F, .92F, true),
        SKILLS("skills", .50F, .86F, true),
        ATTACKS("attacks", .68F, .91F, true),
        MINIMAP("minimap", .92F, .12F, true),
        BUFFS("buffs", .96F, .08F, true),
        TARGET("target", .50F, .05F, true),
        OBJECTIVE("objective", .50F, .14F, true),
        ANNOUNCEMENTS("announcements", .50F, .12F, true),
        PROMPTS("prompts", .50F, .50F, true),
        HERO_RESOURCE("hero_resource", .92F, .88F, true),
        DPS("dps", .05F, .92F, true),
        COMBAT_STATS("combat_stats", .91F, .38F, false),
        FEEDBACK("feedback", .50F, .50F, true),
        FLOATERS("floaters", .50F, .75F, true),
        WORLD_BARS("world_bars", .50F, .50F, true),
        TAB("tab", .50F, .08F, true);

        private final String id;
        private final float defaultX;
        private final float defaultY;
        private final boolean defaultVisible;

        Element(String id, float defaultX, float defaultY, boolean defaultVisible) {
            this.id = id;
            this.defaultX = defaultX;
            this.defaultY = defaultY;
            this.defaultVisible = defaultVisible;
        }

        public String id() { return id; }
        public float defaultX() { return defaultX; }
        public float defaultY() { return defaultY; }
    }

    public record State(float x, float y, float scale, boolean visible) { }
    private static final EnumMap<Element, State> STATES = new EnumMap<>(Element.class);
    private static boolean loaded;

    private HudLayoutConfig() { }

    public static State state(Element element) {
        ensureLoaded();
        return STATES.get(element);
    }

    public static void render(GuiGraphicsExtractor gui, Element element, Runnable draw) {
        State state = state(element);
        if (!state.visible()) return;
        Minecraft mc = Minecraft.getInstance();
        float width = mc.getWindow().getGuiScaledWidth();
        float height = mc.getWindow().getGuiScaledHeight();
        float defaultX = element.defaultX * width;
        float defaultY = element.defaultY * height;
        float targetX = state.x() * width;
        float targetY = state.y() * height;
        gui.pose().pushMatrix();
        gui.pose().translate(targetX, targetY);
        gui.pose().scale(state.scale());
        gui.pose().translate(-defaultX, -defaultY);
        draw.run();
        gui.pose().popMatrix();
    }

    public static void set(Element element, float x, float y, float scale, boolean visible) {
        ensureLoaded();
        STATES.put(element, new State(clamp01(x), clamp01(y), Math.clamp(scale, .50F, 2.00F), visible));
        save();
    }

    /** HUD 拖拽中的即时预览；释放鼠标时再统一落盘，避免每个鼠标事件都写配置文件。 */
    public static void setTransient(Element element, float x, float y, float scale, boolean visible) {
        ensureLoaded();
        setWithoutSave(element, x, y, scale, visible);
    }

    public static void saveNow() {
        ensureLoaded();
        save();
    }

    public static void reset(Element element) {
        ensureLoaded();
        STATES.put(element, defaults(element));
        save();
    }

    public static void resetAll() {
        loaded = true;
        STATES.clear();
        for (Element element : Element.values()) STATES.put(element, defaults(element));
        save();
    }

    private static State defaults(Element element) {
        return new State(element.defaultX, element.defaultY, 1.0F, element.defaultVisible);
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        for (Element element : Element.values()) STATES.put(element, defaults(element));
        Path path = path();
        if (!Files.isRegularFile(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (Element element : Element.values()) {
                if (!root.has(element.id) || !root.get(element.id).isJsonObject()) continue;
                JsonObject value = root.getAsJsonObject(element.id);
                State fallback = defaults(element);
                setWithoutSave(element,
                        value.has("x") ? value.get("x").getAsFloat() : fallback.x(),
                        value.has("y") ? value.get("y").getAsFloat() : fallback.y(),
                        value.has("scale") ? value.get("scale").getAsFloat() : fallback.scale(),
                        value.has("visible") ? value.get("visible").getAsBoolean() : fallback.visible());
            }
        } catch (Exception e) {
            Myriad.LOGGER.warn("[hud] failed to load layout", e);
        }
    }

    private static void setWithoutSave(Element element, float x, float y, float scale, boolean visible) {
        STATES.put(element, new State(clamp01(x), clamp01(y), Math.clamp(scale, .50F, 2.00F), visible));
    }

    private static void save() {
        JsonObject root = new JsonObject();
        for (Element element : Element.values()) {
            State state = STATES.get(element);
            JsonObject value = new JsonObject();
            value.addProperty("x", state.x());
            value.addProperty("y", state.y());
            value.addProperty("scale", state.scale());
            value.addProperty("visible", state.visible());
            root.add(element.id, value);
        }
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            Myriad.LOGGER.warn("[hud] failed to save layout", e);
        }
    }

    private static float clamp01(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config")
                .resolve("myriad_hud_layout.json");
    }
}
