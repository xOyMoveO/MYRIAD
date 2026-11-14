package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilityRuntime;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Self-only pull; the destination is shortened to the nearest body-safe point in front of the wall. */
public final class TowCable extends RivetSkill {
    public TowCable(Definition definition) { super(definition); }
    @Override public boolean recastWhileMoving() { return true; }
    @Override public boolean recastWhileRooted() { return true; }
    @Override public boolean recastAvailable(AbilityRuntime.Cast c) { return c.memory().until > c.now() && AbilityRuntime.moving(c.player); }
    @Override public void recast(AbilityRuntime.Cast c) {
        c.memory().until = 0;
        c.data.setBuff("rivet_tow_cable_recast", 0);
        c.stopMotion();
    }
    private Vec3 destination(AbilityRuntime.Cast c) {
        var anchor = TacticalAnchor.anchor(c.player);
        if (anchor == null || anchor.point().distanceToSqr(c.player.position()) > 100.0001) return null;
        var hit = TacticalAnchor.loadedBlocks(c.player.level()).clip(new ClipContext(c.player.getEyePosition(), anchor.point(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.player));
        if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(anchor.point()) > .01) return null;
        // Align the actor's chest with the attachment rather than putting their feet into its wall.
        Vec3 delta = anchor.point().add(Vec3.atLowerCornerOf(anchor.normal().getUnitVec3i()).scale(.45))
                .add(0, -c.player.getBbHeight() * .55, 0).subtract(c.player.position());
        double length = Math.min(5, delta.length());
        if (length < .1) return null;
        Vec3 travel = delta.normalize().scale(length);
        int pieces = Math.max(1, (int) Math.ceil(length / .2));
        Vec3 step = travel.scale(1.0 / pieces);
        for (int index = 0; index < pieces; index++) {
            Vec3 offset = step.scale(index);
            Vec3 next = c.player.position().add(offset).add(step);
            if (!c.player.level().hasChunkAt(net.minecraft.core.BlockPos.containing(next))
                    || !c.player.level().getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(next))
                    || !c.player.level().noCollision(c.player, c.player.getBoundingBox().move(offset).expandTowards(step))) return null;
        }
        return c.player.position().add(travel);
    }
    @Override public boolean canRelease(AbilityRuntime.Cast c) {
        return c.data.getBuff("g1_root") <= 0 && c.data.getBuff("g1_stun") <= 0 && destination(c) != null;
    }
    @Override public void release(AbilityRuntime.Cast c) {
        Vec3 to = destination(c);
        if (to == null) return;
        var anchor = TacticalAnchor.anchor(c.player);
        c.memory().until = Long.MAX_VALUE;
        RivetVfx.cable(c, c.player.getEyePosition(), anchor.point());
        c.actionDash(to.subtract(c.player.position()), 10, .05, (target, first) -> { },
                () -> {
                    c.memory().until = 0;
                    c.data.setBuff("rivet_tow_cable_recast", 0);
                }, false, false);
        c.data.setBuff("rivet_tow_cable_recast", AbilityRuntime.motionRemaining(c.player));
        c.whileActive(2400, age -> {
            if (c.memory().until <= c.now() || !AbilityRuntime.moving(c.player)) {
                c.memory().until = 0;
                c.data.setBuff("rivet_tow_cable_recast", 0);
                return false;
            }
            c.data.setBuff("rivet_tow_cable_recast", Math.max(1, AbilityRuntime.motionRemaining(c.player)));
            if (age % 2 == 0) RivetVfx.cable(c, c.player.getEyePosition(), anchor.point());
            return true;
        });
    }
}
