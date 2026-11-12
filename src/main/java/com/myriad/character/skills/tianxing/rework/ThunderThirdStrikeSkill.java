package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class ThunderThirdStrikeSkill extends AbilitySkill {
    public ThunderThirdStrikeSkill(Definition definition){super(definition);}
    @Override public void onHit(AbilityRuntime.Hit h){if(!h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();
 if(!h.target().getUUID().equals(m.target)||c.now()-m.last>80)m.count=0;m.target=h.target().getUUID();m.last=c.now();
 if(++m.count<3)return;m.count=0;ThunderAudio.play(c,ThunderAudio.Cue.HIT,h.target().position());com.myriad.character.ability.AbilityVisuals.pillar(c,h.target().position(),2.8);c.damage(h.target(),(.24f+.08f*(c.rank-1))*c.strength,TRUE,false);
 c.enemies(h.target().position(),3).stream().filter(t->t!=h.target()).limit(2).forEach(t->{com.myriad.character.ability.AbilityVisuals.beam(c,h.target().position().add(0,.7,0),t.position().add(0,.7,0),.08);c.damage(t,c.value(20,8,.2f),MAGIC,false);});}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
