package com.myriad.game;

import com.myriad.character.SkillSystem;
import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.network.AbilityMotionPayload;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.character.skills.fenjue.rework.FlameStepVfx;
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
import java.util.function.Consumer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.minecraft.sounds.SoundSource;

/** Real skill requests, collision-stepped mock motion and original charged damage lifecycle. */
final class FenjueReleaseChecks {
    private static boolean step(int index){return index<2||index==4;}
    private record Heard(String id,Vec3 at,long tick,boolean server,SoundSource source,float volume){}
    private record Fixture(int index,ServerPlayer p,Vec3 origin,List<LivingEntity> targets,List<AbilityMotionPayload> motions,Map<BlockPos,BlockState> blocks,List<AbilityVfxPayload> cues,ServerPlayer viewer){}
    static void start(GameTestHelper h){
        var fixtures=new ArrayList<Fixture>();boolean noCd=TrainingMode.isNoCooldown(),noMana=TrainingMode.isNoMana();
        var audio=new ArrayList<Heard>();Consumer<PlayLevelSoundEvent.AtPosition> listener=e->{if(e.getSound()!=null){
            String id=e.getSound().value().location().getPath();if(id.startsWith("skill_fenjue_step_")||id.startsWith("skill_fenjue_burst_")||id.equals("foley_fire"))
                audio.add(new Heard(id,e.getPosition(),e.getLevel().getGameTime(),!e.getLevel().isClientSide(),e.getSource(),e.getOriginalVolume()));
        }};NeoForge.EVENT_BUS.addListener(listener);
        try{
            TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
            for(int i=0;i<5;i++){
                var f=fixture(h,i);fixtures.add(f);var d=PlayerDataProvider.get(f.p);var skill=FirstGroupSkills.node("fenjue",step(i)?3:5);
                SkillSystem.cast(f.p,skill.slot());
                h.assertTrue(d.getMana()==700-skill.getManaCost()&&d.getCooldown(skill.getId())==skill.getCooldownTicks(step(i)?1:7),"Normal one-time mana/CD: "+i);
                if(step(i)){
                    h.assertTrue(f.p.position().equals(f.origin),"Flame Step starts with motion, never a teleport");
                    h.assertTrue(Math.abs(f.p.getDeltaMovement().length()-8.0/6)<1E-6,"Eight blocks over original six guided ticks");
                    var cue=f.motions.getFirst();h.assertTrue(new Vec3(cue.tx(),cue.ty(),cue.tz()).distanceTo(f.origin.add(0,0,8))<1E-6,"Client receives exact 8-block endpoint, including looking down");
                    h.assertTrue(f.cues.stream().filter(FlameStepVfx::sides).count()==1&&f.cues.stream().noneMatch(FlameStepVfx::finish),"Body accents/hold start with real dash; no premature endpoint slash");
                    h.assertTrue(heard(audio,f,"skill_fenjue_step_wind").size()==1&&heard(audio,f,"skill_fenjue_step_slash").isEmpty(),"One dash whoosh, no slash before reaching the real endpoint");
                    SkillSystem.cast(f.p,skill.slot());h.assertTrue(heard(audio,f,"skill_fenjue_step_wind").size()==1,"Repeated input/cooldown cannot echo dash sound");
                }else {h.assertTrue(heard(audio,f,"skill_fenjue_burst_charge").size()==1&&heard(audio,f,"skill_fenjue_burst_release").isEmpty(),"One charge cue, no premature explosion");
                    h.assertTrue(AbilityRuntime.channeling(f.p),"Burst still has a real 13-tick interruptible windup");
                    h.assertTrue(Math.abs(f.p.getAttributeValue(Attributes.MOVEMENT_SPEED)/f.p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue()-.25)<.001,"Burst walks at exactly a quarter speed instead of freezing");}
            }
        }catch(Throwable failure){NeoForge.EVENT_BUS.unregister(listener);fixtures.forEach(FenjueReleaseChecks::cleanup);throw failure;}
        finally{TrainingMode.setNoCooldown(noCd);TrainingMode.setNoMana(noMana);}
        boolean[] done={false};
        for(int tick=1;tick<=20;tick++){int elapsed=tick;h.runAfterDelay(tick,()->{
            if(done[0])return;
            try{
                if(elapsed==4)PlayerDataProvider.get(fixtures.get(3).p).setBuff("g1_stun",30);
                if(elapsed==2)PlayerDataProvider.get(fixtures.get(4).p).setBuff("g1_stun",30);
                for(var f:fixtures){
                    if(f.index==2&&elapsed<=10)f.p.move(MoverType.SELF,new Vec3(.12,0,0));
                    if(step(f.index)&&AbilityRuntime.moving(f.p))f.p.move(MoverType.SELF,AbilityTargeting.clampPrediction(f.p.position(),f.origin.add(0,0,8),f.p.getDeltaMovement()));
                    AbilityRuntime.tick(f.p);
                    if(f.index==0)h.assertTrue(f.p.getZ()<=f.origin.z+8+1E-6,"No motion overshoot");
                    if(f.index==1)h.assertTrue(f.p.getZ()<f.origin.z+4&&f.targets.getFirst().getHealth()==1000,"Body sweep stops before wall; no hit behind wall");
                    if(!step(f.index)&&elapsed<13)h.assertTrue(f.targets.getFirst().getHealth()==1000,"No early damage during visual charge");
                }
                if(elapsed==6){
                    var f=fixtures.get(0);
                    h.assertTrue(f.p.position().distanceTo(f.origin.add(0,0,8))<1E-5&&!AbilityRuntime.moving(f.p),"Full 8-block motion completes in six steps");
                    var ends=f.cues.stream().filter(FlameStepVfx::finish).toList();
                    h.assertTrue(ends.size()==1&&ends.getFirst().from().distanceTo(f.p.getEyePosition())<1E-6&&ends.getFirst().started()==f.p.level().getGameTime(),"Exactly one endpoint slash at actual completion position/time");
                    h.assertTrue(f.cues.stream().filter(FlameStepVfx::sides).allMatch(c->c.anchor()==f.p.getId()&&c.life()==3),"Short attached side streams follow the moving body, not the old cast location");
                    var blocked=fixtures.get(1).cues.stream().filter(FlameStepVfx::finish).toList();
                    h.assertTrue(blocked.size()==1&&blocked.getFirst().started()<ends.getFirst().started()&&blocked.getFirst().from().z<fixtures.get(1).origin.z+4,"Collision causes an early endpoint slash before the wall");
                    for(int index:new int[]{0,1}){
                        var owner=fixtures.get(index);var sound=heard(audio,owner,"skill_fenjue_step_slash");var cue=owner.cues.stream().filter(FlameStepVfx::finish).findFirst().orElseThrow();
                        h.assertTrue(sound.size()==1&&sound.getFirst().tick==cue.started()&&sound.getFirst().at.distanceTo(owner.p.position())<1E-6,"Single slash sound uses the same actual endpoint/time as its VFX, including collision");
                    }
                }
                if(elapsed==20){
                    h.assertTrue(Math.abs(fixtures.get(0).targets.getFirst().getHealth()-925)<.05,"Target beyond old 4-block dash is hit once with unchanged 35+40%S damage");
                    h.assertTrue(Math.abs(fixtures.get(2).targets.getFirst().getHealth()-630)<.05,"New 5.8-block Burst target receives unchanged rank7 / S100 formula exactly once");
                    h.assertTrue(fixtures.get(2).p.getX()-fixtures.get(2).origin.x>1&&!SkillChargeSystem.active(fixtures.get(2).p)
                            &&Math.abs(fixtures.get(2).p.getAttributeValue(Attributes.MOVEMENT_SPEED)-fixtures.get(2).p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue())<.001,"Walking more than old 0.8-block cancellation threshold completes Burst and restores normal movement");
                    h.assertTrue(fixtures.get(2).targets.getLast().getHealth()==1000,"Burst excludes 6.2-block target");
                    h.assertTrue(fixtures.get(3).targets.getFirst().getHealth()==1000,"Interrupted Burst causes no completion damage");
                    for(int index:new int[]{2,3}){var f=fixtures.get(index);
                        h.assertTrue(heard(audio,f,"skill_fenjue_burst_release").size()==(index==2?1:0)&&heard(audio,f,"foley_fire").isEmpty(),"Only a completed charged burst plays one explosion; no generic duplicate");}
                    for(var f:fixtures)if(f.index<2)h.assertTrue(f.cues.stream().filter(FlameStepVfx::finish).count()==1,"No repeated endpoint slash after completion");
                    var interrupted=fixtures.get(4);
                    h.assertTrue(interrupted.cues.stream().noneMatch(FlameStepVfx::finish)&&interrupted.cues.stream().anyMatch(c->c.kind()==AbilityVfxGeometry.CANCEL&&c.variant()==3),"Controlled dash cancels its hold/body cues, never plays a successful finish slash");
                    h.assertTrue(interrupted.targets.getFirst().getHealth()==1000,"Interrupted dash adds no endpoint damage");
                    for(var f:fixtures)if(step(f.index)){
                        h.assertTrue(heard(audio,f,"skill_fenjue_step_wind").size()==1&&heard(audio,f,"skill_fenjue_step_slash").size()==(f.index==4?0:1),"Exactly one whoosh and one successful slash; interrupted dash has no slash");
                        h.assertTrue(heard(audio,f,"foley_fire").isEmpty(),"No old generic fire sound stacked over Step's recordings");
                    }
                    h.assertTrue(audio.stream().filter(s->s.id.startsWith("skill_fenjue_step_")).allMatch(s->s.server&&s.source==SoundSource.PLAYERS&&s.volume<=.70f),"Server-authoritative nearby sound, bounded volume, no client echo");
                    NeoForge.EVENT_BUS.unregister(listener);
                    done[0]=true;fixtures.forEach(FenjueReleaseChecks::cleanup);
                    System.out.println("[MYRIAD-FLAME-STEP-AUDIO] PASS: public dash start / actual normal and wall endpoints, no early/duplicate slash, controlled cancellation, no generic echo, unchanged damage/cost/CD");
                    System.out.println("[MYRIAD-FENJUE-RELEASE] PASS: actual 8-block motion, endpoint packets, six steps, wall and far single-hit; 13-tick / 6-block burst, unchanged formula/cost/CD, range and stun interruption");
                }
            }catch(Throwable failure){done[0]=true;NeoForge.EVENT_BUS.unregister(listener);fixtures.forEach(FenjueReleaseChecks::cleanup);throw failure;}
        });}
    }
    private static List<Heard> heard(List<Heard> sounds,Fixture f,String id){return sounds.stream().filter(s->s.id.equals(id)&&Math.abs(s.at.y-f.origin.y)<.01&&Math.abs(s.at.x-f.origin.x)<2&&s.at.z>f.origin.z-1&&s.at.z<f.origin.z+10).toList();}
    private static Fixture fixture(GameTestHelper h,int index){
        var level=h.getLevel();var floor=h.absolutePos(new BlockPos(60,280+index*8,70));var blocks=new HashMap<BlockPos,BlockState>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=10;z++)for(int y=0;y<4;y++){
            var at=floor.offset(x,y,z);blocks.put(at,level.getBlockState(at));level.setBlockAndUpdate(at,(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());
        }
        if(index==1)for(int y=1;y<=2;y++)level.setBlockAndUpdate(floor.offset(0,y,4),Blocks.STONE.defaultBlockState());
        var motions=new ArrayList<AbilityMotionPayload>();var cues=new ArrayList<AbilityVfxPayload>();var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),connection,p,net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket cp){
                if(cp.payload() instanceof AbilityMotionPayload cue)motions.add(cue);
                if(cp.payload() instanceof AbilityVfxPayload cue&&cue.owner()==p.getId())cues.add(cue);
            }}
        };
        p.connection.markClientLoaded();p.setNoGravity(true);var origin=Vec3.atBottomCenterOf(floor.above());p.setPos(origin);p.setYRot(0);p.setXRot(step(index)?89:0);
        // The caster is advanced explicitly below. Register only a passive observer, otherwise
        // the real PlayerTick event and the fixture would both advance the same dash in one tick.
        var viewer=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var viewerConnection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(viewerConnection);
        viewer.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),viewerConnection,viewer,net.minecraft.server.network.CommonListenerCookie.createInitial(viewer.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket cp&&cp.payload() instanceof AbilityVfxPayload cue&&cue.owner()==p.getId())cues.add(cue);
            }
        };
        viewer.connection.markClientLoaded();viewer.setNoGravity(true);viewer.setPos(origin.add(3,0,0));PlayerDataProvider.get(viewer).setFaction(Faction.XUANXIAO);level.addNewPlayer(viewer);
        var d=PlayerDataProvider.get(p);d.setCharacterId("fenjue");d.setLevel(20);d.setMana(700);d.setFaction(Faction.XUANXIAO);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);p.setHealth(1000);d.setModMaxHealth(1000);d.setModHealthInitialized(true);
        p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(100);p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);p.getAttribute(ModAttributes.COOLDOWN_REDUCTION).setBaseValue(0);
        var skill=FirstGroupSkills.node("fenjue",step(index)?3:5);d.setSkillLevel(skill.getId(),step(index)?1:7);
        var targets=new ArrayList<LivingEntity>();
        for(double z:step(index)?new double[]{7}:new double[]{5.8,6.2}){
            var target=h.spawn(EntityTypes.PIG,new BlockPos(60,281+index*8,70));target.setPos(origin.add(0,0,z));target.setNoAi(true);target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);targets.add(target);
        }
        return new Fixture(index,p,origin,targets,motions,blocks,cues,viewer);
    }
    private static void cleanup(Fixture f){AbilityRuntime.clear(f.p);AbilityRuntime.clear(f.viewer);f.targets.forEach(Entity::discard);f.blocks.forEach((at,state)->f.p.level().setBlockAndUpdate(at,state));f.p.discard();f.viewer.level().removePlayerImmediately(f.viewer,Entity.RemovalReason.DISCARDED);}
}
