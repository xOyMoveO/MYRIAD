package com.myriad.client;

import com.myriad.network.LuoxiaBowPosePayload;
import com.myriad.client.firstperson.LuoxiaBowPrototype;
import com.myriad.network.LuoxiaBowPosePayload.Phase;
import com.myriad.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;

/** Observer and owner share the same pose. Only the initial local nock is predicted. */
public final class LuoxiaPoseClient {
    private record Sample(LuoxiaBowPosePayload pose, long received) {}
    private static final Map<UUID,Sample> bows = new HashMap<>(), steps = new HashMap<>();
    private static final Map<UUID,Long> chains = new HashMap<>();
    private static ClientLevel world;
    public static final LuoxiaBowPrototype FIRST_PERSON = new LuoxiaBowPrototype();
    private static int localToken;
    private static boolean localHeld;
    private static long earlyUntil;

    private LuoxiaPoseClient() {}

    public static void reset() {
        bows.clear(); steps.clear(); chains.clear(); FIRST_PERSON.reset();
        world = Minecraft.getInstance().level; localHeld = false; localToken = 0; earlyUntil = 0;
    }

    public static void tick(Minecraft mc) {
        if (world != mc.level) reset();
        if (world == null) return;
        long now = world.getGameTime();
        if (mc.player == null || !hasBow(mc.player.getUUID())) FIRST_PERSON.reset();
        bows.entrySet().removeIf(e -> now - e.getValue().received > 18 || !hasBow(e.getKey()));
        steps.entrySet().removeIf(e -> now - e.getValue().received > 2 || !hasBow(e.getKey()));
        chains.entrySet().removeIf(e -> now >= e.getValue() || !hasBow(e.getKey()));
    }

    private static boolean hasBow(UUID id) {
        var player = world.getPlayerByUUID(id);
        return player != null && player.isAlive() && !player.isSpectator() && player.getMainHandItem().is(ModItems.GUIXIA_BOW.get())
                && (player != Minecraft.getInstance().player || ClientDataHolder.modEnabled() && "luoxia".equals(ClientDataHolder.characterId()));
    }

    private static void storePose(LuoxiaBowPosePayload cue, long tick) {
        bows.put(cue.owner(), new Sample(cue, tick));
        var mc = Minecraft.getInstance();
        if (mc.player != null && cue.owner().equals(mc.player.getUUID()))
            FIRST_PERSON.accept(cue, tick + (double)mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    static void begin(int token, boolean predict) {
        var mc = Minecraft.getInstance();
        if (world != mc.level) reset();
        if (world == null || mc.player == null) return;
        localToken = token; localHeld = true; earlyUntil = 0;
        if (!predict) return;
        long now = world.getGameTime();
        var pose = new LuoxiaBowPosePayload(mc.player.getUUID(), token, -1, Phase.DRAW, 0, 11, 11,
                mc.player.getYRot(), mc.player.getXRot(), now);
        storePose(pose, now);
    }

    static void letGo(boolean cancel) {
        localHeld = false;
        if (!cancel) return;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var sample = bows.get(player.getUUID());
            if (sample != null && sample.pose.phase() == Phase.DRAW) {
                bows.remove(player.getUUID());
                FIRST_PERSON.reset();
            }
        }
    }

    public static void receive(LuoxiaBowPosePayload p) {
        var mc = Minecraft.getInstance();
        if (world != mc.level) reset();
        if (world == null || mc.player == null) return;
        if (p.phase() == Phase.RESET) {
            bows.remove(p.owner()); steps.remove(p.owner()); chains.remove(p.owner());
            if (mc.player.getUUID().equals(p.owner())) { localHeld = false; earlyUntil = 0; FIRST_PERSON.reset(); }
            return;
        }
        if (!hasBow(p.owner())) return;
        long now = world.getGameTime();
        boolean owner = p.owner().equals(mc.player.getUUID());
        if (owner && p.skill() <= 1 && p.phase() != Phase.CHAIN && p.token() < localToken) return;
        switch (p.phase()) {
            case EARLY -> { if (owner) earlyUntil = now + 8; }
            case CHAIN -> chains.put(p.owner(), now + 24);
            case STEP -> {
                if (p.work() < 1) steps.put(p.owner(), new Sample(p, now)); else steps.remove(p.owner());
            }
            case CANCEL -> {
                Sample old = bows.get(p.owner());
                if (old != null && (old.pose.skill() <= 1) == (p.skill() <= 1) && old.pose.token() == p.token()) {
                    // Arrow leaves immediately. The empty arms have a short return pose.
                    storePose(p, now);
                }
                if (owner && p.skill() <= 1 && p.token() == localToken) {
                    localHeld = false;
                    if (mc.player.getUseItem().is(ModItems.GUIXIA_BOW.get())) mc.player.stopUsingItem();
                }
            }
            case DRAW -> {
                if (!owner || localHeld && p.token() == localToken) storePose(p, now);
            }
            default -> {
                storePose(p, now);
                if (p.phase() == Phase.RELEASE && p.skill() <= 1) chains.remove(p.owner());
            }
        }
    }

    public static LuoxiaBowPosePayload pose(LivingEntity p) {
        var s = bows.get(p.getUUID());
        if (s == null || world != p.level() || !hasBow(p.getUUID())) return null;
        if (s.pose.phase() == Phase.RELEASE && world.getGameTime() - s.received >= 4) return null;
        if (s.pose.phase() == Phase.CANCEL && world.getGameTime() - s.received >= 2) return null;
        return s.pose;
    }

    public static float recoil(LivingEntity p, float partial) {
        var s = bows.get(p.getUUID());
        return s == null || s.pose.phase() != Phase.RELEASE ? 0 :
                Math.clamp(1 - (world.getGameTime() - s.received + partial) / 4f, 0, 1);
    }

    static boolean exclusive(LivingEntity p) {
        var cue = pose(p);
        return cue != null && cue.skill() > 1 && cue.phase() != Phase.CANCEL && (cue.phase() != Phase.RELEASE
                || (cue.skill() == 4 || cue.skill() == 6) && cue.work() < 3);
    }
    public static float stepDirection(LivingEntity p) {
        var step = steps.get(p.getUUID());
        return step == null ? 0 : net.minecraft.util.Mth.wrapDegrees(step.pose.yaw() - p.getYRot());
    }
    public static boolean stepping(LivingEntity p) { return steps.containsKey(p.getUUID()); }
    public static boolean chained(LivingEntity p) { return chains.containsKey(p.getUUID()); }
    public static boolean early() { return world != null && world.getGameTime() < earlyUntil; }
}
