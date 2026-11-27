package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityTargeting;
import com.myriad.combat.InputFailure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.List;

/** Independent reusable skill module; tuning metadata is supplied by the role binding. */
public final class ThunderBlinkSkill extends AbilitySkill {
    private static final double HORIZONTAL = 6, RISE = 2.5, DROP = 4, FOOT_GAP = .025;
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(40, 15, .45f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

    public ThunderBlinkSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){com.myriad.character.ability.AbilityVisuals.burst(c,c.player.position(),1.2);}
    @Override public boolean canRelease(Cast c) {
        if(c.data.getBuff("g1_root")>0)return false;
        if(c.point==null)c.point=landing(c.player);
        return legalLanding(c.player,c.point);
    }
    @Override public InputFailure releaseRejection(Cast c) {
        return c.data.getBuff("g1_root")>0?InputFailure.ROOT:InputFailure.POINT;
    }

    public static Vec3 landing(Player p) {
        Vec3 eye=p.getEyePosition(), aim=p.getLookAngle();
        var hit=trace(p,eye,eye.add(aim.scale(9)));
        if(hit.getType()==HitResult.Type.BLOCK && hit.getDirection()==Direction.UP
                && hit.getLocation().subtract(p.position()).horizontalDistanceSqr()<=HORIZONTAL*HORIZONTAL) {
            Vec3 point=hit.getLocation().add(0,FOOT_GAP,0);
            return legalLanding(p,point)?point:null;
        }
        Vec3 flat=aim.multiply(1,0,1).normalize();
        if(flat.lengthSqr()<.01)return null;
        double distance=HORIZONTAL;
        if(hit.getType()==HitResult.Type.BLOCK) {
            double face=hit.getLocation().subtract(p.position()).horizontalDistance();
            if(hit.getDirection().getAxis().isHorizontal() && face+.42<=HORIZONTAL) {
                Vec3 probe=p.position().add(flat.scale(face+.42));
                Vec3 point=probeFloor(p,probe);
                if(point!=null && point.y>p.getY()+.15 && legalLanding(p,point))return point;
            }
            distance=Math.min(distance,face-.34);
        }
        if(aim.y>.55)return null;
        for(;distance>=.55;distance-=.4) {
            Vec3 probe=p.position().add(flat.scale(distance));
            Vec3 point=probeFloor(p,probe);
            if(point!=null && legalLanding(p,point))return point;
        }
        return null;
    }

    private static Vec3 probeFloor(Player p,Vec3 probe) {
        if(!p.level().hasChunkAt(BlockPos.containing(probe)))return null;
        var floor=trace(p,probe.add(0,RISE+FOOT_GAP,0),probe.add(0,-DROP-.01,0));
        return floor.getType()==HitResult.Type.BLOCK && floor.getDirection()==Direction.UP
                ?floor.getLocation().add(0,FOOT_GAP,0):null;
    }

    public static boolean legalLanding(Player p, Vec3 point) {
        if(point==null || !AbilityTargeting.finite(point))return false;
        Vec3 delta=point.subtract(p.position());
        if(delta.horizontalDistanceSqr()>HORIZONTAL*HORIZONTAL+.0001
                ||delta.y>RISE+FOOT_GAP+.0001||delta.y<-DROP||!AbilityTargeting.canStand(p,point))return false;
        var body=p.getBoundingBox().move(delta);
        if(p.level().containsAnyLiquid(body) || !p.level().getWorldBorder().isWithinBounds(body))return false;
        var support=trace(p,point.add(0,.015,0),point.add(0,-.065,0));
        if(support.getType()!=HitResult.Type.BLOCK || support.getDirection()!=Direction.UP)return false;
        Vec3 eye=p.getEyePosition(), feet=point.add(0,.10,0);
        var obstructed=trace(p,eye,feet);
        if(obstructed.getType()==HitResult.Type.MISS)return true;
        // A raised step can hide its own top; only its supporting face may occlude the low ray.
        if(delta.y<=.15 || !obstructed.getBlockPos().equals(support.getBlockPos()))return false;
        Vec3 upper=point.add(0,p.getEyeHeight(),0);
        return trace(p,eye,upper).getType()==HitResult.Type.MISS;
    }

    private static BlockHitResult trace(Player p,Vec3 from,Vec3 to) {
        return p.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
    }
 @Override public void release(Cast c){ThunderSkillPresentation.play(c,com.myriad.network.ThunderSkillViewPayload.GROUND,14);com.myriad.character.ability.AbilityVisuals.burst(c,c.player.position(),.6);
 com.myriad.character.ability.AbilityVisuals.emit(c,com.myriad.character.ability.AbilityVfxGeometry.TRAIL,c.player.position().add(0,.4,0),c.point.add(0,.4,0),.22,0,8,false);
 c.player.teleportTo(c.point.x,c.point.y,c.point.z);ThunderAudio.play(c,ThunderAudio.Cue.CAST);c.area(c.point,2.5,DAMAGE.value(c),DAMAGE.kind());}
}
