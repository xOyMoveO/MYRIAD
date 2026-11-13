package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.skills.keheng.KehengVfx;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Production-mesh spatial and temporal contracts, independent of client graphics classes. */
final class KehengVisualChecks {
    private KehengVisualChecks(){}
    private static AbilityVfxPayload p(int variant,int kind,Vec3 from,Vec3 to,float radius,float stage,int life,long started,int instance){
        return new AbilityVfxPayload(1,-1,11,variant,kind,from,to,radius,stage,life,started,false,instance);
    }
    public static List<AbilityVfxPayload> samples(){
        var out=new ArrayList<AbilityVfxPayload>();
        out.add(p(0,SEAL,Vec3.ZERO,new Vec3(0,0,1),.38f,1,5,100,0));
        out.add(p(0,BURST,new Vec3(0,.9,0),new Vec3(0,.9,1),.45f,3,5,100,0));
        out.add(p(1,SHIELD,Vec3.ZERO,new Vec3(0,1,0),.65f,360,8,100,0));
        out.add(p(2,FLIGHT,Vec3.ZERO,new Vec3(1,.4,.6),.22f,0,3,100,71));
        out.add(p(2,BURST,Vec3.ZERO,new Vec3(1,.4,.6),.55f,0,5,100,0));
        out.add(p(3,TRAIL,new Vec3(-1,.4,0),new Vec3(1.3,.4,.7),.18f,0,7,100,0));
        out.add(p(3,SEAL,Vec3.ZERO,new Vec3(0,1,0),2,0,5,100,72));
        out.add(p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,300,5,100,73));
        out.add(p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,18,5,118,73));
        out.add(p(5,SEAL,Vec3.ZERO,new Vec3(0,1,0),3,18,5,118,74));
        out.add(p(5,BURST,Vec3.ZERO,new Vec3(0,1,0),3,KehengVfx.NATURAL_END,6,150,0));
        out.add(p(5,BURST,Vec3.ZERO,new Vec3(0,1,0),3,KehengVfx.INTERRUPTED_END,4,150,0));
        out.add(p(6,SEAL,Vec3.ZERO,new Vec3(0,1,0),4,18,5,118,75));
        out.add(p(6,ARC,Vec3.ZERO,new Vec3(0,1,0),4,0,5,130,0));
        out.add(p(7,CHARGE,Vec3.ZERO,new Vec3(0,0,12),1.5f,0,5,100,76));
        out.add(p(7,BEAM,new Vec3(0,1.3,0),new Vec3(0,1.3,12),1.5f,0,6,116,0));
        out.add(p(2,BEAM,Vec3.ZERO,new Vec3(0,0,.8),.03f,KehengVfx.BASIC,3,100,0));
        out.add(p(2,BURST,Vec3.ZERO,new Vec3(0,0,1),.3f,KehengVfx.BASIC,3,100,0));
        return out;
    }
    static void verify(GameTestHelper h){
        int count=0;
        for(var recipe:samples())for(float age:new float[]{0,.75f,2}){
            var mesh=AbilityVfxGeometry.build(recipe,age,false);var low=AbilityVfxGeometry.build(recipe,age,true);
            h.assertTrue(!mesh.isEmpty()&&mesh.size()<=192&&low.size()<=mesh.size(),"Keheng production stages retain finite normal/reduced meshes");
            for(var q:mesh){
                h.assertTrue(q.alpha()>0&&q.alpha()<=1,"Keheng alpha remains bounded");
                for(var v:new Vec3[]{q.a(),q.b(),q.c(),q.d()})h.assertTrue(Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&KehengVfx.bounds(recipe).inflate(.01).contains(v),"Keheng world bounds contain actual mesh vertices");
            }
            h.assertTrue(AbilityVfxGeometry.build(recipe,recipe.life(),false).isEmpty(),"Keheng actual expiry leaves no geometry");count++;
        }
        var nodes=p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,300,5,100,70);
        var arcs=p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,306,5,106,70);
        h.assertTrue(AbilityVfxGeometry.build(arcs,0,false).size()>AbilityVfxGeometry.build(nodes,0,false).size(),"Boundary starts as four nodes before connecting arcs");
        var active=p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,12,5,112,70);
        var refresh=p(4,SEAL,Vec3.ZERO,new Vec3(0,1,0),3.5f,15,5,115,70);
        h.assertTrue(AbilityVfxGeometry.build(active,3,false).equals(AbilityVfxGeometry.build(refresh,0,false)),"Fresh TTL does not restart field phase at the same world time");
        h.assertTrue(!AbilityVfxGeometry.build(active,0,false).equals(AbilityVfxGeometry.build(active,2,false)),"Production field layers really move between frames");
        for(int variant:new int[]{3,4,5,6}){
            float radius=variant==6?4:variant==4?3.5f:variant==5?3:2;
            var field=p(variant,SEAL,Vec3.ZERO,new Vec3(0,1,0),radius,18,5,118,70);
            var mesh=AbilityVfxGeometry.build(field,1,false);
            double maxRadius=0;
            for(var q:mesh){
                h.assertTrue(q.color()!=KehengVfx.WHITE,"Persistent time fields never become constant white screens");
                double area=q.b().subtract(q.a()).cross(q.d().subtract(q.a())).length();
                h.assertTrue(area<.6,"Time field uses open strips instead of filled panels");
                for(var v:new Vec3[]{q.a(),q.b(),q.c(),q.d()})maxRadius=Math.max(maxRadius,Math.sqrt(v.x*v.x+v.z*v.z));
            }
            h.assertTrue(maxRadius>=radius-.05&&maxRadius<=radius+.10,"Visual boundary agrees with actual 2/3/3.5/4 block radius");
        }
        for(var direction:List.of(new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(1,.6,-.7).normalize(),new Vec3(0,0,1))){
            var shot=p(2,FLIGHT,Vec3.ZERO,direction.scale(1.2),.22f,0,3,100,9);var mesh=AbilityVfxGeometry.build(shot,.8f,false);
            double foremost=-100,rearmost=100;
            for(var q:mesh)for(var v:new Vec3[]{q.a(),q.b(),q.c(),q.d()}){double along=v.dot(direction);foremost=Math.max(foremost,along);rearmost=Math.min(rearmost,along);}
            h.assertTrue(foremost>1&&rearmost<.1,"Needle tip and twin wake follow horizontal, vertical and oblique motion");
        }
        var natural=p(5,BURST,Vec3.ZERO,new Vec3(0,1,0),3,KehengVfx.NATURAL_END,6,150,0);
        var interrupted=p(5,BURST,Vec3.ZERO,new Vec3(0,1,0),3,KehengVfx.INTERRUPTED_END,4,150,0);
        h.assertTrue(!AbilityVfxGeometry.build(natural,1,false).equals(AbilityVfxGeometry.build(interrupted,1,false)),"Natural gathering needle and interrupted scatter are distinct");
        var other=p(4,SEAL,new Vec3(1,0,0),new Vec3(1,1,0),3.5f,15,5,115,71);
        h.assertTrue(KehengVfx.replaces(refresh,active)&&!KehengVfx.replaces(other,active)&&KehengVfx.obsolete(active,refresh),"One instance refreshes; two legal same-skill fields coexist; old snapshots cannot replace new ones");
        var lifetime=new AbilityVisualLifetime();
        var ended=p(4,CANCEL,Vec3.ZERO,Vec3.ZERO,0,2,1,116,70);lifetime.end(ended,116);
        h.assertTrue(lifetime.finished(refresh,116)&&!lifetime.finished(other,116),"Field end suppresses only its queued visual instance");
        var wake=KehengVfx.wakeForFlight(p(2,FLIGHT,Vec3.ZERO,new Vec3(1,.2,0),.2f,0,3,100,8));
        h.assertTrue(wake!=null&&wake.instance()==0&&wake.kind()==TRAIL&&wake.life()<=5&&AbilityVisualLifetime.expired(wake,105),"Needle wake follows real sampled endpoints and retires within 0.25 seconds");
        System.out.println("[MYRIAD-KEHENG-VISUAL] PASS: "+count+" real stage frames, eight candidates/basic, world phase, 3D orientation, exact boundaries, open strips, per-instance lifecycle and reduced budget");
    }
}
