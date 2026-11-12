package com.myriad.game;
import com.myriad.character.skills.shuo.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Direction, finite lifetime, actual segment bounds and differentiated layer budgets. */
final class ShuoDashChecks {
    static void start(GameTestHelper h){
        var players=new ArrayList<net.minecraft.server.level.ServerPlayer>();var observers=new ArrayList<ShuoWorldVfxChecks.Observer>();
        var origins=new ArrayList<Vec3>();var saved=new HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int i=0;i<3;i++){
            var at=Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(55,210+i*8,5)));
            var p=ShuoChecks.player(h,at,new ArrayList<>());players.add(p);origins.add(at);observers.add(ShuoWorldVfxChecks.observe(h,at.add(8,0,0)));
            ShuoCombat.draw(p,0,9);com.myriad.player.PlayerDataProvider.get(p).setSkillLevel("shuo_yishan",1);
        }
        boolean[] done={false};
        Runnable cleanup=()->{saved.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));observers.forEach(ShuoWorldVfxChecks::cleanup);players.forEach(p->{com.myriad.character.ability.AbilityRuntime.clear(p);p.discard();});};
        for(int n=1;n<=24;n++){int age=n;h.runAfterDelay(n,()->{
            if(done[0])return;try{
                if(age==2){
                    var p=players.getFirst();for(int x=-1;x<=1;x++)for(int y=0;y<3;y++){
                        var pos=p.blockPosition().offset(x,y,1);saved.put(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    }
                    for(var player:players)com.myriad.character.SkillSystem.cast(player,2);
                }
                if(age==5||age==6)com.myriad.player.PlayerDataProvider.get(players.get(1)).setBuff("g1_stun",age==5?1:0);
                for(int i=0;i<3;i++){
                    var p=players.get(i);
                    if(com.myriad.character.ability.AbilityRuntime.moving(p))p.move(net.minecraft.world.entity.MoverType.SELF,
                        com.myriad.character.ability.AbilityTargeting.clampPrediction(p.position(),origins.get(i).add(0,0,4),p.getDeltaMovement()));
                    com.myriad.character.ability.AbilityRuntime.tick(p);
                }
                if(age==20){
                    for(int i=0;i<3;i++){
                        int owner=players.get(i).getId();var packets=observers.get(i).packets().stream().filter(p->p.owner()==owner).toList();
                        long starts=packets.stream().filter(p->p.angle()==ShuoDashVfx.DEPART).count(),ends=packets.stream().filter(p->p.angle()==ShuoDashVfx.ARRIVE).count();
                        h.assertTrue(starts==(i==0?0:1)&&ends==(i==2?1:0),"Blocked/no-step and interrupted paths cannot fabricate launch/arrival bursts, fixture="+i+" start="+starts+" end="+ends);
                        if(i==0)h.assertTrue(packets.stream().noneMatch(p->p.angle()==305||p.angle()==306),"No trail through a first-step wall");
                    }
                }
                if(age==24){done[0]=true;cleanup.run();System.out.println("[MYRIAD-SHUO-DASH-EVENTS] PASS: real public dash, blocked-first-step silence, brief-control no false arrival, exactly-once phases and actual endpoints");}
            }catch(Throwable error){done[0]=true;cleanup.run();throw error;}
        });}
    }
    static void verify(GameTestHelper h){
        int samples=0;
        for(int index:new int[]{2,4,6,7})for(int signal:new int[]{305,306,315,316})for(var direction:List.of(new Vec3(0,0,1),new Vec3(1,.4,-1).normalize(),new Vec3(0,1,0))){
            var at=new Vec3(12,20,13);var packet=ShuoVisuals.recipe(1,-1,index,signal,at,at.add(direction.scale(1.5)),.85f,7,100);
            for(int stage=0;stage<28;stage++)for(boolean low:new boolean[]{false,true}){
                var mesh=ShuoDashVfx.build(packet,(stage+.25f)*7/28,low);
                h.assertTrue(!mesh.isEmpty()&&mesh.size()<=ShuoDashVfx.MAX_QUADS,"Motion graphics stay nonempty inside lifespan and bounded under the existing render budget");
                if(signal==305||signal==306){
                    h.assertTrue(mesh.size()>=(low?12:40),"Layered motion is more than the previous two planes");
                    for(var q:mesh)for(var p:List.of(q.a(),q.b(),q.c(),q.d())){
                        double u=p.subtract(at).dot(direction);
                        h.assertTrue(u>=-1E-6&&u<=1.500001,"No plane goes ahead of the measured endpoint or behind its start");
                    }
                }
                for(var q:mesh){
                    h.assertTrue(q.alpha()>0&&q.alpha()<=1&&q.material()>=75&&q.material()<=78,"Only existing HD alpha materials, without dust cubes");
                    for(var p:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(ShuoSkillVfx.bounds(packet).contains(p),"Culling encloses every ornate wing and wake");
                }
                samples++;
            }
            h.assertTrue(ShuoDashVfx.ownerOpacity(packet)>.3&&ShuoDashVfx.ownerOpacity(packet)<.6,"Owner keeps readable but reduced first-person motion effects");
            for(float age:new float[]{-1,7,8,Float.NaN})h.assertTrue(ShuoDashVfx.build(packet,age,false).isEmpty(),"No frozen motion sheets outside their absolute lifetime");
        }
        var styles=new HashSet<Integer>();
        for(int index:new int[]{2,4,6,7})styles.add(ShuoDashVfx.build(ShuoVisuals.recipe(1,-1,index,305,Vec3.ZERO,new Vec3(0,0,1),.85f,7,0),2,false).hashCode());
        h.assertTrue(styles.size()==4,"Retreat, flash, ultimate and combo pursuit have distinct silhouettes/power");
        h.assertTrue(ShuoDashVfx.ownerOpacity(ShuoVisuals.recipe(1,-1,5,302,Vec3.ZERO,new Vec3(0,0,1),3,7,0))==1,"Existing non-motion skills keep their brightness");
        System.out.println("[MYRIAD-SHUO-DASH] PASS: "+samples+" motion/phase/direction/LOD samples, layered HD materials, endpoint bounds, style differentiation, owner readability and finite lifetime");
    }
}
