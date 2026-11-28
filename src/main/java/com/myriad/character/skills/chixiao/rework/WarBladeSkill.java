package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Independent reusable branch. */
public final class WarBladeSkill extends AbilitySkill {
    public static final int WINDOW_TICKS = 120, BONUS_HITS = 3, HIT_INTERVAL_TICKS = 4;
    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 230, 87.5f, .7f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(45, 23, .3f, .8f / BONUS_HITS).segment("skill.myriad.damage.segment.bonus_basic", BONUS_HITS);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public WarBladeSkill(Definition definition){super(definition);}

@Override public void release(Cast c){
    c.shield(c.player,SHIELD.value(c),WINDOW_TICKS);
    var m=c.memory();m.until=c.now()+WINDOW_TICKS;m.count=BONUS_HITS;c.buff(getId()+"_ready",WINDOW_TICKS);
    long until=m.until;
    AbilityVisuals.following(c,AbilityVfxGeometry.SEAL,c.player,.62,m.count,7);
    c.whileActive(WINDOW_TICKS,age->{
        if(m.until!=until||m.count<=0||c.now()>=until)return false;
        if(age>0&&age%7==0)AbilityVisuals.following(c,AbilityVfxGeometry.SEAL,c.player,.62,m.count,7);
        return true;
    });
}
@Override public void onHit(AbilityRuntime.Hit h){if(!h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();if(m.until<=c.now()||m.count<=0||c.now()-m.last<HIT_INTERVAL_TICKS)return;m.last=c.now();m.count--;c.damage(h.target(),DAMAGE.value(c),DAMAGE.kind(),false);AbilityVisuals.arc(c,c.player.position(),3,80);if(m.count==0)c.buff(getId()+"_ready",0);}
@Override public void present(Cast c){WarSpiritHandCues.cast(c);AbilityVisuals.field(c,c.player.position(),2,16);}
}

