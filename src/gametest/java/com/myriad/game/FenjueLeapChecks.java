package com.myriad.game;

import com.myriad.character.SkillSystem;
import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.fenjue.rework.HeavenFireSeaSkill;
import com.myriad.network.FlameSkillViewPayload;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import com.myriad.game.GameTestSounds.PlayedSound;
import java.util.function.Consumer;
import static com.myriad.network.FlameSkillViewPayload.*;

/** Actual selected/confirmed ultimate, collision-stepped motion and successful-landing-only damage. */
final class FenjueLeapChecks {
    private record Hit(long tick,float hp){}
    private record Tracked(LivingEntity target,List<Hit> hits){}
    private record Heard(String id,Vec3 at,long tick){}
    private record Fixture(int index,ServerPlayer p,Vec3 origin,Vec3 target,LivingEntity victim,
                           List<FlameSkillViewPayload> cues,Map<BlockPos,BlockState> saved,double[] peak,List<Hit> losses,List<Tracked> secondary){}
    static void run(GameTestHelper h){
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        start(h,h::succeed);
    }

    static void start(GameTestHelper h){
        start(h,()->{});
    }

    private static void start(GameTestHelper h,Runnable complete){
        var fixtures=new ArrayList<Fixture>();boolean noCd=TrainingMode.isNoCooldown(),noMana=TrainingMode.isNoMana();
        var heard=new ArrayList<Heard>();Consumer<PlayedSound> listener=e->{if(e.getSound()!=null&&e.getSound().value().location().getPath().startsWith("skill_fenjue_sea_"))
            heard.add(new Heard(e.getSound().value().location().getPath(),e.getPosition(),e.getLevel().getGameTime()));};GameTestSounds.addListener(listener);
        try{
            TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
            for(int i=0;i<4;i++){
                var f=fixture(h,i);fixtures.add(f);var skill=FirstGroupSkills.node("fenjue",6);var d=PlayerDataProvider.get(f.p);
                d.setFirstGroupAttribute("strength",i==1?1:0);
                d.setFirstGroupAttribute("wisdom",i==3?1:0);
                FirstGroupGrowth.apply(f.p,d);
                if(i==0){
                    var ceiling=BlockPos.containing(f.origin.add(0,5,7));set(h,f.saved,ceiling,Blocks.STONE.defaultBlockState());
                    SkillSystem.cast(f.p,skill.slot());AbilityRuntime.confirm(f.p);
                    h.assertTrue(d.getMana()==700&&d.getCooldown(skill.getId())==0&&f.cues.isEmpty(),"Low ceiling rejects the 5-block arc without payment or a fake jump gesture");
                    set(h,f.saved,ceiling,Blocks.AIR.defaultBlockState());AbilityRuntime.cancel(f.p);
                }
                SkillSystem.cast(f.p,skill.slot());h.assertTrue(d.getPlacementType()==106&&f.cues.isEmpty(),"Fire sea selection is not a predicted cast");
                AbilityRuntime.confirm(f.p);
                h.assertTrue(AbilityRuntime.moving(f.p)&&f.p.position().equals(f.origin),"Confirmed fire sea starts motion, not teleport");
                h.assertTrue(d.getMana()==700-skill.getManaCost()&&d.getCooldown(skill.getId())==skill.getCooldownTicks(1),"One unchanged cost/CD");
                h.assertTrue(f.cues.size()==1&&f.cues.getFirst().action()==SEA_FLIGHT,"Jump has a confirmed owner-only hand cue");
            }
        }catch(Throwable failure){GameTestSounds.removeListener(listener);fixtures.forEach(f->cleanup(h,f));throw failure;}
        finally{TrainingMode.setNoCooldown(noCd);TrainingMode.setNoMana(noMana);}
        boolean[] done={false};
        for(int tick=1;tick<=111;tick++){int elapsed=tick;h.runAfterDelay(tick,()->{
            if(done[0])return;
            try{
                if(elapsed==5)PlayerDataProvider.get(fixtures.get(2).p).setBuff("g1_stun",40);
                for(var f:fixtures){
                    if(elapsed%20==0)CombatChunks.hold(h.getLevel(),BlockPos.containing(f.target));
                    if(elapsed==4&&f.index!=2){
                        var data=PlayerDataProvider.get(f.p);
                        data.setFirstGroupAttribute("strength",12);
                        data.setFirstGroupAttribute("wisdom",12);
                        FirstGroupGrowth.apply(f.p,data);
                    }
                    if(AbilityRuntime.moving(f.p))f.p.move(MoverType.SELF,f.p.getDeltaMovement());
                    f.peak[0]=Math.max(f.peak[0],f.p.getY()-f.origin.y);
                    if(!f.secondary.isEmpty()&&f.losses.size()==2&&h.getLevel().getGameTime()==f.losses.getFirst().tick()+21)
                        f.secondary.getLast().target().setPos(f.target.add(-7,0,0));
                    recordStrike(f);
                    if(!f.losses.isEmpty()&&h.getLevel().getGameTime()==f.losses.getFirst().tick()+40)
                        recordStrike(f); // A scheduled pulse must not pay twice if advanced twice in one real tick.
                    if(elapsed<18)h.assertTrue(f.victim.getHealth()==1000,"No area damage or fake slam during flight");
                }
                if(elapsed<18)h.assertTrue(heard.stream().noneMatch(s->s.id.equals("skill_fenjue_sea_impact")),"No landing sound while airborne");
                if(elapsed==25){
                    for(var f:fixtures){
                        if(f.index!=2){
                            h.assertTrue(f.peak[0]>=5-1E-5,"Actual motion reaches five blocks above takeoff, including downhill");
                            h.assertTrue(f.p.position().distanceTo(f.target)<.06&&!AbilityRuntime.moving(f.p),"Actual 14-block landing reached without overshoot: case="+f.index+" pos="+f.p.position()+" expected="+f.target+" peak="+f.peak[0]);
                            double landing=f.index==1?170+20.0/3:170;
                            h.assertTrue(f.losses.size()==1&&Math.abs(f.losses.getFirst().hp()-landing)<.02,
                                    "One landing hit with the cast snapshot and no early field damage, build "+f.index+" losses="+f.losses);
                            h.assertTrue(f.cues.stream().filter(c->c.action()==SEA_LAND).count()==1,"Exactly one actual landing hand cue");
                            var cue=f.cues.stream().filter(c->c.action()==SEA_LAND).findFirst().orElseThrow();
                            h.assertTrue(heard.stream().filter(s->s.id.equals("skill_fenjue_sea_impact")&&s.at.distanceTo(f.p.position())<1E-6&&s.tick==cue.started()).count()==1,"One impact sound at the actual landing location and VFX tick, including downhill");
                        }else h.assertTrue(f.victim.getHealth()==1000&&f.cues.stream().noneMatch(c->c.action()==SEA_LAND)
                                &&f.cues.stream().anyMatch(c->c.action()==CANCEL),"Interrupted flight never deals impact damage or plays a successful landing");
                    }
                    h.assertTrue(heard.stream().filter(s->s.id.equals("skill_fenjue_sea_jump")).count()==4&&heard.stream().filter(s->s.id.equals("skill_fenjue_sea_impact")).count()==3,"Four successful takeoffs and three real landings, no sound from rejected ceiling or controlled interruption");
                    System.out.println("[MYRIAD-FLAME-SEA-AUDIO] PASS: confirmed takeoff, normal/downhill actual landing sound/VFX tick, low ceiling and controlled interruption have no fake impact");
                    System.out.println("[MYRIAD-FENJUE-LEAP] PASS: public select/confirm, 14-block normal/downhill, actual 5-block height, ceiling rejects cost-free, no early impact, one landing hit/cue, controlled cancellation");
                }
                if(elapsed==111){
                    for(var f:fixtures){
                        if(f.index==2){
                            h.assertTrue(f.losses.isEmpty(),"Interrupted leap never creates a damage field");
                            continue;
                        }
                        h.assertTrue(f.losses.size()==5,"One landing and exactly four pulses, build "+f.index+" losses="+f.losses);
                        double pulse=f.index==1?36+4.0/3:36;
                        long landed=f.losses.getFirst().tick();
                        for(int i=1;i<=4;i++){
                            Hit hit=f.losses.get(i);
                            h.assertTrue(hit.tick()-landed==20L*i&&Math.abs(hit.hp()-pulse)<.02,
                                    "Pulses use a 20-real-tick cadence and the original cast snapshot, build "+f.index+" losses="+f.losses);
                        }
                        double expected=f.index==1?326:314;
                        h.assertTrue(Math.abs(1000-f.victim.getHealth()-expected)<.02,"Five hits add one ultimate purchase budget, build "+f.index);
                    }
                    var field=fixtures.getFirst();
                    var staying=field.secondary.getFirst().hits();
                    var leaving=field.secondary.getLast().hits();
                    h.assertTrue(staying.equals(field.losses)&&leaving.equals(field.losses.subList(0,2)),
                            "Each target is sampled independently: stay gets four pulses, leave after first avoids three; stay="+staying+" leave="+leaving);
                    System.out.println("MYRIAD_ROSTER_112 "+new com.google.gson.Gson().toJson(
                            Map.of("scenario","fenjue.sea_stay_leave","primary",field.losses,"stay",staying,"leave",leaving)));
                    for(var f:fixtures)System.out.println("MYRIAD_ROSTER_112 "+new com.google.gson.Gson().toJson(
                            Map.of("scenario","fenjue.fire_sea_timeline","build",f.index,"hits",f.losses)));
                    System.out.println("[MYRIAD-DAMAGE-FIRE-SEA] PASS: physical landing plus four pulses, raw totals 314/326/314, original cast snapshot survives later investment");
                    done[0]=true;GameTestSounds.removeListener(listener);fixtures.forEach(f->cleanup(h,f));
                    complete.run();
                }
            }catch(Throwable failure){done[0]=true;GameTestSounds.removeListener(listener);fixtures.forEach(f->cleanup(h,f));throw failure;}
        });}
    }

