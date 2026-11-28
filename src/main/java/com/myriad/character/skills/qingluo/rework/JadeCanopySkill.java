package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class JadeCanopySkill extends AbilitySkill {
    private static final SupportAmount HEAL = new SupportAmount(SupportEffect.HEAL, SupportScaling.TOTAL_SPELL, 100, 43.75f, .4f);
    private static final SupportAmount SHIELD = new SupportAmount(SupportEffect.SHIELD, SupportScaling.TOTAL_SPELL, 200, 81.25f, .65f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(HEAL, SHIELD); }

 public JadeCanopySkill(Definition definition){super(definition);}

 @Override public void release(Cast c){StaffHandCues.release(c);for(var ally:c.allies(c.player.position(),8)){HerbVisuals.heal(c,ally,HEAL.value(c),.85);c.shield(ally,SHIELD.value(c),100);c.slow(ally,-.2f,60);}}
@Override public void present(Cast c){AbilityVisuals.field(c,c.player.position(),8,24);}
}

