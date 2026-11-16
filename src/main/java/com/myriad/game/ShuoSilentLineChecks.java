package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.skills.shuo.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.SkillSystem;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;

final class ShuoSilentLineChecks {
    private static AbilityVfxPayload packet(int signal,Vec3 from,Vec3 to,int life,long start,int instance){
        return new AbilityVfxPayload(1,-1,10,6,AbilityVfxGeometry.BEAM,from,to,.9f,signal,life,start,false,instance);
    }
    static void verify(GameTestHelper h){
        int count=0;
        for(var direction:List.of(new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(.5,.3,-.7).normalize()))
            for(double length:new double[]{.15,3,12})for(int signal:new int[]{ShuoSilentLineVfx.SEAM,ShuoSilentLineVfx.CUT})
                for(boolean low:new boolean[]{false,true}){
                    var from=new Vec3(3,40,-5);var p=packet(signal,from,from.add(direction.scale(length)),signal==ShuoSilentLineVfx.SEAM?3:10,100,57);
                    for(int frame=0;frame<20;frame++){
                        float age=(frame+.1f)*p.life()/20;var mesh=AbilityVfxGeometry.build(p,age,low);
                        h.assertTrue(!mesh.isEmpty()&&mesh.size()<=(low?96:160),"Silent line keeps its visible core in full/LOD and finite layer budget");
                        for(var q:mesh){
                            h.assertTrue(q.alpha()>0&&q.alpha()<=1&&(q.material()==0||q.material()==75||q.material()==77),"Only silver filament, authored blade and stream layers");
                            h.assertTrue(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).lengthSqr()>1E-13,"No collapsed slash faces after endpoint clipping");
                            h.assertTrue(q.startU()>=0&&q.startU()<q.endU()&&q.endU()<=1&&q.startV()>=0&&q.startV()<q.endV()&&q.endV()<=1,"Clipped blade keeps valid authored UV ranges");
                            for(var point:List.of(q.a(),q.b(),q.c(),q.d())){
                                double along=point.subtract(from).dot(direction);
                                h.assertTrue(Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z)
                                        &&along>=-1E-6&&along<=length+1E-6&&ShuoSkillVfx.bounds(p).inflate(.01).contains(point),
                                        "No decoration projects beyond the actual travelled start/end or culling volume");
                            }
                        }
                        h.assertTrue(mesh.equals(AbilityVfxGeometry.build(p,age,low)),"Seeded random blade paths are stable within one frame");count++;
                    }
                    for(float age:new float[]{-1,p.life(),p.life()+2,Float.NaN})h.assertTrue(AbilityVfxGeometry.build(p,age,low).isEmpty(),"No line survives invalid age or finite expiry");
                }
        var seam=packet(ShuoSilentLineVfx.SEAM,Vec3.ZERO,new Vec3(0,0,12),3,100,7);
        var fresh=packet(ShuoSilentLineVfx.SEAM,Vec3.ZERO,new Vec3(0,0,12),3,101,7);
        var separate=packet(ShuoSilentLineVfx.SEAM,Vec3.ZERO,new Vec3(0,0,12),3,101,8);
        h.assertTrue(ShuoSilentLineVfx.replaces(fresh,seam)&&!ShuoSilentLineVfx.replaces(separate,seam),"Refresh replaces only its own segment, never another cast");
        h.assertTrue(AbilityVfxGeometry.build(seam,1,false).equals(AbilityVfxGeometry.build(fresh,0,false)),"Renewing the seam does not restart its travelling highlight");
        var cut=packet(ShuoSilentLineVfx.CUT,Vec3.ZERO,new Vec3(0,0,12),10,110,7);
        h.assertTrue(AbilityVfxGeometry.build(seam,.5f,false).stream().filter(q->q.material()==0).count()==2
                &&AbilityVfxGeometry.build(cut,.5f,false).stream().filter(q->q.material()==0).count()==4,"One retained line becomes exactly two crossing cut cores before random flourishes");
        h.assertTrue(!AbilityVfxGeometry.build(cut,3,false).equals(AbilityVfxGeometry.build(packet(ShuoSilentLineVfx.CUT,Vec3.ZERO,new Vec3(0,0,12),10,110,8),3,false)),"Distinct casts get distinct seeded travelling blades");
        System.out.println("[MYRIAD-SHUO-SILENT-LINE] PASS: "+count+" path/direction/stage/LOD frames, exact endpoints, one seam/two cuts, seeded flourishes, phase continuity and finite expiry");
    }

    static void start(GameTestHelper h){
        var players=new ArrayList<net.minecraft.server.level.ServerPlayer>();var targets=new ArrayList<net.minecraft.world.entity.LivingEntity>();
        var observers=new ArrayList<ShuoWorldVfxChecks.Observer>();var origins=new ArrayList<Vec3>();
        var blocks=new HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int i=0;i<3;i++){
            var origin=Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(90,100+i*9,5)));
            var p=ShuoChecks.player(h,origin,new ArrayList<>());players.add(p);origins.add(origin);
            PlayerDataProvider.get(p).setSkillLevel(FirstGroupSkills.node("shuo",6).getId(),1);
            targets.add(ShuoChecks.target(h,origin.add(0,0,5)));observers.add(ShuoWorldVfxChecks.observe(h,origin.add(8,0,0)));
        }
        for(int x=-1;x<=1;x++)for(int y=0;y<3;y++){
            var pos=players.get(2).blockPosition().offset(x,y,1);blocks.put(pos,h.getLevel().getBlockState(pos));
            h.getLevel().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        boolean[] done={false};
        Runnable cleanup=()->{blocks.forEach((p,s)->h.getLevel().setBlockAndUpdate(p,s));observers.forEach(ShuoWorldVfxChecks::cleanup);players.forEach(p->{AbilityRuntime.clear(p);p.discard();});targets.forEach(net.minecraft.world.entity.Entity::discard);};
        for(int n=1;n<=55;n++){int age=n;h.runAfterDelay(n,()->{
            if(done[0])return;
            try{
                if(age==2)players.forEach(p->SkillSystem.cast(p,3));
                // Interrupt only after a real path and before the delayed damage resolution.
                if(age==24)PlayerDataProvider.get(players.get(1)).setBuff("g1_stun",1);
                if(age==25)PlayerDataProvider.get(players.get(1)).setBuff("g1_stun",0);
                for(int i=0;i<3;i++){
                    var p=players.get(i);
                    if(AbilityRuntime.moving(p))p.move(net.minecraft.world.entity.MoverType.SELF,AbilityTargeting.clampPrediction(p.position(),origins.get(i).add(0,0,12),p.getDeltaMovement()));
                    AbilityRuntime.tick(p);
                }
                if(age==22)h.assertTrue(targets.getFirst().getHealth()==1000,"Retained seam is visible before damage and never deals early damage");
                if(age==45){
                    for(int i=0;i<3;i++){
                        int id=players.get(i).getId();var packets=observers.get(i).packets().stream().filter(p->p.owner()==id).toList();
                        var seams=packets.stream().filter(p->p.angle()==ShuoSilentLineVfx.SEAM).toList();
                        var cuts=packets.stream().filter(p->p.angle()==ShuoSilentLineVfx.CUT).toList();
                        if(i==0){
                            h.assertTrue(!seams.isEmpty()&&cuts.size()==1,"Normal straight dash refreshes one measured seam and resolves one cutting event");
                            long hit=packets.stream().filter(p->p.angle()==ShuoSkillVfx.CONTACT).mapToLong(AbilityVfxPayload::started).min().orElseThrow();
                            h.assertTrue(cuts.getFirst().started()==hit&&targets.get(i).getHealth()<1000,"Double cut is emitted on the actual delayed damage tick, not a fixed visual timer");
                            h.assertTrue(cuts.getFirst().from().distanceTo(origins.get(i).add(0,.9,0))<.001&&cuts.getFirst().to().distanceTo(origins.get(i).add(0,.9,12))<.001,"Cut is anchored to actual full motion path");
                            int cutIndex=packets.indexOf(cuts.getFirst());
                            h.assertTrue(seams.stream().noneMatch(p->p.started()>hit)
                                    &&packets.subList(cutIndex+1,packets.size()).stream().noneMatch(p->p.angle()==ShuoSilentLineVfx.SEAM),
                                    "No retained seam refresh occurs after the actual cut packet");
                            h.assertTrue(packets.subList(packets.indexOf(seams.getLast())+1,cutIndex).stream()
                                    .anyMatch(p->p.kind()==AbilityVfxGeometry.CANCEL&&p.variant()==6),
                                    "A same-tick final seam is explicitly cancelled before the cut is displayed");
                        }else{
                            h.assertTrue(cuts.isEmpty()&&targets.get(i).getHealth()==1000,"Brief interruption or first-step wall never fabricates a cut or damage");
                            if(i==1)h.assertTrue(!seams.isEmpty(),"Interrupted test really travelled and retained a path before control");
                            if(i==2)h.assertTrue(seams.isEmpty(),"Blocked first step paints no future path through a wall");
                        }
                    }
                }
                if(age==55){done[0]=true;cleanup.run();System.out.println("[MYRIAD-SHUO-SILENT-EVENTS] PASS: real cast, actual retained path, no early damage, exact damage-tick cut, brief CC and first-wall cleanup");}
            }catch(Throwable failure){done[0]=true;cleanup.run();throw failure;}
        });}
    }
}