    private static void recordStrike(Fixture f){
        var tracked=new ArrayList<>(f.secondary);
        tracked.add(new Tracked(f.victim,f.losses));
        var before=tracked.stream().map(entry->entry.target().getHealth()).toList();
        AbilityRuntime.tick(f.p);
        long now=f.p.level().getGameTime();
        for(int i=0;i<tracked.size();i++){
            Tracked entry=tracked.get(i);
            float loss=before.get(i)-entry.target().getHealth();
            if(loss>0)entry.hits().add(new Hit(now,loss));
        }
    }

    private static BlockPos floor(GameTestHelper h,int index){
        return h.absolutePos(new BlockPos(8,227+20*index,-6));
    }

    private static Fixture fixture(GameTestHelper h,int index){
        var floor=floor(h,index);var saved=new HashMap<BlockPos,BlockState>();
        CombatChunks.prepare(h.getLevel(), floor);
        CombatChunks.prepare(h.getLevel(), floor.south(14));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=16;z++)for(int y=-2;y<=9;y++){
            int ground=index==1&&z>=1?-2:0;
            set(h,saved,floor.offset(x,y,z),(y<=ground?Blocks.STONE:Blocks.AIR).defaultBlockState());
        }
        Vec3 origin=Vec3.atBottomCenterOf(floor.above()),target=origin.add(0,index==1?-1.975:.025,index==1?13.7:14);
        var cues=new ArrayList<FlameSkillViewPayload>();var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var con=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(con);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),con,p,net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket cp&&cp.payload() instanceof FlameSkillViewPayload cue)cues.add(cue);}
        };p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());p.setPos(origin);p.setNoGravity(true);p.setYRot(0);p.setXRot(0);
        if(index==1){var aim=target.add(0,-.025,0).subtract(p.getEyePosition());p.setXRot((float)Math.toDegrees(Math.atan2(-aim.y,aim.z)));}
        var d=PlayerDataProvider.get(p);d.setFaction(Faction.GUARDIAN);d.setCharacterId("fenjue");d.setLevel(20);d.setMana(700);
        FirstGroupGrowth.initialize(p, d);
        var skill=FirstGroupSkills.node("fenjue",6);d.setSkillLevel(skill.getId(),1);
        p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(100);p.getAttribute(ModAttributes.COOLDOWN_REDUCTION).setBaseValue(0);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);p.setHealth(1000);d.setModMaxHealth(1000);d.setModHealthInitialized(true);
        var victim=target(h,target);
        List<Tracked> secondary=index==0?List.of(new Tracked(target(h,target.add(1.5,0,0)),new ArrayList<>()),
                new Tracked(target(h,target.add(-1.5,0,0)),new ArrayList<>())):List.of();
        return new Fixture(index,p,origin,target,victim,cues,saved,new double[]{0},new ArrayList<>(),secondary);
    }

    private static LivingEntity target(GameTestHelper h,Vec3 position){
        // The landing is in the active test section. A remote block chunk load alone does
        // not guarantee that its entities are queryable by the area damage path.
        var victim=h.spawn(EntityTypes.PIG,h.relativeVec(position));
        victim.setPos(position);victim.setNoAi(true);victim.setNoGravity(true);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);victim.setHealth(1000);
        victim.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);
        victim.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(100);
        victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        return victim;
    }
    private static void set(GameTestHelper h,Map<BlockPos,BlockState> saved,BlockPos at,BlockState state){saved.putIfAbsent(at,h.getLevel().getBlockState(at));h.getLevel().setBlockAndUpdate(at,state);}
    private static void cleanup(GameTestHelper h,Fixture f){AbilityRuntime.clear(f.p);f.victim.discard();f.secondary.forEach(entry->entry.target().discard());f.p.discard();f.saved.forEach((at,state)->h.getLevel().setBlockAndUpdate(at,state));}
}
