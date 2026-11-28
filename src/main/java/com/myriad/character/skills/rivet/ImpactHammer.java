package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.DamageCalc;
import net.minecraft.world.entity.LivingEntity;
import java.util.Comparator;
import java.util.List;

/** The broad hammer hits several enemies; only the nearest aim-line contact is pushed. */
public final class ImpactHammer extends RivetSkill {
    public static final int WALL_ROOT_TICKS = 10, ANCHOR_ROOT_TICKS = 16;
    public static final double HIT_RANGE = 3;
    public static final double PUSH_DISTANCE = 2, ANCHORED_PUSH_DISTANCE = 2.5;
    private static final AbilityDamageFormula DIRECT = AbilityDamageFormula.physicalFromSpell(75, 25, .50f, 5f / 14)
            .segment("skill.myriad.damage.direct_hit", 1);
    private static final AbilityDamageFormula WALL = AbilityDamageFormula.physicalFromSpell(29, 10, .20f, 1f / 7)
            .segment("skill.myriad.damage.wall_collision", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DIRECT, WALL);
    }

    public ImpactHammer(Definition definition) { super(definition); }
    @Override public int commitWindupTicks() { return 6; }
    @Override public void preparing(AbilityRuntime.Cast c) { RivetVfx.hammerWindup(c); }
    @Override public void release(AbilityRuntime.Cast c) {
        var direction = c.forward(1);
        var targets = c.enemies(c.player.position(), 3.8).stream()
                .filter(t -> BasicAttackSystem.inMeleeArc(c.player.position(), direction, t.getBoundingBox(), HIT_RANGE,
                        Math.toRadians(55))).toList();
        var eye = c.player.getEyePosition();
        var rayEnd = eye.add(c.player.getLookAngle().scale(3.5));
        LivingEntity primary = targets.stream()
                .filter(t -> t.getBoundingBox().inflate(.35).clip(eye, rayEnd).isPresent())
                .min(Comparator.comparingDouble(t -> t.getBoundingBox().inflate(.35).clip(eye, rayEnd)
                        .orElse(rayEnd).distanceToSqr(eye))).orElse(null);
        boolean primaryHit = false;
        for (var target : targets) {
            boolean effective = c.damage(target, DIRECT.value(c), DamageCalc.Kind.PHYSICAL);
            if (target == primary) primaryHit = effective;
        }
        RivetVfx.hammer(c);
        if (primaryHit && primary != null && primary.isAlive()) {
            double distance = HoldProtocol.extendsHammer(c.player, primary) ? ANCHORED_PUSH_DISTANCE : PUSH_DISTANCE;
            RivetPush.start(c, primary, direction, distance, WALL.value(c), WALL_ROOT_TICKS, ANCHOR_ROOT_TICKS, true);
        }
    }
}
