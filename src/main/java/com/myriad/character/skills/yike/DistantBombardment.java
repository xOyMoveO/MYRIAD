package com.myriad.character.skills.yike;

import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import java.util.List;
import java.util.Map;

/** Paid on legal confirmation; an interrupted charge never refunds the ultimate. */
public final class DistantBombardment extends AbilitySkill {
    public static final double RANGE = 26;
    public static final float BLAST_RADIUS = 4;
    public static final int WINDUP_TICKS = 12;

    private static final AbilityDamageFormula DAMAGE = AbilityDamageFormula.spellPower(DamageCalc.Kind.MAGIC, 250, 106.25f, 1.15f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    private static final java.util.Map<java.util.UUID,AbilityRuntime.Cast> CHARGES=new java.util.HashMap<>();
    public static void tick(net.minecraft.server.level.ServerPlayer p){
        var c=CHARGES.get(p.getUUID());if(c==null)return;
        if(c.phase()!=AbilityRuntime.Phase.WINDUP||!SkillChargeSystem.active(p)){CHARGES.remove(p.getUUID());return;}
        YikeVfx.event(c,"heavy_warning",c.point.add(0,.06,0),c.point.add(0,.06,0),BLAST_RADIUS,2);
    }
    public static void clear(net.minecraft.server.level.ServerPlayer p){CHARGES.remove(p.getUUID());}
    public DistantBombardment(Definition d){super(d);}
    @Override public boolean needsPoint(){return true;}
    @Override public double pointRange(AbilityRuntime.Cast c){return RANGE;}
    @Override public double previewRadius(AbilityRuntime.Cast c){return BLAST_RADIUS;}
    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("range_blocks", RANGE, "blast_radius_blocks", BLAST_RADIUS,
                "windup_seconds", WINDUP_TICKS / 20.0,
                "minimum_flight_seconds", Math.ceil(YikeProjectiles.heavyFlightTicks(0)) / 20.0);
    }
    @Override public boolean canRelease(AbilityRuntime.Cast c){return !PrismaticBeam.active(c.player);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void release(AbilityRuntime.Cast c){
        YikeStance.exit(c.player,"ultimate");
        YikeVfx.event(c,"heavy_warning",c.point.add(0,.06,0),c.point.add(0,.06,0),BLAST_RADIUS,2);
        YikeAudio.play(c.player,"heavy_charge",c.player.position(),.65f,.85f);
        CHARGES.put(c.player.getUUID(),c);
        c.windup(WINDUP_TICKS,0,()->{CHARGES.remove(c.player.getUUID());YikeProjectiles.heavy(c,c.point,DAMAGE.value(c));});
    }
}
