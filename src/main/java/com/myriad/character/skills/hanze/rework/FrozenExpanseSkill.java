package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import static com.myriad.character.ability.AbilityVfxGeometry.PILLAR;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class FrozenExpanseSkill extends AbilitySkill {
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 45, 25, .22f).segment("skill.myriad.damage.segment.pulse", 6);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public FrozenExpanseSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 7;}
@Override public void selectionPose(Cast c,boolean active){StaffHandCues.select(c,active);}
@Override public void release(Cast c){StaffHandCues.release(c);Vec3 at=c.point;c.repeat(120,20,i->{
    c.pulse(at,7,DAMAGE.value(c),DAMAGE.kind());
    for(var t:c.enemies(at,7))c.slow(t,.35f,20);
    Vec3 outward=new Vec3(Math.cos(i*2),0,Math.sin(i*2));
    Vec3 position=at.add(outward.scale(3));
    var level=c.player.level();
    if(!level.hasChunkAt(BlockPos.containing(position)))return;
    var floor=level.clip(new ClipContext(position.add(0,1.25,0),position.add(0,-2.5,0),
            ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player));
    if(floor.getType()!=HitResult.Type.BLOCK||floor.getDirection()!=Direction.UP)return;
    Vec3 foot=floor.getLocation().add(0,.055,0);
    AbilityVisuals.emit(c,PILLAR,foot,foot.add(outward).add(0,2.7,0),.75,0,14,false);
});}
@Override public void present(Cast c){AbilityVisuals.field(c,c.point,7,120);}
}

