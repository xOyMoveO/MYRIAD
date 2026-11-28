package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class SpiritChimeSkill extends AbilitySkill {
    private static final SupportAmount HEAL = new SupportAmount(SupportEffect.HEAL, SupportScaling.TOTAL_SPELL, 18, 8.4f, .15f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(HEAL); }

 public SpiritChimeSkill(Definition definition){super(definition);}

@Override public void onHit(AbilityRuntime.Hit h){
    Cast c=AbilityRuntime.context(h.player(),this);if(!c.hero(h.target())||c.memory().cooldown>c.now())return;
    c.memory().cooldown=c.now()+120;
    AbilityVisuals.seal(c,c.player.position(),1.3,false);
    for(var ally:c.allies(c.player.position(),6)){
        c.heal(ally,HEAL.value(c));c.slow(ally,-.1f,20);
        AbilityVisuals.following(c,AbilityVfxGeometry.SEAL,ally,.66,2,8);
    }
}
@Override public void release(Cast c){}
}

