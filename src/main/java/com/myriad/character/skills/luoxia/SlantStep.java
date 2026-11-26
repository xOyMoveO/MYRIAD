package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.InputFailure;
import net.minecraft.sounds.SoundEvents;

final class SlantStep extends LuoxiaSkill {
    SlantStep(Definition definition) { super(definition); }
    @Override public boolean canRelease(Cast c) { return super.canRelease(c) && c.data.getBuff("g1_root") <= 0; }
    @Override public InputFailure releaseRejection(Cast c) {
        return c.data.getBuff("g1_root") > 0 ? InputFailure.ROOT : super.releaseRejection(c);
    }
    @Override public void dashPose(Cast c, DashPose event, net.minecraft.world.phys.Vec3 direction) {
        boolean ended = event == DashPose.CANCEL || event == DashPose.FINISH;
        LuoxiaBowPose.skill(c, com.myriad.network.LuoxiaBowPosePayload.Phase.STEP, ended ? 1 : 0, direction.normalize());
    }
    @Override public void release(Cast c) {
        var movement = c.player.getLastClientMoveIntent().multiply(1, 0, 1);
        var forward = c.forward(1);
        var direction = movement.lengthSqr() < .01 ? forward.scale(-1) : movement.normalize();
        var last = new net.minecraft.world.phys.Vec3[]{c.player.position()};
        LuoxiaVfx.sound(c.player, SoundEvents.PLAYER_ATTACK_SWEEP, .38f, 1.42f);
        c.actionDash(direction.scale(3), Math.max(4, 8 - c.rank / 2), .05,
                (t, first) -> {}, () -> LuoxiaVfx.step(c, last[0], c.player.position()), false, false);
        c.whileActive(45, age -> {
            var now = c.player.position();
            LuoxiaVfx.step(c, last[0], now);
            last[0] = now;
            return com.myriad.character.ability.AbilityRuntime.moving(c.player);
        });
    }
}
