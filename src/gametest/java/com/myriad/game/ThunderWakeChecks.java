package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.skills.tianxing.rework.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Persistent planes, not persistent projectile bodies. Exact received endpoints and bounded TTL. */
final class ThunderWakeChecks {
    static void verify(GameTestHelper h){
        int count=0;
        for(int variant:new int[]{3,5})for(boolean low:new boolean[]{false,true})for(Vec3 direction:List.of(
                new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(0,0,1),new Vec3(.3,-.7,.6).normalize())){
            double speed=variant==5?4:2.5;
            var from=new Vec3(10,64,10);
            var flight=new AbilityVfxPayload(1,-1,1,variant,FLIGHT,from,from.add(direction.scale(speed)),.22f,0,3,100,false,7);
            var wake=ThunderVfxGeometry.wakeForFlight(flight);
            h.assertTrue(wake!=null&&wake.valid()&&ThunderVfxGeometry.isProjectileWake(wake),"Only approved projectiles spawn wake planes");
            h.assertTrue(wake.from().equals(flight.from())&&wake.to().equals(flight.to())&&wake.owner()==flight.owner()&&wake.started()==flight.started(),"No trail extrapolation beyond received swept endpoints");
            h.assertTrue(wake.instance()==0&&wake.kind()==TRAIL&&wake.life()==(variant==5?7:6),"No model ownership; exactly 0.30/0.35s absolute trail TTL");
            double previous=Double.POSITIVE_INFINITY;
            for(float age:new float[]{0,1,2,3,4,5}){
                var mesh=AbilityVfxGeometry.build(wake,age,low);
                h.assertTrue(!mesh.isEmpty()&&mesh.size()<100&&mesh.size()<=ThunderVfxGeometry.budget(wake,low),"Small fixed planar wake budget");
                double energy=mesh.stream().mapToDouble(Quad::alpha).sum();
                h.assertTrue(energy<previous,"Wake energy continuously decays, no late flashing body");previous=energy;
                for(var q:mesh){
                    h.assertTrue(q.material()==THUNDER_PIXEL_BOLT,"Reuse only HD electrical planes, never a parked solid or blade silhouette");
                    h.assertTrue(q.alpha()>0&&q.alpha()<=1,"Finite alpha");
                    h.assertTrue(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).lengthSqr()>1E-12,"No collapsed crossed planes");
                    for(var at:List.of(q.a(),q.b(),q.c(),q.d())){
                        h.assertTrue(ThunderVfxGeometry.bounds(wake).inflate(.001).contains(at),"Expanded view culling includes the full wake");
                        double along=at.subtract(from).dot(direction);
                        h.assertTrue(along>=-1E-6&&along<=speed+1E-6,"Ribbons do not visually continue beyond a wall/contact");
                    }
                }
            }
            h.assertTrue(AbilityVfxGeometry.build(wake,wake.life(),low).isEmpty()&&AbilityVisualLifetime.expired(wake,100+wake.life()),"All wake planes expire absolutely, even if packets are delayed");
            var lifetimes=new AbilityVisualLifetime();lifetimes.end(flight,101);
            h.assertTrue(lifetimes.finished(flight,101)&&!lifetimes.finished(wake,101),"End immediately removes the body, not a brief independently fading trail");
            h.assertTrue(AbilityVfxGeometry.build(flight,1.2f,low).stream().noneMatch(q->q.material()==THUNDER_PROJECTILE_MODEL),"Never extend model display to fake persistence");
            count++;
        }
        for(int theme=0;theme<10;theme++)for(int variant=0;variant<8;variant++){
            var p=new AbilityVfxPayload(1,-1,theme,variant,FLIGHT,Vec3.ZERO,new Vec3(1,0,0),.22f,0,3,100,false,7);
            h.assertTrue((ThunderVfxGeometry.wakeForFlight(p)!=null)==(theme==1&&(variant==3||variant==5)),"No other role's flight is modified");
        }
        var beam=new AbilityVfxPayload(1,-1,1,3,BEAM,Vec3.ZERO,new Vec3(1,0,0),.22f,0,6,100,false);
        h.assertTrue(ThunderVfxGeometry.wakeForFlight(beam)==null&&!ThunderVfxGeometry.isProjectileWake(beam),"Arc chain/blink beams are unchanged");
        var zero=new AbilityVfxPayload(1,-1,1,3,FLIGHT,Vec3.ZERO,Vec3.ZERO,.22f,0,3,100,false,7);
        h.assertTrue(ThunderVfxGeometry.wakeForFlight(zero)==null,"No zero-length spray at a contact");
        var shortFlight=new AbilityVfxPayload(1,-1,1,5,FLIGHT,Vec3.ZERO,new Vec3(.01,0,0),.22f,0,3,100,false,7);
        var shortWake=ThunderVfxGeometry.wakeForFlight(shortFlight);
        var shortMesh=AbilityVfxGeometry.build(shortWake,.5f,false);
        h.assertTrue(!shortMesh.isEmpty()&&shortMesh.stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d()))
                .allMatch(at->at.x>=-1E-8&&at.x<=.01+1E-8),"Even a one-centimetre clipped final step has no forward overshoot");
        System.out.println("[MYRIAD-THUNDER-WAKE] PASS: "+count+" full/LOD/3D directions, decaying bounded planar wakes, exact clipped segment, finite TTL and ended body separation");
    }
}
