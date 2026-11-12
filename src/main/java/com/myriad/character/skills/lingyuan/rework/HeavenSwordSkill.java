package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;

/** A stationary, telegraphed overhead chop, not a moving projectile. */
public final class HeavenSwordSkill extends AbilitySkill {
    public static final int CHARGE_TICKS=24,CHOP_TICKS=6;
    public static final double REACH=15,WIDTH=3;
    public HeavenSwordSkill(Definition definition){super(definition);}

    @Override public void release(Cast c){
        Vec3 origin=c.player.position(),forward=c.forward(1);
        c.later(CHARGE_TICKS,true,()->{
            Vec3 chopOrigin=new Vec3(origin.x,c.player.getY(),origin.z);
            SwordVisuals.heavenRelease(c,chopOrigin,forward);
            c.later(CHOP_TICKS,true,()->{
                SwordVisuals.heavenImpact(c,chopOrigin,forward);
                Vec3 end=chopOrigin.add(forward.scale(REACH));
                AABB candidates=new AABB(chopOrigin,end).inflate(WIDTH/2,4,WIDTH/2);
                for(var target:c.player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,candidates,
                        t->c.enemy(t)&&intersects(chopOrigin,forward,t.getBoundingBox())))
                    c.damage(target,c.value(300,100,1.2f),MAGIC);
            });
        });
    }
    @Override public void present(Cast c){SwordVisuals.heaven(c);}

    /** Rectangle vs body AABB: four separating axes, no rounded end caps or center-only misses. */
    public static boolean intersects(Vec3 origin,Vec3 direction,AABB box){
        Vec3 f=direction.multiply(1,0,1).normalize();if(f.lengthSqr()<1E-8)f=new Vec3(0,0,1);
        Vec3 s=new Vec3(-f.z,0,f.x),center=origin.add(f.scale(REACH/2));
        if(box.maxY<origin.y-.5||box.minY>origin.y+4)return false;
        Vec3 delta=box.getCenter().subtract(center);
        double ex=(box.maxX-box.minX)/2,ez=(box.maxZ-box.minZ)/2;
        return Math.abs(delta.dot(f))<=REACH/2+Math.abs(f.x)*ex+Math.abs(f.z)*ez
                &&Math.abs(delta.dot(s))<=WIDTH/2+Math.abs(s.x)*ex+Math.abs(s.z)*ez
                &&Math.abs(delta.x)<=ex+REACH/2*Math.abs(f.x)+WIDTH/2*Math.abs(s.x)
                &&Math.abs(delta.z)<=ez+REACH/2*Math.abs(f.z)+WIDTH/2*Math.abs(s.z);
    }
}

