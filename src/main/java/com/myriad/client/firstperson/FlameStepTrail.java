package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.fenjue.rework.FlameFlipbook;
import java.util.List;
import org.joml.Vector3f;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** A small local edge streak bridges the hand cut into the endpoint's larger world-space blade image. */
public final class FlameStepTrail {
    private static final AttackTrailMesh.Style STYLE=new AttackTrailMesh.Style(FENJUE_PIXEL_STREAM,FENJUE_PIXEL_SLASH,0xFFFFFF,0xFFE4AD,.88f,.022);
    private FlameStepTrail(){}
    public static List<Quad> mesh(FlameStepMotion motion,double now,boolean left,float equip){
        if(!motion.slashing(now))return List.of();
        var samples=AttackTrailMesh.samples(now,motion.slashStarted(),motion.slashDuration(),.20,.65,t->{
            var f=motion.frame(t);var m=FlameDaoRig.wrist(f.pose(),equip,left).mul(FlameDaoRig.blade(f,left));
            return AttackTrailMesh.slice(m,new Vector3f(0,.35f,0),new Vector3f(.05f,1.390625f,0),t);
        });
        var a=FlameFlipbook.loop(0,now*1.5,0);var b=FlameFlipbook.loop(0,now*1.5,3);
        return AttackTrailMesh.build(samples,now,STYLE,new AttackTrailMesh.Cell(a.u0(),a.u1(),a.v0(),a.v1()),new AttackTrailMesh.Cell(b.u0(),b.u1(),b.v0(),b.v1()));
    }
}
