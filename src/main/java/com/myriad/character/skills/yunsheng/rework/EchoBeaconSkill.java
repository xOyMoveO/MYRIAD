package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class EchoBeaconSkill extends AbilitySkill {
    public static final double RANGE = 14;
    public static final double RADIUS = 4;
    public static final int FIELD_TICKS = 160;
    public static final int SHIELD_INTERVAL = 20;
    public static final int POLL_INTERVAL = 4;
    public static final int SHIELD_TICKS = 25;
    public static final float SLOW = .3f;
    public static final int SLOW_TICKS = 20;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "pointRange", RANGE,
            "radius", RADIUS,
            "fieldSeconds", FIELD_TICKS / 20.0,
            "shieldSeconds", SHIELD_TICKS / 20.0,
            "slowPercent", Math.round(100 * SLOW),
            "slowSeconds", SLOW_TICKS / 20.0);
    }

    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 65, 20.7f, .3f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 65, 25, .45f).segment("skill.myriad.damage.segment.entry", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public EchoBeaconSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return RANGE;}
@Override public double previewRadius(Cast c){return RADIUS;}
@Override public void release(Cast c){
    Vec3 at=c.point;var hit=new java.util.HashSet<java.util.UUID>(); Vec3 facing=c.forward(1);
    c.tickFor(FIELD_TICKS,a->{
        if(a%SHIELD_INTERVAL==0){
            for(var ally:c.allies(at,RADIUS))c.shield(ally,SHIELD.value(c),SHIELD_TICKS);
            AbilityVisuals.burst(c,at,4);
        }
        if(a%POLL_INTERVAL==0)for(var t:c.enemies(at,RADIUS))if(hit.add(t.getUUID())){
            c.damage(t,DAMAGE.value(c),DAMAGE.kind());c.slow(t,SLOW,SLOW_TICKS);AbilityVisuals.burst(c,t.position(),.6);
        }
        if(a==80)beacon(c,at,facing);
    });
}
@Override public void present(Cast c){WarSpiritHandCues.cast(c);AbilityVisuals.field(c,c.point,4,80);}
@Override public void selectionPose(Cast c,boolean selecting){WarSpiritHandCues.aim(c,selecting);}
private static void beacon(Cast c,Vec3 at,Vec3 forward){
    Vec3 foot=at.add(0,.06,0);
    AbilityVisuals.emit(c,AbilityVfxGeometry.SEAL,foot,foot.add(forward),4,360,80,false);
}
}

