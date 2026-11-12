package com.myriad.game;

import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityFormulaText;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.fenjue.rework.FlameSlashAim;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Public cast, real tick-separated hits and target movement/lifecycle cancellation. */
final class FlameSlashReleaseChecks {
    static void verify(GameTestHelper h){new Fixture(h).start();}
    private static final class Fixture {
        final GameTestHelper h;
        final List<LivingEntity> targets=new ArrayList<>();
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
        final List<AbilityVfxPayload> packets=new ArrayList<>();
        final com.myriad.character.ability.AbilitySkill skill=FirstGroupSkills.node("fenjue",2);
        final ServerPlayer actor;
        final Vec3 origin;
        boolean ended;
        Fixture(GameTestHelper h){
            this.h=h;actor=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
            var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,actor,
                net.minecraft.server.network.CommonListenerCookie.createInitial(actor.getGameProfile(),false)){
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
                    if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket cp&&cp.payload() instanceof AbilityVfxPayload cue&&cue.owner()==actor.getId())packets.add(cue);
                }
            };
            actor.connection.markClientLoaded();origin=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(45,315,110)));
        }
        void start(){
            try{
                var feet=BlockPos.containing(origin);
                for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)for(int y=-1;y<=3;y++){
                    var at=feet.offset(x,y,z);blocks.put(at,h.getLevel().getBlockState(at));
                    h.getLevel().setBlockAndUpdate(at,(y==-1?Blocks.STONE:Blocks.AIR).defaultBlockState());
                }
                actor.setNoGravity(true);actor.setPos(origin);h.getLevel().addNewPlayer(actor);
                var d=PlayerDataProvider.get(actor);d.setCharacterId("fenjue");d.setLevel(20);d.setFaction(Faction.XUANXIAO);
                actor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);actor.setHealth(1000);d.setModMaxHealth(1000);d.setModHealthInitialized(true);
                actor.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);actor.getAttribute(ModAttributes.COOLDOWN_REDUCTION).setBaseValue(0);
                d.setSkillLevel(skill.getId(),1);
                for(int i=0;i<7;i++){
                    var t=h.spawn(EntityTypes.PIG,new BlockPos(45,315,110));t.setNoAi(true);t.setNoGravity(true);
                    t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);t.setHealth(1000);targets.add(t);
                }
                after(2,()->runAim(0));
            }catch(Throwable failure){cleanup();throw failure;}
        }
        void after(int ticks,Runnable action){h.runAfterDelay(ticks,()->{if(ended)return;try{action.run();}catch(Throwable failure){cleanup();throw failure;}});}
        void reset(float pitch,float yaw){
            AbilityRuntime.clear(actor);actor.setPos(origin.add(0,8,0));actor.setXRot(pitch);actor.setYRot(yaw);
            var d=PlayerDataProvider.get(actor);d.setCharacterId("fenjue");d.setCooldown(skill.getId(),0);d.setMana(700);
            actor.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(100);
            for(var t:targets){t.setHealth(1000);t.invulnerableTime=0;t.setPos(origin.add(10,8,10));}
        }
        void place(int index,Vec3 center){var t=targets.get(index);t.setPos(center.add(0,-t.getBbHeight()*.5,0));}
        void cast(){
            boolean oldCd=TrainingMode.isNoCooldown(),oldMana=TrainingMode.isNoMana();
            try{TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);packets.clear();SkillSystem.cast(actor,skill.slot());}
            finally{TrainingMode.setNoCooldown(oldCd);TrainingMode.setNoMana(oldMana);}
            var d=PlayerDataProvider.get(actor);
            h.assertTrue(d.getMana()==700-skill.getManaCost()&&d.getCooldown(skill.getId())==skill.getCooldownTicks(1),"Mana and cooldown committed exactly once");
            var arcs=packets.stream().filter(v->v.theme()==2&&v.variant()==2&&v.kind()==ARC).toList();
            h.assertTrue(arcs.size()==1&&arcs.getFirst().from().distanceTo(actor.getEyePosition())<1E-6&&arcs.getFirst().to().subtract(arcs.getFirst().from()).normalize().dot(actor.getLookAngle().normalize())>.99999,"One eye/look-aligned recipe serves both physical strokes; arcs="+arcs+" eye="+actor.getEyePosition()+" look="+actor.getLookAngle());
            h.assertTrue(packets.stream().noneMatch(v->v.theme()==2&&v.kind()==SEAL),"No flame ring on cast");
            for(var t:targets)h.assertTrue(t.getHealth()==1000,"Damage waits for the slash rather than preceding its image");
        }
        void hp(int index,float expected,String why){h.assertTrue(Math.abs(targets.get(index).getHealth()-expected)<.02,why+" hp="+targets.get(index).getHealth());}
        void runAim(int index){
            float[][] angles={{0,0},{-65,30},{65,200},{-90,130},{90,270}};
            if(index==angles.length){movement();return;}
            reset(angles[index][0],angles[index][1]);var eye=actor.getEyePosition();var f=actor.getLookAngle();
            var side=FlameSlashAim.basis(f,actor.getYRot()).side();
            place(0,eye.add(f.scale(3)));place(1,eye.add(f.scale(6.5)));
            place(2,eye.subtract(f.scale(3)));place(3,eye.add(side.scale(4)));place(4,eye.add(f.scale(8.5)));
            after(1,()->{
                cast();actor.setYRot(actor.getYRot()+180);
                after(2,()->{AbilityRuntime.tick(actor);hp(0,1000,"No early damage");});
                after(3,()->{
                    AbilityRuntime.tick(actor);AbilityRuntime.tick(actor);
                    hp(0,932.5f,"First stroke is exactly half of 135");hp(1,932.5f,"First blade hits visible far target beyond old range");
                    for(int i=2;i<targets.size();i++)hp(i,1000,"Rear/side/outside arc unaffected");
                });
                after(7,()->{
                    AbilityRuntime.tick(actor);AbilityRuntime.tick(actor);
                    hp(0,865,"Second stroke completes original total, once only");hp(1,865,"Second blade reaches the visible far target");
                    for(int i=2;i<targets.size();i++)hp(i,1000,"No rear, side or beyond-arc hit after turning");
                });
                after(9,()->{AbilityRuntime.tick(actor);hp(0,865,"No third hit during visual tail");runAim(index+1);});
            });
        }
        void movement(){
            reset(0,0);var eye=actor.getEyePosition();var f=actor.getLookAngle();
            place(0,eye.add(f.scale(3)));place(1,eye.add(f.scale(3)).add(6,0,0));
            place(2,eye.add(f.scale(5)));var wall=BlockPos.containing(eye.add(f.scale(4)));
            for(int x=-1;x<=1;x++)for(int y=-2;y<=2;y++){
                var at=wall.offset(x,y,0);blocks.putIfAbsent(at,h.getLevel().getBlockState(at));h.getLevel().setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());
            }
            after(1,()->{
                cast();
                after(3,()->{AbilityRuntime.tick(actor);hp(0,932.5f,"Exit target first stroke");hp(1,1000,"Entering target not in first stroke");hp(2,1000,"Wall blocks first stroke");
                    place(0,eye.add(f.scale(3)).add(-6,0,0));place(1,eye.add(f.scale(3)));actor.setPos(actor.position().add(5,0,0));
                });
                after(7,()->{AbilityRuntime.tick(actor);hp(0,932.5f,"Exiting target avoids second stroke");hp(1,932.5f,"New target receives only second stroke");hp(2,1000,"Wall still blocks second stroke");});
                after(9,()->cancel(0));
            });
        }
        void cancel(int test){
            if(test==4){
                h.assertTrue(AbilityFormulaText.evaluate("32.5＋9(r－1)＋35%S",1,100).equals("67.5"),"Tooltip computes actual per-stroke damage");
                System.out.println("[MYRIAD-FLAME-3D-RELEASE] PASS: horizontal/sloped/vertical public casts, two 67.5 hits, visible 6.5-block target, exclusions and cast-locked direction");
                System.out.println("[MYRIAD-FLAME-FORWARD-RELEASE] PASS: tick 3/7, no early/duplicate/tail damage, target enters/exits, fixed origin, walls, one cost/CD; death/role/reset/mod-off cancel");
                cleanup();return;
            }
            reset(0,0);place(0,actor.getEyePosition().add(0,0,3));
            after(1,()->{cast();
                after(3,()->{AbilityRuntime.tick(actor);hp(0,932.5f,"First hit before lifecycle change");
                    switch(test){case 0->AbilityRuntime.clear(actor);case 1->PlayerDataProvider.get(actor).setCharacterId("yingxiao");case 2->actor.setHealth(0);case 3->ModState.setEnabled(false);}
                    AbilityRuntime.tick(actor);
                    if(test==2)actor.setHealth(1000);
                    if(test==3)ModState.setEnabled(true);
                });
                after(8,()->{AbilityRuntime.tick(actor);hp(0,932.5f,"Cancelled owner cannot apply second hit, case="+test);cancel(test+1);});
            });
        }
        void cleanup(){
            if(ended)return;ended=true;AbilityRuntime.clear(actor);h.getLevel().removePlayerImmediately(actor,Entity.RemovalReason.DISCARDED);
            targets.forEach(Entity::discard);blocks.forEach((at,state)->h.getLevel().setBlockAndUpdate(at,state));
        }
    }
}
