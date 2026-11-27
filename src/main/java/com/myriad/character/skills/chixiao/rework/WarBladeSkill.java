package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Independent reusable branch. */
public final class WarBladeSkill extends AbilitySkill {
    private static final SupportAmount SHIELD = new SupportAmount("shield", 230, 87.5f, .7f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(45, 23, .3f, .8f / 3).segment("skill.myriad.damage.segment.bonus_basic", 3);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public WarBladeSkill(Definition definition){super(definition);}

@Override public void release(Cast c){
    c.shield(c.player,SHIELD.value(c),120);
    var m=c.memory();m.until=c.now()+120;m.count=3;c.buff(getId()+"_ready",120);
    long until=m.until;
    AbilityVisuals.following(c,AbilityVfxGeometry.SEAL,c.player,.62,m.count,7);
    c.whileActive(120,age->{
        if(m.until!=until||m.count<=0||c.now()>=until)return false;
        if(age>0&&age%7==0)AbilityVisuals.following(c,AbilityVfxGeometry.SEAL,c.player,.62,m.count,7);
        return true;
    });
}
@Override public void onHit(AbilityRuntime.Hit h){if(!h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();if(m.until<=c.now()||m.count<=0||c.now()-m.last<4)return;m.last=c.now();m.count--;c.damage(h.target(),DAMAGE.value(c),DAMAGE.kind(),false);AbilityVisuals.arc(c,c.player.position(),3,80);if(m.count==0)c.buff(getId()+"_ready",0);}
@Override public void present(Cast c){WarSpiritHandCues.cast(c);AbilityVisuals.field(c,c.player.position(),2,16);}
}

