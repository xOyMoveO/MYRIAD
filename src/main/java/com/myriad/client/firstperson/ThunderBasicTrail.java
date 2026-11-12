package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.tianxing.rework.ThunderFlipbook;
import java.util.List;
import org.joml.Vector3f;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** White/cyan electric edge and blue painted wake, not an independent sword projectile. */
public final class ThunderBasicTrail {
    private static final AttackTrailMesh.Style STYLE=new AttackTrailMesh.Style(THUNDER_PIXEL_SLASH,THUNDER_PIXEL_BOLT,0xFFFFFF,0xB5F5FF,.82f,.018);
    private ThunderBasicTrail(){}
    public static List<Quad> mesh(ThunderSwordMotion motion,double now,boolean left){
        return mesh(motion,now,left,0);
    }
    public static List<Quad> mesh(ThunderSwordMotion motion,double now,boolean left,float equip){
        if(motion.progress(now)<0)return List.of();
        var samples=AttackTrailMesh.samples(now,motion.started(),motion.duration(),.22,.62,t->{
            var f=motion.frame(t);var m=ThunderSwordRig.wrist(f.pose(),equip,left).mul(ThunderSwordRig.blade(f,left));
            return AttackTrailMesh.slice(m,new Vector3f(0,.40f,0),new Vector3f(0,1.40625f,0),t);
        });
        var a=ThunderFlipbook.loop(0,now,0,false);var b=ThunderFlipbook.loop(0,now,3,false);
        return AttackTrailMesh.build(samples,now,STYLE,new AttackTrailMesh.Cell(a.u0(),a.u1(),a.v0(),a.v1()),new AttackTrailMesh.Cell(b.u0(),b.u1(),b.v0(),b.v1()));
    }
}
