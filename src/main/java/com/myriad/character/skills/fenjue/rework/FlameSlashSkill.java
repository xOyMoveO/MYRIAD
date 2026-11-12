package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.SkillTargets;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.LivingEntity;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class FlameSlashSkill extends AbilitySkill {
    public FlameSlashSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){
        var p=FlameSlashAim.recipe(c.player.getId(),c.player.getEyePosition(),c.player.getLookAngle(),c.player.getYRot(),c.now());
        com.myriad.character.ability.AbilityVisuals.emit(c,p.kind(),p.from(),p.to(),p.radius(),p.angle(),p.life(),false);
        FlameSlashAudio.play(c);
    }
    @Override public void release(Cast c){
        // Snapshot the same eye/look/yaw as present(). Turning or walking must not redirect an old arc.
        var visual=FlameSlashAim.recipe(c.player.getId(),c.player.getEyePosition(),c.player.getLookAngle(),c.player.getYRot(),c.now());
        float damage=c.value(65,18,.7f)*.5f;
        for(int cut=0;cut<2;cut++){
            var sweep=FlameSlashAim.sweep(visual,cut);
            // Owner-bound tasks are cancelled by death, role/world changes, logout, reset and mod-off.
            // Four ticks between strokes also respects the normal three-tick hurt immunity window.
            c.later(FlameSlashAim.hitTick(cut),false,()->{
                for(var target:c.player.level().getEntitiesOfClass(LivingEntity.class,sweep.bounds(),t->SkillTargets.enemy(c.player,t)))
                    if(sweep.contains(target.getBoundingBox())&&c.player.level().clip(new ClipContext(visual.from(),target.getBoundingBox().getCenter(),
                            ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player)).getType()==HitResult.Type.MISS)
                        c.damage(target,damage,PHYSICAL);
            });
        }
    }
}
