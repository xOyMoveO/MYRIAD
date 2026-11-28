package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import java.util.List;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class ThunderResonanceSkill extends AbilitySkill {
    public static final int READY_WINDOW = 80, INTERNAL_COOLDOWN = 120, SPLASH_LIMIT = 2;
    public static final double SPLASH_RADIUS = 3;
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 35, 14.4f, .3f).segment("skill.myriad.damage.segment.primary", 1);
    private static final AbilityDamageFormula SPLASH =
            DAMAGE.scaled(.5f).segment("skill.myriad.damage.segment.splash", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE, SPLASH);
    }

    public ThunderResonanceSkill(Definition definition){super(definition);}
    @Override public void tickPassive(Cast c){if(c.data.getBuff(getId())>0&&c.player.tickCount%6==0)
        com.myriad.character.ability.AbilityVisuals.following(c,com.myriad.character.ability.AbilityVfxGeometry.SHIELD,c.player,.32,7);}
    @Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();
 if(!h.basic()&&c.hero(h.target())&&m.cooldown<=c.now()){m.until=c.now()+READY_WINDOW;m.cooldown=c.now()+INTERNAL_COOLDOWN;c.buff(getId(),READY_WINDOW);}
 else if(h.basic()&&m.until>c.now()){m.until=0;c.buff(getId(),0);ThunderAudio.play(c,ThunderAudio.Cue.HIT,h.target().position());com.myriad.character.ability.AbilityVisuals.pillar(c,h.target().position(),2);c.damage(h.target(),DAMAGE.value(c),DAMAGE.kind(),false);
 c.enemies(h.target().position(),SPLASH_RADIUS).stream().filter(t->t!=h.target()).limit(SPLASH_LIMIT).forEach(t->{com.myriad.character.ability.AbilityVisuals.beam(c,h.target().position().add(0,.7,0),t.position().add(0,.7,0),.06);c.damage(t,SPLASH.value(c),SPLASH.kind(),false);});}}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
