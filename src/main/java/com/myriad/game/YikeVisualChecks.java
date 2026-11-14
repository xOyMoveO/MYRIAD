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
import static com.myriad.character.ability.AbilityVfxMaterials.*;

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
                    boolean beamCore=q.material()==0&&(signal==YikeVfx.BEAM||signal==BEAM_END);
                    h.assertTrue(q.alpha()>0&&q.alpha()<=1&&(beamCore||q.material()>=YIKE_HD_RING&&q.material()<=YIKE_HD_SPARK),"Only the continuous beam core uses a solid texel; other non-flight layers retain HD/pixel PNGs with finite opacity");
                    h.assertTrue(q.startU()>=0&&q.endU()<=1&&q.startV()>=0&&q.endV()<=1&&q.startU()<q.endU()&&q.startV()<q.endV(),"HD layer UVs stay inside the authored transparent sprite");
                    for(var point:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z),"Finite production vertices");
                }
                count++;
            }
            h.assertTrue(YikeVfxGeometry.build(p,p.life(),low).isEmpty(),"No mesh survives its absolute effect expiry");
        }
        for(Vec3 f:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(.3,-.8,.4).normalize()))for(int signal:new int[]{BASIC,0,HEAVY}){
            var p=packet(AbilityVfxGeometry.FLIGHT,signal,3,100,f.scale(1.4),signal==BASIC?.065f:signal==HEAVY?.36f:.14f);
            for(boolean low:new boolean[]{false,true}){
                var mesh=YikeVfxGeometry.build(p,.7f,low);var body=mesh.stream().filter(q->q.material()==YIKE_PROJECTILE_MODEL).toList();
                h.assertTrue(body.size()==YikeVfxGeometry.modelFaces(signal,low)&&YikeVfxGeometry.modelParts(signal,low)>4,"Every flight renders its complete real full/LOD Blockbench mesh");
                h.assertTrue(mesh.size()<=(low?192:512),"Authored projectile and HD decoration fit the scoped body budget");
                h.assertTrue(mesh.stream().anyMatch(q->q.material()==YIKE_HD_STREAM)&&mesh.stream().anyMatch(q->q.material()==YIKE_HD_MOTES),"Actual solid body, separate textured flow and pixel fragments are independently layered");
                double front=Double.NEGATIVE_INFINITY,back=Double.POSITIVE_INFINITY,width=0;Vec3 tip=p.from().lerp(p.to(),.7);
                for(var q:mesh){
                    h.assertTrue(q.material()!=0&&q.startU()>=0&&q.endU()<=1&&q.startV()>=0&&q.endV()<=1&&q.startU()<q.endU()&&q.startV()<q.endV(),"Every flight face binds actual atlas UVs");
                    h.assertTrue(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).lengthSqr()>1E-14,"All actual model and decoration faces have positive area");
                    for(var at:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(at.dot(f)<=1.4*.7+1E-6,"Model and 2D layers never extend beyond their confirmed forward endpoint");
                }
                for(var q:body)for(var at:List.of(q.a(),q.b(),q.c(),q.d())){
                    var local=at.subtract(tip);double longitudinal=local.dot(f);front=Math.max(front,longitudinal);back=Math.min(back,longitudinal);
                    width=Math.max(width,local.subtract(f.scale(longitudinal)).length());
                }
                double length=YikeVfxGeometry.modelLength(signal);
                h.assertTrue(Math.abs(front)<1E-5&&back<-length*.75&&back>=-length*1.01&&width>.005&&width<.4,"The +Y nose leads real horizontal/upward/downward motion at stable model scale");
                for(float age:new float[]{1,1.5f,2.5f,3})h.assertTrue(YikeVfxGeometry.build(p,age,low).isEmpty(),"No solid projectile parks on the last endpoint after its one-tick movement sample");
            }
            var wake=YikeVfxGeometry.wakeForFlight(p);h.assertTrue(wake!=null&&wake.kind()==AbilityVfxGeometry.TRAIL&&wake.instance()==0&&wake.from().equals(p.from())&&wake.to().equals(p.to()),"Independent planar wake uses only actual swept endpoints");
            for(var q:YikeVfxGeometry.build(wake,1,false))for(var at:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(at.dot(f)>=-1E-6&&at.dot(f)<=1.4+1E-6,"All three ribbons stop at the confirmed wall/range segment");
            var life=new AbilityVisualLifetime();life.end(p,101);h.assertTrue(life.finished(p,101)&&!life.finished(wake,101),"Flight finish removes body while the finite planar wake fades independently");
        }
        try{
            for(String name:List.of("ring","arc","stream","impact","veil","shield","motes","spark")){
                try(var in=YikeVisualChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/yike_hd_"+name+".png")){
                    var image=javax.imageio.ImageIO.read(in);int size=name.equals("motes")||name.equals("spark")?64:256;
                    h.assertTrue(image.getWidth()==size&&image.getHeight()==size&&image.getColorModel().hasAlpha(),"Actual HD/pixel material has its authored resolution and alpha channel");
                    boolean clear=false,visible=false;for(int y=0;y<size;y++)for(int x=0;x<size;x++){int a=image.getRGB(x,y)>>>24;clear|=a==0;visible|=a>100;}
                    h.assertTrue(clear&&visible,"HD effects contain transparent negative space and visible structural detail");
                }
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        var layered=packet(AbilityVfxGeometry.BURST,HEAVY_IMPACT,14,100,Vec3.ZERO,4);
        var flash=YikeVfxGeometry.build(layered,.3f,false);var sheets=YikeVfxGeometry.build(layered,2.4f,false);var pixels=YikeVfxGeometry.build(layered,5,false);
        h.assertTrue(flash.stream().anyMatch(q->q.material()==YIKE_HD_SPARK)&&flash.stream().noneMatch(q->q.material()==YIKE_HD_MOTES)
                &&sheets.stream().anyMatch(q->q.material()==YIKE_HD_ARC)&&pixels.stream().anyMatch(q->q.material()==YIKE_HD_MOTES),"Cold core flash, HD shock panels and pixel fragments enter at distinct confirmed event ages");
        var beam=packet(AbilityVfxGeometry.BEAM,YikeVfx.BEAM,3,100,new Vec3(8,0,0),.625f);
        var refreshed=packet(AbilityVfxGeometry.BEAM,YikeVfx.BEAM,3,101,new Vec3(8,0,0),.625f);
        h.assertTrue(YikeVfxGeometry.continuousBeam(beam)&&!YikeVfxGeometry.continuousBeam(layered),"Only sustained beam/retraction bypass whole-effect muzzle proximity fading");
        h.assertTrue(YikeVfxGeometry.replaces(refreshed,beam)&&YikeVfxGeometry.obsolete(beam,refreshed),"Refreshed beam replaces older mesh instead of stacking brightness");
        var a=YikeVfxGeometry.build(beam,1.5f,false);var b=YikeVfxGeometry.build(refreshed,.5f,false);
        for(boolean low:new boolean[]{false,true})for(Vec3 f:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(.4,.6,-.3).normalize())){
            var directed=packet(AbilityVfxGeometry.BEAM,YikeVfx.BEAM,3,100,f.scale(22),.625f);
            var core=YikeVfxGeometry.build(directed,0,low).stream().filter(q->q.material()==0).toList();
            h.assertTrue(core.size()==48,"Full and reduced detail retain the entire closed bright core and blue outer tube");
            double near=100,far=-100,width=0;
            for(var q:core){
                h.assertTrue(q.alpha()>=.82f,"White texel beam surfaces remain visibly opaque instead of sparse transparent tail pixels");
                h.assertTrue(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).lengthSqr()>1E-14,"Every beam surface and end cap has real area");
                for(var point:List.of(q.a(),q.b(),q.c(),q.d())){
                    double along=point.dot(f);near=Math.min(near,along);far=Math.max(far,along);
                    width=Math.max(width,point.subtract(f.scale(along)).length());
                    h.assertTrue(along>=.5-1E-6&&along<=22+1E-6,"Solid core starts outside the owner near plane and ends at the clipped endpoint");
                }
            }
            h.assertTrue(Math.abs(near-.5)<1E-6&&Math.abs(far-22)<1E-6&&Math.abs(width-.21)<1E-6,"Core spans the real beam and stays strictly inside its .625 damage half-width");
            var shortBeam=packet(AbilityVfxGeometry.BEAM,YikeVfx.BEAM,3,100,f.scale(.1),.625f);
            for(var q:YikeVfxGeometry.build(shortBeam,0,low))if(q.material()==0)for(var point:List.of(q.a(),q.b(),q.c(),q.d()))
                h.assertTrue(point.dot(f)>=.025-1E-6&&point.dot(f)<=.1+1E-6,"Very close walls still clip the solid core without reversing it");
        }
        h.assertTrue(a.size()==b.size(),"Refreshed beam keeps independent flowing strands");
        for(int i=0;i<a.size();i++)h.assertTrue(a.get(i).a().distanceToSqr(b.get(i).a())<1E-12,"World phase is continuous across per-tick packet renewal");
        var other=new AbilityVfxPayload(1,-1,THEME,7,AbilityVfxGeometry.BEAM,Vec3.ZERO,new Vec3(8,0,0),.625f,YikeVfx.BEAM,3,101,false,10);
        h.assertTrue(!YikeVfxGeometry.replaces(other,beam),"Distinct cast instances are not merged");
        for(var q:a)for(var at:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(at.x>=-1E-6&&at.x<=8+1E-6&&Math.hypot(at.y,at.z)<=.625,"Beam rails/streams remain inside clipped length and actual half-width");
        var end=packet(AbilityVfxGeometry.BEAM,BEAM_END,4,102,new Vec3(8,0,0),.625f);
        h.assertTrue(YikeVfxGeometry.continuousBeam(end),"Retracting beam keeps the same near-plane-safe visibility policy");
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
        System.out.println("[MYRIAD-YIKE-VISUAL] PASS: "+count+" HD/pixel staged frames, three actual textured full/LOD models, 3D tip/UV/area/budgets, no parked bodies, exact wakes/bounds, unchanged authoritative prep/flow and finite cancellation");
        System.out.println("[MYRIAD-YIKE-SOLID-BEAM] PASS: 48 continuous white/blue faces in full/LOD, 4 directions, exact 22/near-wall endpoints, safe near plane, bounded radius and existing cancellation");
    }
}
