package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.combat.DamageCalc.Kind.*;
/** Independent reusable branch. */
public final class EchoBeaconSkill extends AbilitySkill {
 public EchoBeaconSkill(Definition definition){super(definition);}

@Override public boolean needsPoint(){return true;}
@Override public double pointRange(Cast c){return 14;}
@Override public double previewRadius(Cast c){return 4;}
@Override public void release(Cast c){
    Vec3 at=c.point;var hit=new java.util.HashSet<java.util.UUID>(); Vec3 facing=c.forward(1);
    c.tickFor(160,a->{
        if(a%20==0){
            for(var ally:c.allies(at,4))c.shield(ally,c.value(65,18,.3f),25);
            AbilityVisuals.burst(c,at,4);
        }
        if(a%4==0)for(var t:c.enemies(at,4))if(hit.add(t.getUUID())){
            c.damage(t,c.value(65,20,.45f),MAGIC);c.slow(t,.3f,20);AbilityVisuals.burst(c,t.position(),.6);
        }
        if(a==80)beacon(c,at,facing);
    });
}
@Override public void present(Cast c){WarSpiritHandCues.cast(c);AbilityVisuals.field(c,c.point,4,80);}
@Override public void selectionPose(Cast c,boolean selecting){WarSpiritHandCues.aim(c,selecting);}
private static void beacon(Cast c,Vec3 at,Vec3 forward){
    Vec3 foot=at.add(0,.06,0);
    AbilityVisuals.emit(c,AbilityVfxGeometry.SEAL,foot,foot.add(forward),4,360,80,false);
}
}

