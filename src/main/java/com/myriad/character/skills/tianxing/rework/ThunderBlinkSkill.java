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
public final class ThunderBlinkSkill extends AbilitySkill {
    public ThunderBlinkSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.burst(c,c.player.position(),1.2);}
    @Override public boolean canRelease(Cast c){if(c.data.getBuff("g1_root")>0)return false;
        c.point=com.myriad.character.ability.AbilityTargeting.ground(c.player,6,true);return c.point!=null;}
 @Override public void release(Cast c){ThunderSkillPresentation.play(c,com.myriad.network.ThunderSkillViewPayload.GROUND,14);com.myriad.character.ability.AbilityVisuals.burst(c,c.player.position(),.6);
 com.myriad.character.ability.AbilityVisuals.emit(c,com.myriad.character.ability.AbilityVfxGeometry.TRAIL,c.player.position().add(0,.4,0),c.point.add(0,.4,0),.22,0,8,false);
 c.player.teleportTo(c.point.x,c.point.y,c.point.z);ThunderAudio.play(c,ThunderAudio.Cue.CAST);c.area(c.point,2.5,c.value(40,12,.45f),PHYSICAL);}
}
