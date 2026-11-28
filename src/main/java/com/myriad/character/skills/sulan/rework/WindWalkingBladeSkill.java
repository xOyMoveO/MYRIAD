package com.myriad.character.skills.sulan.rework;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.KnockbackBuffer;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class WindWalkingBladeSkill extends AbilitySkill {
    public static final double CHARGE_DISTANCE = 30, STEP_LIMIT = .55;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(35, 14.4f, .35f, .25f).segment("skill.myriad.damage.segment.bonus_basic", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public WindWalkingBladeSkill(Definition definition) {
        super(definition);
    }

    static void trackSkillDash(Cast source, DashPose phase) {
        if (phase == DashPose.MOVING) return;
        var passive = FirstGroupSkills.node("sulan", 0);
        if (source.data.skillLevel(passive.getId()) <= 0) return;
        var movement = AbilityRuntime.context(source.player, passive);
        var dash = movement.memory("skill_motion");
        if (phase == DashPose.START) {
            dash.point = source.player.position();
        } else if (dash.point != null) {
            // Motion tasks finish before the passive samples current position.
            // The final short step still belongs to the dash.
            movement.memory().lastPosition = source.player.position();
            dash.point = null;
        }
    }

    @Override public void tickPassive(Cast c) {
        var memory = c.memory();
        Vec3 pos = c.player.position();
        if (memory.lastPosition != null && c.player.onGround() && !AbilityRuntime.channeling(c.player)
                && !AbilityRuntime.moving(c.player) && !KnockbackBuffer.recentlyForced(c.player)) {
            double moved = pos.subtract(memory.lastPosition).multiply(1, 0, 1).length();
            if (moved <= STEP_LIMIT) memory.distance += moved;
        }
        memory.lastPosition = pos;
        if (memory.distance >= CHARGE_DISTANCE) {
            memory.distance = 0;
            memory.count = 1;
            c.data.setBuff("sulan_passive_ready", 1);
        }
        if (memory.count > 0) com.myriad.character.ability.AbilityVisuals.ready(c);
    }

    @Override public void onHit(AbilityRuntime.Hit hit) {
        if (!hit.basic()) return;
        Cast c = AbilityRuntime.context(hit.player(), this);
        var memory = c.memory();
        if (memory.count == 0) return;
        memory.count = 0;
        WindVisuals.cut(c, hit.target(), .85, 145);
        c.data.setBuff("sulan_passive_ready", 0);
        c.damage(hit.target(), DAMAGE.value(c), DAMAGE.kind(), false);
    }

    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
