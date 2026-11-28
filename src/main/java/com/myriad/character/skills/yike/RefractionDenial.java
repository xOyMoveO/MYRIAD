package com.myriad.character.skills.yike;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;

import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import java.util.List;
import java.util.Map;

/** A committed warning is independent of the character's later controls. */
public final class RefractionDenial extends AbilitySkill {
    public static final double RANGE = 14;
    public static final float BLAST_RADIUS = 2.5f;
    public static final int WARNING_TICKS = 11;
    public static final float MOVEMENT_SLOW = .30f;
    public static final int SLOW_TICKS = 25;
    public static final float RECOVERY_MOVEMENT = .5f;

    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.spellPower(DamageCalc.Kind.MAGIC, 70, 22.5f, .35f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public RefractionDenial(Definition d){super(d);}
    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("range_blocks", RANGE, "blast_radius_blocks", BLAST_RADIUS,
                "warning_seconds", WARNING_TICKS / 20.0, "slow_percent", MOVEMENT_SLOW * 100,
                "slow_seconds", SLOW_TICKS / 20.0, "recovery_move_percent", RECOVERY_MOVEMENT * 100,
                "pause_seconds", YikeStance.PAUSE_FOR_W_TICKS / 20.0);
    }
    @Override public int inputRecoveryTicks(){return 0;}
    @Override public boolean needsPoint(){return true;}
    @Override public double pointRange(AbilityRuntime.Cast c){return RANGE;}
    @Override public double previewRadius(AbilityRuntime.Cast c){return BLAST_RADIUS;}
    @Override public boolean interruptPreparation(AbilityRuntime.Cast c){return AbilityRuntime.preparingSkill(c.player).equals("yike_rainbow_shell");}
    @Override public boolean canRelease(AbilityRuntime.Cast c){return !PrismaticBeam.active(c.player);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void release(AbilityRuntime.Cast c){
        var at=c.point.add(0,.06,0);YikeStance.pauseForW(c.player);
        // Recovery owns movement only, not the independently placed warning.
        c.later(WARNING_TICKS,true,RECOVERY_MOVEMENT,()->YikeStance.refreshMovement(c.player));YikeStance.refreshMovement(c.player);
        WeaponPosePayload.play(c.player, Move.DENIAL, 7);
        YikeVfx.event(c,"denial_warning",at,at,BLAST_RADIUS,WARNING_TICKS);YikeAudio.play(c.player,"warning",at,.4f,1.2f);
        c.later(WARNING_TICKS,false,()->{
            for(var target:YikeGeometry.sphere(c,at,BLAST_RADIUS)){c.damage(target,DAMAGE.value(c),DamageCalc.Kind.MAGIC);c.slow(target,MOVEMENT_SLOW,SLOW_TICKS);}
            YikeVfx.event(c,"denial_burst",at,at,BLAST_RADIUS,9);YikeAudio.play(c.player,"impact",at,.65f,1.15f);
        });
    }
}
