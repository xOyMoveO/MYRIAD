package com.myriad.game;

import com.myriad.client.firstperson.*;
import com.myriad.character.skills.fenjue.rework.FlameCrossSlashVfx;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

final class FlameSlashViewChecks {
    static void verify(GameTestHelper h){
        var cue=new AbilityVfxPayload(1,-1,2,2,ARC,Vec3.ZERO,new Vec3(0,0,1),4,100,8,100,false);
        var motion=new FlameSlashMotion();var idle=new FlameDaoMotion().frame(0);
        h.assertTrue(!motion.active(0)&&motion.accept(cue,20,0,idle),"Server-confirmed flame slash begins locally");
        h.assertTrue(!motion.accept(cue,20,0,idle),"Duplicate cue cannot replay or restart double slash");
        int poses=0;
        for(int i=0;i<240;i++)for(boolean left:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true}){
            var f=motion.frame(20+i/30.0);var blade=FlameDaoRig.wrist(f.pose(),0,left).mul(FlameDaoRig.blade(f,left));
            var hand=FlameDaoRig.hand(f.pose(),0,left).mul(FlameDaoRig.arm(slim,left));
            h.assertTrue(blade.isFinite()&&blade.determinant()>0,"Finite skill blade, no negative mirroring");
            h.assertTrue(hand.transformPosition(new Vector3f((slim?.5f:1)/16*(left?1:-1),.5f,0)).distance(blade.transformPosition(new Vector3f()))<1E-6,"Skill preserves real palm/handle attachment");
            if(f.progress()>.205&&f.progress()<.645){
                var shaft=blade.transformDirection(new Vector3f(0,1,0)).normalize();var travel=FlameDaoRig.travel(f,left);
                var transverse=new Vector3f(travel).sub(new Vector3f(shaft).mul(travel.dot(shaft))).normalize();
                h.assertTrue(blade.transformDirection(new Vector3f(1,0,0)).normalize().dot(transverse)>.999,"Both cuts lead with sharp edge, not the blade flat");
            }poses++;
        }
        for(int cut=0;cut<2;cut++){
            double base=cut==0?20:23.6,duration=cut==0?3.6:4.4;
            var a=tip(motion.frame(base+duration*.20));var b=tip(motion.frame(base+duration*.65));
            var ready=motion.frame(base+duration*.20);var strike=motion.frame(base+duration*.42);
            h.assertTrue(strike.pose().z()<ready.pose().z()-.4,"Both slashes extend the held weapon forward, not only sideways");
            double dx=b.x/-b.z-a.x/-a.z,dy=b.y/-b.z-a.y/-a.z;
            h.assertTrue((cut==0?dx<-.5:dx>.5)&&dy<-.25,"Opposite diagonal descending slashes: "+cut+" "+dx+" "+dy);
        }
        h.assertTrue(!motion.active(28)&&motion.frame(28).cut()<0,"Ends in 0.4s without continuing normal combo");
        var late=new FlameSlashMotion();h.assertTrue(late.accept(cue,22,2,idle)&&late.frame(22).equals(motion.frame(22)),"Latency samples original timeline, not a fresh full animation");
        h.assertTrue(!new FlameSlashMotion().accept(cue,28,8,idle)&&!new FlameSlashMotion().accept(cue,Double.NaN,0,idle),"Expired and invalid cues rejected");
        var cancel=new AbilityVfxPayload(1,-1,2,2,CANCEL,Vec3.ZERO,Vec3.ZERO,0,1,1,102,false);
        h.assertTrue(motion.accept(cancel,22,0,idle)&&!motion.active(22)&&!motion.accept(cue,22,0,idle),"Cancellation clears and blocks stale replay");
        motion.reset();h.assertTrue(!motion.active(20),"World/role/reset clears gesture");
        int meshes=0;
        for(var forward:java.util.List.of(new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(.6,-.4,.8).normalize())){
            var p=new AbilityVfxPayload(1,-1,2,2,ARC,Vec3.ZERO,forward,4,100,8,100,false);
            for(float age:new float[]{0,.8f,2,3.8f,4.5f,7.9f})for(boolean low:new boolean[]{false,true}){
                var mesh=FlameCrossSlashVfx.build(p,age,low);
                h.assertTrue((age==0||age>=8*13.6/14?mesh.isEmpty():!mesh.isEmpty())&&mesh.size()<=(low?36:192),"Zero-area birth and eroded end, bounded curved image faces");
                for(int j=0;j<mesh.size();j+=2){var q=mesh.get(j);
                    double lead=q.b().subtract(q.a()).normalize().dot(forward);
                    double facing=q.b().subtract(q.a()).cross(q.d().subtract(q.a())).normalize().dot(forward);
                    h.assertTrue(lead>=-1E-6&&Math.abs(facing)<1E-6,
                        "Radial painted blade convexity stays forward in a plane with zero lateral XY thickness");
                    for(var vertex:java.util.List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(vertex.subtract(p.from()).dot(forward)>.3,"Entire enlarged slash stays ahead of the caster");
                    h.assertTrue(q.material()==70||!low&&q.material()==69,"Dedicated painted crescent plus sparse ember after-flame, never a ring");
                    h.assertTrue((q.material()!=70||q.b().distanceTo(q.a())>4)&&q.endV()>q.startV(),"Bolder full-width painted body and continuous native source UVs");
                    h.assertTrue(q.alpha()>0&&q.alpha()<=1,"Bright finite bounded alpha");
                }
                meshes++;
            }
            var crossed=FlameCrossSlashVfx.build(p,4.5f,false);
            h.assertTrue(crossed.size()>8&&crossed.size()<=192,"Both curved image strokes overlap within the full budget without double-frame ghosting");
            var first=FlameCrossSlashVfx.window(4.5,0,8);var second=FlameCrossSlashVfx.window(4.5,1,8);
            h.assertTrue(first.tail()>0&&first.head()==1&&second.head()>second.tail(),"Earlier stroke erodes from tail while the next stroke draws");
            h.assertTrue(crossed.stream().anyMatch(q->q.alpha()>.6),"Solid painted body remains bright during overlap");
            h.assertTrue(FlameCrossSlashVfx.build(p,8,false).isEmpty(),"No cross wave outlives authoritative TTL");
        }
        System.out.println("[MYRIAD-FLAME-CROSS] PASS: "+poses+" actual skill grip/edge poses, opposite rapid cuts, cue lifecycle, "+meshes+" tall painted image meshes, strong body and bounded TTL/LOD");
    }
    private static Vector3f tip(FlameDaoMotion.Frame f){return FlameDaoRig.wrist(f.pose(),0,false).mul(FlameDaoRig.blade(f,false)).transformPosition(new Vector3f(0,1.390625f,0));}
}
