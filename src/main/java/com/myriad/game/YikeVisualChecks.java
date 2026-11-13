package com.myriad.game;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVisualLifetime;
import com.myriad.character.skills.yike.YikeVfx;
import com.myriad.character.skills.yike.YikeVfxGeometry;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.skills.yike.YikeVfx.*;

/** Production mesh invariants only; actual input-event assertions live in the existing YikeChecks. */
final class YikeVisualChecks {
    private YikeVisualChecks() {}
    private static AbilityVfxPayload packet(int kind,int signal,int life,long start,Vec3 end,float radius){
        return new AbilityVfxPayload(1,-1,THEME,signal==HEAVY||signal==HEAVY_IMPACT||signal==HEAVY_WARNING?6:signal==YikeVfx.BEAM||signal==BEAM_END||signal==BEAM_HIT||signal==BEAM_CHARGE?7:2,kind,Vec3.ZERO,end,radius,signal,life,start,false,9);
    }
    static void verify(GameTestHelper h){
        int count=0;
        int[] signals={BASIC,BASIC_HIT,IMPACT,DENIAL_WARNING,DENIAL_BURST,FIXED,MOBILE,MUZZLE,HEAVY_WARNING,HEAVY_IMPACT,YikeVfx.BEAM,BEAM_HIT,BEAM_CHARGE,MARK1,MARK2,MARK_BURST,BARRIER,DEPLOY,DISSIPATE,LAST_MUZZLE,MUZZLE_CHARGE,BEAM_END};
        for(int signal:signals)for(boolean low:new boolean[]{false,true}){
            int kind=signal==YikeVfx.BEAM||signal==BEAM_END?AbilityVfxGeometry.BEAM:signal==DENIAL_WARNING||signal==HEAVY_WARNING?AbilityVfxGeometry.SEAL:AbilityVfxGeometry.BURST;
            var p=packet(kind,signal,signal==HEAVY_IMPACT?14:8,100,new Vec3(8,0,0),signal==HEAVY_IMPACT||signal==HEAVY_WARNING?4:signal==DENIAL_WARNING||signal==DENIAL_BURST?2.5f:.625f);
            for(float age:new float[]{0,.5f,2,4,p.life()-.1f}){
                var mesh=YikeVfxGeometry.build(p,age,low);
                h.assertTrue(mesh.size()<=(low?96:192),"Each role recipe respects its real full/LOD budget");
                for(var q:mesh){
                    h.assertTrue(q.alpha()>0&&q.alpha()<=1&&q.material()==0,"Existing material family, finite opacity");
                    for(var point:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z),"Finite production vertices");
                }
                count++;
            }
            h.assertTrue(YikeVfxGeometry.build(p,p.life(),low).isEmpty(),"No mesh survives its absolute effect expiry");
        }
        for(Vec3 f:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(.3,-.8,.4).normalize()))for(int signal:new int[]{BASIC,0,HEAVY}){
            var p=packet(AbilityVfxGeometry.FLIGHT,signal,3,100,f.scale(1.4),signal==BASIC?.065f:signal==HEAVY?.36f:.14f);
            var mesh=YikeVfxGeometry.build(p,.7f,false);h.assertTrue(!mesh.isEmpty(),"Every 3D flight has a pointed crystal body");
            for(var q:mesh)for(var at:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(at.dot(f)<=1.4*.7+1E-6,"Crystal and petals never project in front of the received collision endpoint");
            var wake=YikeVfxGeometry.wakeForFlight(p);h.assertTrue(wake!=null&&wake.kind()==AbilityVfxGeometry.TRAIL&&wake.instance()==0&&wake.from().equals(p.from())&&wake.to().equals(p.to()),"Independent planar wake uses only actual swept endpoints");
            for(var q:YikeVfxGeometry.build(wake,1,false))for(var at:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(at.dot(f)>=-1E-6&&at.dot(f)<=1.4+1E-6,"All three ribbons stop at the confirmed wall/range segment");
            var life=new AbilityVisualLifetime();life.end(p,101);h.assertTrue(life.finished(p,101)&&!life.finished(wake,101),"Flight finish removes body while the finite planar wake fades independently");
        }
        var beam=packet(AbilityVfxGeometry.BEAM,YikeVfx.BEAM,3,100,new Vec3(8,0,0),.625f);
        var refreshed=packet(AbilityVfxGeometry.BEAM,YikeVfx.BEAM,3,101,new Vec3(8,0,0),.625f);
        h.assertTrue(YikeVfxGeometry.replaces(refreshed,beam)&&YikeVfxGeometry.obsolete(beam,refreshed),"Refreshed beam replaces older mesh instead of stacking brightness");
        var a=YikeVfxGeometry.build(beam,1.5f,false);var b=YikeVfxGeometry.build(refreshed,.5f,false);
        h.assertTrue(a.size()==b.size(),"Refreshed beam keeps independent flowing strands");
        for(int i=0;i<a.size();i++)h.assertTrue(a.get(i).a().distanceToSqr(b.get(i).a())<1E-12,"World phase is continuous across per-tick packet renewal");
        var other=new AbilityVfxPayload(1,-1,THEME,7,AbilityVfxGeometry.BEAM,Vec3.ZERO,new Vec3(8,0,0),.625f,YikeVfx.BEAM,3,101,false,10);
        h.assertTrue(!YikeVfxGeometry.replaces(other,beam),"Distinct cast instances are not merged");
        for(var q:a)for(var at:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(at.x>=-1E-6&&at.x<=8+1E-6&&Math.hypot(at.y,at.z)<=.625,"Beam rails/streams remain inside clipped length and actual half-width");
        var end=packet(AbilityVfxGeometry.BEAM,BEAM_END,4,102,new Vec3(8,0,0),.625f);
        double previous=Double.POSITIVE_INFINITY;
        for(float t:new float[]{0,1,2,3}){double extent=YikeVfxGeometry.build(end,t,false).stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).mapToDouble(v->v.x).max().orElse(0);h.assertTrue(extent<previous,"Cancel contracts monotonically toward the muzzle with no continuing contact stage");previous=extent;}
        for(int signal:new int[]{DENIAL_WARNING,HEAVY_WARNING}){
            var warning=packet(AbilityVfxGeometry.SEAL,signal,11,100,Vec3.ZERO,signal==DENIAL_WARNING?2.5f:4);
            for(var q:YikeVfxGeometry.build(warning,5,false))for(var at:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(Math.hypot(at.x,at.z)<=warning.radius()+1E-6&&Math.abs(at.y)<.1,"Sparse warning stays flat and does not imply a larger damage radius");
        }
        for(float progress:new float[]{.1f,.5f,.9f}){
            var prep=new AbilityVfxPayload(1,-1,THEME,2,AbilityVfxGeometry.CHARGE,Vec3.ZERO,new Vec3(1,0,0),.35f,MUZZLE_CHARGE+.4f*progress,3,100,false,9);
            h.assertTrue(Math.abs(YikeVfxGeometry.preparation(prep)-progress)<1E-5,"Windup progress is server-authoritative, independent of renewed TTL");
            var early=YikeVfxGeometry.build(prep,.2f,false);var late=YikeVfxGeometry.build(prep,1.2f,false);
            h.assertTrue(early.size()==late.size()&&early.getFirst().a().equals(late.getFirst().a()),"Slow preparation cannot run to completion from local packet age");
        }
        System.out.println("[MYRIAD-YIKE-VISUAL] PASS: "+count+" staged full/LOD frames, vertical/downward flight, exact three-layer wakes, radius/beam clipping, non-restarting preparation and flow, finite cancellation");
    }
}
