package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class SpiritChimeSkill extends AbilitySkill {
    public static final int INTERNAL_COOLDOWN = 120;
    public static final double ALLY_RANGE = 6;
    public static final float HASTE = .1f;
    public static final int HASTE_TICKS = 20;

    @Override public java.util.Map<String, Number> descriptionFields(int rank) {
        return java.util.Map.of(
            "procSeconds", INTERNAL_COOLDOWN / 20.0,
            "allyRange", ALLY_RANGE,
            "hastePercent", Math.round(100 * HASTE),
            "hasteSeconds", HASTE_TICKS / 20.0);
    }

    private static final SupportAmount HEAL = new SupportAmount(SupportEffect.HEAL, SupportScaling.TOTAL_SPELL, 18, 8.4f, .15f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(HEAL); }

 public SpiritChimeSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){
    Cast c=AbilityRuntime.context(h.player(),this);if(!c.hero(h.target())||c.memory().cooldown>c.now())return;
    c.memory().cooldown=c.now()+INTERNAL_COOLDOWN;
    AbilityVisuals.seal(c,c.player.position(),1.3,false);
    for(var ally:c.allies(c.player.position(),ALLY_RANGE)){
        c.heal(ally,HEAL.value(c));c.slow(ally,-HASTE,HASTE_TICKS);
        AbilityVisuals.following(c,AbilityVfxGeometry.SEAL,ally,.66,2,8);
    }
}
@Override public void release(Cast c){}
}

