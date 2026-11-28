package com.myriad.character.skills.hanze.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

import static com.myriad.character.ability.AbilityVfxGeometry.PILLAR;
import static com.myriad.combat.DamageCalc.Kind.*;

/** Independent branch; no gameplay switches in the batch registry. */
public final class FrozenExpanseSkill extends AbilitySkill {
    public static final int DURATION_TICKS=120, PULSE_TICKS=20, SLOW_TICKS=20;
    public static final double POINT_RANGE=14, RADIUS=7;
    public static final float SLOW=.35f;

    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.spellPower(MAGIC, 45, 28.75f, .22f).segment("skill.myriad.damage.segment.pulse", DURATION_TICKS / PULSE_TICKS);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    @Override public Map<String, Number> descriptionFields(int rank) {
        return Map.of("point_range", POINT_RANGE, "radius", RADIUS, "duration", DURATION_TICKS / 20.0, "slow_pct", SLOW * 100);
    }

    public FrozenExpanseSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return POINT_RANGE;}
@Override public double previewRadius(Cast c){return RADIUS;}
@Override public void selectionPose(Cast c,boolean active){StaffHandCues.select(c,active);}
@Override public void release(Cast c){StaffHandCues.release(c);Vec3 at=c.point;c.repeat(DURATION_TICKS,PULSE_TICKS,i->{
    c.pulse(at,RADIUS,DAMAGE.value(c),DAMAGE.kind());
    for(var t:c.enemies(at,RADIUS))c.slow(t,SLOW,SLOW_TICKS);
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
@Override public void present(Cast c){AbilityVisuals.field(c,c.point,RADIUS,DURATION_TICKS);}
}

