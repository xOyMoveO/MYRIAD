package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.SkillTargets;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;


/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class FlameSlashSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.purchased(com.myriad.combat.DamageCalc.Kind.PHYSICAL, 104, 13.5f, .5f).scaled(.5f).segment("skill.myriad.damage.segment.sweep", 2);

    @Override public String singleTargetTotalKey() { return "skill.myriad.damage.stable_total"; }

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

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
        float damage=DAMAGE.value(c);
        for(int cut=0;cut<2;cut++){
            var sweep=FlameSlashAim.sweep(visual,cut);
            // Owner-bound tasks are cancelled by death, role/world changes, logout, reset and mod-off.
            // Four ticks between strokes also respects the normal three-tick hurt immunity window.
            c.actionLater(FlameSlashAim.hitTick(cut),()->{
                for(var target:c.player.level().getEntitiesOfClass(LivingEntity.class,sweep.bounds(),t->SkillTargets.enemy(c.player,t)))
                    if(sweep.contains(target.getBoundingBox())&&c.player.level().clip(new ClipContext(visual.from(),target.getBoundingBox().getCenter(),
                            ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player)).getType()==HitResult.Type.MISS)
                        c.damage(target,damage,DAMAGE.kind());
            });
        }
    }
}
