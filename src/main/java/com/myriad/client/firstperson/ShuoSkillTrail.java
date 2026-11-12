package com.myriad.client.firstperson;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.ShuoViewPayload;
import java.util.*;
import net.minecraft.world.phys.Vec3;
/** Skill-only edge light and swept surface, sampled from the exact rendered motion, including entry blending. */
public final class ShuoSkillTrail {
    private ShuoSkillTrail(){}
    public static List<Quad> build(ShuoMotion motion,double now,boolean mirror){
        int action=motion.action(now);if(!motion.active(now)||ShuoViewPayload.basicAction(action))return List.of();
        var f=motion.frame(now,motion.fromDrawn());var blade=ShuoRig.blade(f,mirror);double exposure=ShuoBladeLight.exposure(f);
        float fade=(float)Math.min(1,(motion.started()+motion.duration()-now)/.8);
        var out=new ArrayList<Quad>(ShuoBladeLight.build(blade,exposure,now,.92f*fade,true));
        if(exposure<=.03){
            // Closed guard/retreat/charge: a restrained sheath-throat flow, not a blade through solid sheath.
            var throat=ShuoRig.sheath(f,mirror);var a=throat.transformPosition(new org.joml.Vector3f(-.11f,0,-.035f));var b=throat.transformPosition(new org.joml.Vector3f(.11f,0,-.035f));
            var p=new Vec3(a.x,a.y,a.z);var q=new Vec3(b.x,b.y,b.z);var up=new Vec3(0,.045,0);
            ShuoBladeLight.panel(out,p.subtract(up),q.subtract(up),q.add(up),p.add(up),77,0xD2EDFF,.9f*fade,true,0,1,0,1);
        }
        boolean cutting=action==ShuoViewPayload.FLASH||action>=ShuoViewPayload.CUT_LEFT&&action<=ShuoViewPayload.CUT_DOWN;
        if(cutting&&exposure>.05){
            double begin=motion.fromDrawn()?0:.32,end=begin+(1-begin)*.52;
            var samples=AttackTrailMesh.samples(now,motion.started(),motion.duration(),begin,end,t->{
                var pose=motion.frame(t,motion.fromDrawn());var mat=ShuoRig.blade(pose,mirror);double exposed=ShuoBladeLight.exposure(pose);
                return new AttackTrailMesh.Slice(ShuoBladeLight.at(mat,Math.min(.14,exposed)),ShuoBladeLight.at(mat,Math.min(.96,exposed)),t);
            });
            var sparse=new ArrayList<AttackTrailMesh.Slice>();for(int i=0;i<samples.size();i+=2)sparse.add(samples.get(i));
            if(!samples.isEmpty()&&(sparse.isEmpty()||sparse.getLast()!=samples.getLast()))sparse.add(samples.getLast());
            out.addAll(com.myriad.character.skills.shuo.ShuoBrightness.apply(AttackTrailMesh.build(sparse,now,new AttackTrailMesh.Style(75,77,0xD7EDFF,0xFFFFFF,.86f,.014),
                new AttackTrailMesh.Cell(0,1,0,1),new AttackTrailMesh.Cell(0,1,0,1))));
        }
        return List.copyOf(out);
    }
}
