package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.SkillTargets;
import com.myriad.player.ModShieldSystem;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Each owner retains a removable source; the shared strut family contributes only its highest shield. */
public final class EmergencyStrut extends RivetSkill {
    private static final Map<UUID, Long> BRACING = new HashMap<>();
    private static final Map<UUID, Set<LivingEntity>> TARGETS = new HashMap<>();
    public EmergencyStrut(Definition definition) { super(definition); }
    @Override public void release(AbilityRuntime.Cast c) {
        BRACING.put(c.player.getUUID(), c.now() + 40);
        var targets = TARGETS.computeIfAbsent(c.player.getUUID(), ignored -> new HashSet<>());
        var recipients = new HashSet<>(c.player.level().getEntitiesOfClass(LivingEntity.class, c.player.getBoundingBox().inflate(3),
                target -> SkillTargets.alive(target) && !target.isSpectator() && SkillTargets.ally(c.player, target)
                        && target.position().distanceToSqr(c.player.position()) <= 9.0001));
        recipients.add(c.player);
        for (var target : recipients) {
            ModShieldSystem.grantMaxGroup(target, "rivet_emergency_strut/" + c.player.getUUID(),
                    "rivet_emergency_strut", c.value(95, 22, .5f), 40);
            targets.add(target);
            RivetVfx.shield(c, target, .75);
        }
        var anchor = TacticalAnchor.anchor(c.player);
        if (anchor != null && c.player.position().distanceToSqr(anchor.point()) <= 16.0001) {
            var w = FirstGroupSkills.node("rivet", 3);
            if (w != null) c.data.setCooldown(w.getId(), Math.max(0, c.data.getCooldown(w.getId()) - 40));
        }
        PlayerDataProvider.syncToClient(c.player);
    }
    public static double knockbackMultiplier(LivingEntity target) {
        return target.level().getGameTime() < BRACING.getOrDefault(target.getUUID(), 0L) ? .5 : 1;
    }
    public static void clear(ServerPlayer player) {
        BRACING.remove(player.getUUID());
        var targets = TARGETS.remove(player.getUUID());
        if (targets != null) for (var target : targets) ModShieldSystem.removeSource(target, "rivet_emergency_strut/" + player.getUUID());
    }
}
