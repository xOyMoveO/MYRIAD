package com.myriad.game;

import com.myriad.client.firstperson.*;
import com.myriad.character.skills.fenjue.rework.*;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

final class FlameStepViewChecks {
    static void verify(GameTestHelper h){
        var idle=new FlameDaoMotion().frame(0);var start=new AbilityVfxPayload(1,1,2,3,SHIELD,Vec3.ZERO,new Vec3(0,0,1),.8f,0,3,100,false);
        var finish=new AbilityVfxPayload(1,-1,2,3,ARC,new Vec3(0,1.62,8),new Vec3(0,1.62,9),2.2f,1,8,106,false);
        var motion=new FlameStepMotion();h.assertTrue(motion.accept(start,20,0,idle)&&!motion.accept(start,20,0,idle),"Confirmed dash starts once, not on local key prediction");
        var ready=motion.frame(23);var tip=tip(ready,false);
        h.assertTrue(tip.x<-.15&&tip.z<-.5,"Dash visibly holds the long blade tip to the left and in front of the hand");
        h.assertTrue(ready.pose().equals(motion.frame(30).pose())&&!motion.slashing(30),"Hold follows server completion, does not automatically slash after a guessed six ticks");
        h.assertTrue(FlameStepTrail.mesh(motion,23,false,0).isEmpty(),"No premature local slashing trail during dash hold");
        h.assertTrue(motion.accept(finish,26,0,motion.frame(26))&&!motion.accept(finish,26,0,idle),"Actual end starts exactly one slash");
        var cutTip=tip(motion.frame(29.25),false);
        h.assertTrue(cutTip.x/-cutTip.z-tip.x/-tip.z>.6,"Endpoint cut sweeps from left to right");
        h.assertTrue(motion.frame(26.1).pose().roll()<FlameStepMotion.READY.roll(),"Slash begins immediately at completion, no second windup");
        int poses=0,meshes=0;
        for(int i=0;i<180;i++)for(boolean left:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true}){
            double now=26+i/36.0;var f=motion.frame(now);var blade=FlameDaoRig.wrist(f.pose(),0,left).mul(FlameDaoRig.blade(f,left));
            var arm=FlameDaoRig.hand(f.pose(),0,left).mul(FlameDaoRig.arm(slim,left));
            h.assertTrue(blade.isFinite()&&blade.determinant()>0,"Finite positive-scale step blade transform");
            h.assertTrue(arm.transformPosition(new Vector3f((slim?.5f:1)/16*(left?1:-1),.5f,0)).distance(blade.transformPosition(new Vector3f()))<1E-6,"Both skin types/hands preserve palm-to-handle attachment");
            if(f.progress()>.205&&f.progress()<.645){
                var axis=blade.transformDirection(new Vector3f(0,1,0)).normalize();var travel=FlameDaoRig.travel(f,left);
                var transverse=new Vector3f(travel).sub(new Vector3f(axis).mul(travel.dot(axis))).normalize();
                h.assertTrue(blade.transformDirection(new Vector3f(1,0,0)).normalize().dot(transverse)>.999,"Endpoint cut leads with the sharp edge, not the flat");
            }
            for(var q:FlameStepTrail.mesh(motion,now,left,0))for(var v:java.util.List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(v.z<-.055,"Local trail never crosses camera near plane");
            poses++;
        }
        h.assertTrue(!motion.active(31)&&motion.frame(31).cut()<0,"Single quick endpoint cut settles to idle");
        var early=new FlameStepMotion();early.accept(start,20,0,idle);
        var collision=new AbilityVfxPayload(1,-1,2,3,ARC,finish.from(),finish.to(),2.2f,1,8,101,false);
        h.assertTrue(early.accept(collision,21,0,early.frame(21))&&early.slashing(21),"Collision completion releases immediately, not after normal travel duration");
        var cancel=new AbilityVfxPayload(1,-1,2,3,CANCEL,Vec3.ZERO,Vec3.ZERO,0,1,1,107,false);
        h.assertTrue(motion.accept(cancel,27,0,idle)&&!motion.active(27)&&!motion.accept(finish,27,0,idle)&&!motion.accept(start,27,0,idle),"Cancel clears gesture and blocks stale start/end replay");
        h.assertTrue(!new FlameStepMotion().accept(finish,30,6,idle)&&!new FlameStepMotion().accept(start,20,Double.NaN,idle),"Expired/nonfinite cues cannot start a new gesture");
        motion.reset();h.assertTrue(!motion.active(20),"Role/world reset clears step gesture");
        for(var forward:java.util.List.of(new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(.6,-.4,.8).normalize()))for(boolean side:new boolean[]{false,true}){
            var p=new AbilityVfxPayload(1,side?1:-1,2,3,side?SHIELD:ARC,Vec3.ZERO,forward,side?.8f:2.2f,side?0:1,side?3:8,100,false);
            for(float age:new float[]{0,.2f,1,2,2.9f,4,7.8f})for(boolean low:new boolean[]{false,true}){
                var mesh=AbilityVfxGeometry.build(p,age,low);
                h.assertTrue(mesh.size()<=(low?20:128),"Step's local image recipe stays within 128/20 quads");
                for(var q:mesh){
                    h.assertTrue(q.material()==68||q.material()==70,"Authored stream and native crisp slash textures only");
                    h.assertTrue(q.alpha()>0&&q.alpha()<=1&&q.startU()>=0&&q.endU()<=1&&q.startV()>=0&&q.endV()<=1,"Valid alpha and atlas cell UVs");
                    for(var v:java.util.List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&FlameVfxGeometry.bounds(p).contains(v),"Finite vertices within real culling bounds");
                }meshes++;
            }
            h.assertTrue(AbilityVfxGeometry.build(p,p.life(),false).isEmpty(),"Side wakes and finish slash expire at TTL");
        }
        var sides=FlameStepVfx.build(start,1,false);
        var renderedBody=new Vec3(0,0,4);
        var translated=start.from().add(renderedBody.subtract(FlameStepVfx.anchorOrigin(start,renderedBody)));
        h.assertTrue(translated.equals(renderedBody),"Latency cannot leave attached side effects four blocks behind the rendered body");
        h.assertTrue(FlameStepVfx.anchorOrigin(finish,renderedBody).equals(renderedBody),"Nonattached endpoint and all other recipes keep their existing anchor behavior");
        h.assertTrue(sides.stream().anyMatch(q->q.a().x<-.5)&&sides.stream().anyMatch(q->q.a().x>.5),"Both body sides receive flowing images");
        h.assertTrue(sides.stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).allMatch(v->v.z<.02&&v.y<1.5),"Body accents trail behind and below eyes, leaving forward sight clear");
        h.assertTrue(FlameVfxGeometry.firstPersonOpacity(finish,.42f)==1,"Endpoint image is not dimmed just because its packet begins at the eye");
        System.out.println("[MYRIAD-FLAME-STEP-VIEW] PASS: "+poses+" grip/edge poses, left-pointing hold, actual-end release, early collision/cancel/replay/TTL; "+meshes+" side/finish meshes, UV/LOD/bounds, both sides and clear view");
    }
    private static Vector3f tip(FlameDaoMotion.Frame f,boolean left){return FlameDaoRig.wrist(f.pose(),0,left).mul(FlameDaoRig.blade(f,left)).transformPosition(new Vector3f(0,1.390625f,0));}
}
