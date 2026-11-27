package com.myriad.acceptance;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.myriad.client.ClientDataHolder;
import dev.mcpfabric.McpFabric;
import dev.mcpfabric.bridge.RpcContext;
import dev.mcpfabric.bridge.RpcException;
import dev.mcpfabric.client.ClientMc;
import dev.mcpfabric.handlers.support.Gates;
import dev.mcpfabric.handlers.support.Takeover;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Test-launch source set only: extends the authenticated MCPFabric router, never a release JAR. */
public final class MyriadInputBridge implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onTick());
    }
    private record Held(KeyMapping mapping, InputConstants.Key original, int key, int button, long until) {}
    private static final List<Held> held = new ArrayList<>();
    private static boolean registered;
    private static long tick, keyEvents, mouseEvents;
    private static Object inputWorld;
    private static Capture capture;
    private static final java.util.ArrayDeque<JsonObject> presentation = new java.util.ArrayDeque<>();

    private static final class Capture {
        final String id = UUID.randomUUID().toString();
        final JsonArray frames = new JsonArray();
        final int count, interval;
        final Path directory;
        long next;
        boolean inFlight;
        String error;
        Capture(int count, int interval) throws Exception {
            this.count = count; this.interval = interval; this.next = tick;
            directory = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots/myriad/" + id);
            Files.createDirectories(directory);
        }
    }

    public static void onTick() {
        tick++;
        if (!registered && McpFabric.router() != null) {
            var router = McpFabric.router();
            router.register("myriad.state", c -> onClient(() -> { Gates.vision(); return state(); }));
            router.register("myriad.input", c -> onClient(() -> input(c)));
            router.register("myriad.release", c -> onClient(() -> {
                releaseAll();
                JsonObject result = new JsonObject();
                result.addProperty("heldInputs", held.size());
                return result;
            }));
            router.register("myriad.capture", c -> onClient(() -> startCapture(c)));
            router.register("myriad.captureStatus", c -> onClient(() -> captureStatus(c)));
            router.register("myriad.presentation", c -> onClient(() -> {
                Gates.vision(); JsonArray events=new JsonArray(); presentation.forEach(events::add);
                JsonObject result=new JsonObject(); result.add("events",events); return result;
            }));
            registered = true;
            McpFabric.LOGGER.info("[myriad-acceptance] native input and frame capture registered");
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            boolean interrupted = Takeover.paused() || mc.level != inputWorld || mc.gui.screen() != null;
            for (Held key : List.copyOf(held)) if (interrupted || tick >= key.until()) release(key);
            if (capture != null && capture.error == null && capture.frames.size() < capture.count
                    && !capture.inFlight && tick >= capture.next) captureFrame(mc, capture);
        } catch (Exception error) {
            McpFabric.LOGGER.error("[myriad-acceptance] cleanup/capture failed", error);
        }
    }

    public static void observedKey() { keyEvents++; }
    public static void observedMouse() { mouseEvents++; }

    /** Observes production presentation entry points; never changes a skill or its result. */
    public static void observePresentation(String type, JsonObject detail) {
        detail.addProperty("type",type); detail.addProperty("clientTick",tick);
        if(presentation.size()>=128)presentation.removeFirst();
        presentation.addLast(detail);
    }

    private static JsonObject onClient(java.util.concurrent.Callable<JsonObject> task) throws RpcException {
        return ClientMc.call(() -> {
            try { return task.call(); }
            catch (RpcException error) { throw error; }
            catch (Exception error) { throw new RpcException("adapter_error", error.toString()); }
        });
    }

    private static JsonObject input(RpcContext c) throws Exception {
        Gates.playerControl();
        Minecraft mc = Minecraft.getInstance();
        ClientMc.player();
        if (mc.gui.screen() != null) Gates.uiControl();
        int duration = c.optInt("holdTicks", 1);
        if (duration < 1 || duration > 20) throw RpcException.badRequest("holdTicks must be 1..20");
        if (c.optInt("captureFrames", 0) > 0) startCapture(c);
        String kind = c.getString("kind");
        if (kind.equals("scroll")) {
            var screen = mc.gui.screen();
            if (screen == null) throw RpcException.badRequest("GUI scroll requires an open screen");
            var window = mc.getWindow();
            double x = c.getDouble("x"), y = c.getDouble("y"), amount = c.getDouble("amount");
            if (!Double.isFinite(amount) || Math.abs(amount) > 20) throw RpcException.badRequest("Scroll must be within 20 steps");
            mc.mouseHandler.onMove(window.handle(), x * window.getScreenWidth() / screen.width,
                    y * window.getScreenHeight() / screen.height, 0, 0);
            mc.mouseHandler.onScroll(window.handle(), 0, amount);
            var result = state();
            result.addProperty("inputRoute", "native_mouse_scroll");
            return result;
        }
        Held key;
        if (kind.equals("key")) {
            String name = c.getString("key");
            KeyMapping mapping = null;
            int code;
            InputConstants.Key original = null;
            if (name.startsWith("key.keyboard.")) {
                code = InputConstants.getKey(name).getValue();
            } else {
                for (KeyMapping candidate : mc.options.keyMappings) if (candidate.getName().equals(name)) mapping = candidate;
                if (mapping == null) throw RpcException.notFound("Unknown key mapping: " + name);
                if (mapping.isDown()) throw RpcException.unavailable("Key mapping is already held");
                original = KeyMappingHelper.getBoundKeyOf(mapping);
                code = unusedKey(mc);
                mapping.setKey(InputConstants.Type.KEYBOARD.getOrCreate(code));
                KeyMapping.resetMapping();
            }
            key = new Held(mapping, original, code, -1, tick + duration);
        } else if (kind.equals("mouse")) {
            int button = c.getInt("button");
            if (button < 0 || button > 2) throw RpcException.badRequest("button must be 0..2");
            if (mc.gui.screen() != null) {
                if (!c.has("x") || !c.has("y")) throw RpcException.badRequest("GUI mouse input needs x and y");
                var window = mc.getWindow();
                mc.mouseHandler.onMove(window.handle(),
                        c.getDouble("x") * window.getScreenWidth() / mc.gui.screen().width,
                        c.getDouble("y") * window.getScreenHeight() / mc.gui.screen().height, 0, 0);
            }
            int nativeButton = switch(button) {
                case 0 -> InputConstants.MOUSE_BUTTON_LEFT;
                case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
                default -> InputConstants.MOUSE_BUTTON_MIDDLE;
            };
            key = new Held(null, null, -1, nativeButton, tick + duration);
        } else throw RpcException.badRequest("kind must be key, mouse or scroll");
        inputWorld = mc.level;
        held.add(key);
        try { dispatch(key, InputConstants.PRESS); }
        catch (Exception error) { release(key); throw error; }
        JsonObject result = new JsonObject();
        result.addProperty("heldInputs", held.size());
        if (McpFabric.config().enableVision) result = state();
        result.addProperty("inputRoute", "native_keyboard_or_mouse_handler");
        if (capture != null) result.addProperty("captureId", capture.id);
        return result;
    }

    private static int unusedKey(Minecraft mc) throws RpcException {
        for (int code = InputConstants.KEY_F13; code <= InputConstants.KEY_F24; code++) { // SDL F13..F24; temporary, never saved to options.
            boolean used = false;
            for (KeyMapping mapping : mc.options.keyMappings)
                if (KeyMappingHelper.getBoundKeyOf(mapping).getType() == InputConstants.Type.KEYBOARD && KeyMappingHelper.getBoundKeyOf(mapping).getValue() == code) used = true;
            if (!used) return code;
        }
        throw RpcException.unavailable("No unused temporary key is available");
    }

    private static void dispatch(Held key, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (key.button() >= 0) {
            mc.mouseHandler.onButton(mc.getWindow().handle(), new MouseButtonInfo(key.button(), 0), action);
        } else {
            int keycode = org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyFromScancode(key.key(), (short)0, false);
            mc.keyboardHandler.keyPress(mc.getWindow().handle(), action, new KeyEvent(key.key(), keycode, 0));
        }
    }

    private static void release(Held key) throws Exception {
        try { dispatch(key, InputConstants.RELEASE); }
        finally {
            if (key.mapping() != null) {
                key.mapping().setDown(false);
                key.mapping().setKey(key.original());
                KeyMapping.resetMapping();
            }
            held.remove(key);
        }
    }
    private static void releaseAll() throws Exception { for (Held key : List.copyOf(held)) release(key); }

    private static JsonObject state() {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o = new JsonObject();
        o.addProperty("adapterVersion", 3);
        var json = new com.google.gson.Gson();
        o.add("lobby", json.toJsonTree(com.myriad.client.LobbyClient.snapshot()));
        o.add("draft", json.toJsonTree(ClientDataHolder.pickState()));
        o.addProperty("phase", ClientDataHolder.phase().name());
        if (mc.level != null) {
            o.addProperty("dimension", mc.level.dimension().identifier().toString());
            o.addProperty("worldTick", mc.level.getGameTime());
        }
        o.addProperty("platform", "fabric-26.3-sdl");
        o.addProperty("character", ClientDataHolder.characterId());
        o.addProperty("modEnabled", ClientDataHolder.modEnabled());
        o.addProperty("training", ClientDataHolder.isTraining());
        o.addProperty("nativeKeyEvents", keyEvents);
        o.addProperty("nativeMouseEvents", mouseEvents);
        o.addProperty("heldInputs", held.size());
        o.addProperty("clientTick", tick);
        o.addProperty("guiWidth", mc.getWindow().getGuiScaledWidth());
        o.addProperty("guiHeight", mc.getWindow().getGuiScaledHeight());
        o.addProperty("placementType", ClientDataHolder.placementType());
        o.addProperty("screen", mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
        var data = ClientDataHolder.get();
        if (data != null) {
            o.addProperty("gold", data.gold()); o.addProperty("noCooldown", data.noCooldown());
            o.addProperty("noMana", data.noMana()); o.addProperty("noGoldCost", data.noGoldCost());
            o.addProperty("level", data.level()); o.addProperty("mana", data.mana());
            o.addProperty("skillPoints", data.skillPoints());
            JsonObject ranks = new JsonObject(); data.skillLevels().forEach(ranks::addProperty); o.add("skills", ranks);
            JsonArray cooldowns = new JsonArray();
            for (int i = 0; i < 5; i++) cooldowns.add(ClientDataHolder.cooldownTicks(i));
            o.add("cooldowns", cooldowns);
        }
        if (mc.player != null) {
            o.addProperty("x", mc.player.getX()); o.addProperty("y", mc.player.getY()); o.addProperty("z", mc.player.getZ());
            o.addProperty("yaw", mc.player.getYRot()); o.addProperty("pitch", mc.player.getXRot());
            o.addProperty("camera", mc.options.getCameraType().name());
            o.addProperty("fov", mc.options.fov().get());
            o.addProperty("mainArm", mc.player.getMainArm().name());
            o.addProperty("mainItem", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString());
            o.addProperty("offhandEmpty", mc.player.getOffhandItem().isEmpty());
            o.addProperty("skin", mc.player.getSkin().body().texturePath().toString());
            o.addProperty("shuoDrawn", ClientDataHolder.syncedCounter("g1_shuo_drawn") > 0);
            o.addProperty("overworldClock", mc.level.getOverworldClockTime());
            o.addProperty("rain", mc.level.getRainLevel(1));
            o.addProperty("uuid", mc.player.getUUID().toString());
            o.addProperty("name", mc.player.getName().getString());
            JsonArray observed = new JsonArray();
            for (var other : mc.level.players()) {
                JsonObject player = new JsonObject();
                player.addProperty("id", other.getId());
                player.addProperty("name", other.getName().getString());
                player.addProperty("uuid", other.getUUID().toString());
                player.addProperty("x", other.getX()); player.addProperty("y", other.getY()); player.addProperty("z", other.getZ());
                player.addProperty("health", other.getHealth());
                observed.add(player);
            }
            o.add("observedPlayers", observed);
        }
        return o;
    }

    private static JsonObject startCapture(RpcContext c) throws Exception {
        Gates.vision(); ClientMc.player();
        if (capture != null && capture.error == null && capture.frames.size() < capture.count)
            throw RpcException.unavailable("A frame capture is already running");
        int count = c.optInt("captureFrames", 20), interval = c.optInt("intervalTicks", 1);
        if (count < 1 || count > 40 || interval < 1 || interval > 10)
            throw RpcException.badRequest("captureFrames must be 1..40; intervalTicks must be 1..10");
        capture = new Capture(count, interval);
        return captureInfo(capture);
    }

    private static void captureFrame(Minecraft mc, Capture job) {
        if (mc.level == null || mc.player == null) { job.error = "World closed during capture"; return; }
        job.inFlight = true;
        JsonObject frame = state();
        Path file = job.directory.resolve(String.format("frame-%03d.png", job.frames.size()));
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try {
                image.writeToFile(file);
                frame.addProperty("path", file.toAbsolutePath().toString());
                job.frames.add(frame);
            } catch (Exception error) { job.error = error.toString(); }
            finally { image.close(); job.inFlight = false; job.next = tick + job.interval; }
        });
    }

    private static JsonObject captureInfo(Capture job) {
        JsonObject o = new JsonObject();
        o.addProperty("captureId", job.id); o.addProperty("complete", job.frames.size() == job.count || job.error != null);
        o.addProperty("requestedFrames", job.count); o.add("frames", job.frames.deepCopy());
        if (job.error != null) o.addProperty("error", job.error);
        return o;
    }

    private static JsonObject captureStatus(RpcContext c) throws Exception {
        Gates.vision();
        if (capture == null || !capture.id.equals(c.getString("captureId"))) throw RpcException.notFound("Capture not found");
        JsonObject result = captureInfo(capture);
        Files.writeString(capture.directory.resolve("capture.json"), result.toString());
        return result;
    }
}
