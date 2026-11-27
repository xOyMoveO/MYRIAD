package com.myriad.character.skills.yingxiao.rework;

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

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class NightAmbushSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(30, 14.4f, .25f, .25f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public NightAmbushSkill(Definition definition){super(definition);}
    @Override public void onHit(AbilityRuntime.Hit h){Cast c=AbilityRuntime.context(h.player(),this);if(!c.hero(h.target()))return;var m=c.memory();if(m.targets.getOrDefault(h.target().getUUID(),0L)>c.now())return;
 if(c.enemies(h.target().position(),4).stream().anyMatch(t->t!=h.target()&&c.hero(t)))return;m.targets.put(h.target().getUUID(),c.now()+160);com.myriad.character.ability.AbilityVisuals.burst(c,h.target().position().add(0,h.target().getBbHeight()*.45,0),.65);c.damage(h.target(),DAMAGE.value(c),DAMAGE.kind(),false);}
    @Override public void release(Cast cast){} // Passive: event hooks above own the effect.
}
