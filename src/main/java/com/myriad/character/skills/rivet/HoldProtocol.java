package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.combat.SkillTargets;
import com.myriad.player.ModShieldSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** The fixed region survives anchor replacement and terrain destruction, but belongs to its caster's lifecycle. */
public final class HoldProtocol extends RivetSkill {
    private static final class Zone {
        final AbilityRuntime.Cast cast;
        final Vec3 center;
        final long until;
        boolean shieldGranted;
        Zone(AbilityRuntime.Cast c, Vec3 point) { cast = c; center = point; until = c.now() + 120; }
    }
    private static final Map<UUID, Zone> ZONES = new HashMap<>();
    public HoldProtocol(Definition definition) { super(definition); }
    @Override public boolean canRelease(AbilityRuntime.Cast c) {
        var anchor = TacticalAnchor.anchor(c.player);
        return anchor != null && c.player.position().distanceToSqr(anchor.point()) <= 100.0001;
    }
    @Override public void release(AbilityRuntime.Cast c) {
        var anchor = TacticalAnchor.anchor(c.player);
        if (anchor == null) return;
        clear(c.player);
        var zone = new Zone(c, anchor.point());
        ZONES.put(c.player.getUUID(), zone);
        RivetVfx.field(c, zone.center, 120);
        tick(c.player);
    }
    private static boolean contains(Zone zone, LivingEntity target) {
        return target.level() == zone.cast.player.level() && target.position().distanceToSqr(zone.center) <= 16.0001;
    }
    public static double incoming(LivingEntity target) {
        long now = target.level().getGameTime();
        for (var zone : ZONES.values()) if (now < zone.until && contains(zone, target)
                && SkillTargets.ally(zone.cast.player, target)) return .85;
        return 1;
    }
    public static boolean extendsHammer(ServerPlayer owner, LivingEntity enemy) {
        var zone = ZONES.get(owner.getUUID());
        return zone != null && owner.level().getGameTime() < zone.until && contains(zone, enemy);
    }
    public static void tick(ServerPlayer player) {
        var zone = ZONES.get(player.getUUID());
        if (zone == null) return;
        if (player.level().getGameTime() >= zone.until) { clear(player); return; }
        if (!zone.shieldGranted && contains(zone, player)) {
            zone.shieldGranted = true;
            ModShieldSystem.grantMaxSource(player, "rivet_hold_protocol/" + player.getUUID(),
                    zone.cast.value(142, 50, .6f), (int) (zone.until - player.level().getGameTime()));
            RivetVfx.shield(zone.cast, player, .9);
        }
    }
    public static void clear(ServerPlayer player) {
        ZONES.remove(player.getUUID());
        ModShieldSystem.removeSource(player, "rivet_hold_protocol/" + player.getUUID());
    }
}
