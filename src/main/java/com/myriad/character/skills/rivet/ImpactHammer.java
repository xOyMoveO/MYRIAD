package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.DamageCalc;
import net.minecraft.world.entity.LivingEntity;
import java.util.Comparator;

/** The broad hammer hits several enemies; only the nearest aim-line contact is pushed. */
public final class ImpactHammer extends RivetSkill {
    public ImpactHammer(Definition definition) { super(definition); }
    @Override public int commitWindupTicks() { return 6; }
    @Override public void preparing(AbilityRuntime.Cast c) { RivetVfx.hammerWindup(c); }
    @Override public void release(AbilityRuntime.Cast c) {
        var direction = c.forward(1);
        var targets = c.enemies(c.player.position(), 3.8).stream()
                .filter(t -> BasicAttackSystem.inMeleeArc(c.player.position(), direction, t.getBoundingBox(), 3,
                        Math.toRadians(55))).toList();
        var eye = c.player.getEyePosition();
        var rayEnd = eye.add(c.player.getLookAngle().scale(3.5));
        LivingEntity primary = targets.stream()
                .filter(t -> t.getBoundingBox().inflate(.35).clip(eye, rayEnd).isPresent())
                .min(Comparator.comparingDouble(t -> t.getBoundingBox().inflate(.35).clip(eye, rayEnd)
                        .orElse(rayEnd).distanceToSqr(eye))).orElse(null);
        boolean primaryHit = false;
        for (var target : targets) {
            boolean effective = c.damage(target, c.value(75, 20, .5f), DamageCalc.Kind.PHYSICAL);
            if (target == primary) primaryHit = effective;
        }
        RivetVfx.hammer(c);
        if (primaryHit && primary != null && primary.isAlive()) {
            double distance = HoldProtocol.extendsHammer(c.player, primary) ? 2.5 : 2;
            RivetPush.start(c, primary, direction, distance, c.value(29, 8, .2f), 10, 16, true);
        }
    }
}
