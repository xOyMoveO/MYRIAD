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
public final class ThunderThirdStrikeSkill extends AbilitySkill {
    public static final int REQUIRED_HITS = 3, HIT_WINDOW = 80, SPLASH_LIMIT = 2;
    public static final double SPLASH_RADIUS = 3;
    private static final AbilityDamageFormula STRIKE =
            AbilityDamageFormula.spellPower(TRUE, 0, 0, .24f).withScaleGrowth(.08f).segment("skill.myriad.damage.segment.primary", 1);
    private static final AbilityDamageFormula SPLASH =
            AbilityDamageFormula.spellPower(MAGIC, 20, 9.6f, .2f).segment("skill.myriad.damage.segment.splash", 1);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(STRIKE, SPLASH);
    }

    public ThunderThirdStrikeSkill(Definition definition){super(definition);}
    @Override public void onHit(AbilityRuntime.Hit h){if(!h.basic())return;Cast c=AbilityRuntime.context(h.player(),this);var m=c.memory();
 if(!h.target().getUUID().equals(m.target)||c.now()-m.last>HIT_WINDOW)m.count=0;m.target=h.target().getUUID();m.last=c.now();
 if(++m.count<REQUIRED_HITS)return;m.count=0;ThunderAudio.play(c,ThunderAudio.Cue.HIT,h.target().position());com.myriad.character.ability.AbilityVisuals.pillar(c,h.target().position(),2.8);c.damage(h.target(),STRIKE.value(c),STRIKE.kind(),false);
 c.enemies(h.target().position(),SPLASH_RADIUS).stream().filter(t->t!=h.target()).limit(SPLASH_LIMIT).forEach(t->{com.myriad.character.ability.AbilityVisuals.beam(c,h.target().position().add(0,.7,0),t.position().add(0,.7,0),.08);c.damage(t,SPLASH.value(c),SPLASH.kind(),false);});}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
