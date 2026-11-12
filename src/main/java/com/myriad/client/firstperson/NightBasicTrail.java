package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.yingxiao.rework.NightFlipbook;
import java.util.List;
import org.joml.Vector3f;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Gray shadow wisps and silver tip streaks for the dagger's actual three paths. */
public final class NightBasicTrail {
    private static final AttackTrailMesh.Style STYLE=new AttackTrailMesh.Style(NIGHT_GRAY_WAKE,NIGHT_GRAY_BLADE,0xFFFFFF,0xF5F7FF,.82f,.014);
    private NightBasicTrail(){}
    public static List<Quad> mesh(NightDaggerMotion motion,double now,boolean left){
        if(motion.progress(now)<0)return List.of();
        int cut=motion.index();double from=cut==0?.16:cut==1?.30:.34,to=cut==0?.46:cut==1?.70:.66;
        var samples=AttackTrailMesh.samples(now,motion.started(),motion.duration(),from,to,t->{
            var p=motion.sample(t);var m=NightDaggerRig.wrist(p,left).mul(NightDaggerRig.blade(left,p.grip()));
            return AttackTrailMesh.slice(m,new Vector3f(.02f,.18f,0),new Vector3f(.06375f,.5925f,0),t);
        });
        var a=NightFlipbook.loop(NIGHT_GRAY_WAKE,0,now,0);var b=NightFlipbook.loop(NIGHT_GRAY_BLADE,0,now,3);
        return AttackTrailMesh.build(samples,now,STYLE,new AttackTrailMesh.Cell(a.u0(),a.u1(),a.v0(),a.v1()),new AttackTrailMesh.Cell(b.u0(),b.u1(),b.v0(),b.v1()));
    }
}
