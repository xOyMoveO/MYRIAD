package com.myriad.game;

import com.myriad.character.SkillSystem;
import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.*;
import com.myriad.player.*;
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

/** Server-entry transactions and real ticks, damage, shields and collision-stepped movement. */
final class AbilityContractChecks {
    private static final class Fixture {
        ServerPlayer player; AbilitySkill skill; AbilityRuntime.Cast cast;
        LivingEntity target; Vec3 origin,point; UUID flight;
        int realtime, pulses; final Map<BlockPos,BlockState> blocks=new HashMap<>();
    }
    static void start(GameTestHelper h){
        var fixtures=new ArrayList<Fixture>();
        boolean noCd=TrainingMode.isNoCooldown(),noMana=TrainingMode.isNoMana();
        TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
        try{
            for(int i=0;i<26;i++){
                var f=fixture(h,i);fixtures.add(f);var p=f.player;var d=PlayerDataProvider.get(p);
                if(i==25){
                    AbilityRuntime.request(p,d,f.skill,4);
                    f.point=AbilityRuntime.context(p,f.skill).point(14);AbilityRuntime.confirm(p);
                    h.assertTrue(SkillChargeSystem.active(p)&&d.getMana()==700,"Ground confirmation starts deferred payment at the selected point");
                }else if(i<=12||i>=18&&i<=21||i==24){
                    f.cast=AbilityRuntime.request(p,d,f.skill,i==24?1:4);
                    h.assertTrue(f.cast!=null&&f.cast.phase()==AbilityRuntime.Phase.WINDUP,"Accepted request starts a distinct unpaid windup: "+i);
                    h.assertTrue(d.getMana()==700&&d.getCooldown(f.skill.getId())==0&&ModShieldSystem.value(p)==0,"Windup has no payment, cooldown or effect");
                    h.assertTrue(!f.cast.commit(),"A retained handle cannot force premature payment");
                    h.assertTrue(AbilityRuntime.request(p,d,f.skill,4)==null,"Duplicate input is rejected, never queued");
                }else if(i==16||i==22){
                    var c=AbilityRuntime.context(p,f.skill);f.cast=c;
                    f.flight=c.actionProjectile(i==22?3:15,i==22?.5:1.5,.8,false,false,(target,back)->c.damage(target,50,DamageCalc.Kind.PHYSICAL));
                    ActionTime.apply(f.flight,p.getUUID(),"flight",i==22?.1:.5,p.level().getGameTime()+50);
                    // Actor and independent projectile rates are separate objects.
                    ActionTime.apply(p.getUUID(),p.getUUID(),"actor",.1,p.level().getGameTime()+50);
                }else{
                    SkillSystem.cast(p,f.skill.slot());
                    h.assertTrue(d.getMana()==700-f.skill.getManaCost()&&d.getCooldown(f.skill.getId())>0,"Original input keeps immediate payment: "+i);
                }
                if(i==0){
                    ActionTime.apply(p.getUUID(),p.getUUID(),"weak",.75,p.level().getGameTime()+100);
                    ActionTime.apply(p.getUUID(),p.getUUID(),"strong",.5,p.level().getGameTime()+100);
                    f.cast.later(4,false,()->f.realtime++);
                    f.cast.repeat(6,2,ignored->f.pulses++);
                }
                if(i==14)ActionTime.apply(p.getUUID(),p.getUUID(),"dash",.5,p.level().getGameTime()+4);
                if(i==17)ActionTime.apply(p.getUUID(),p.getUUID(),"windup",.5,p.level().getGameTime()+100);
                if(i==24)ActionTime.apply(p.getUUID(),p.getUUID(),"short",.5,p.level().getGameTime()+100);
                if(i==3)d.setBuff("g1_root",30); // Root allows stationary casting.
                if(i==23){var c=AbilityRuntime.context(p,f.skill);c.later(4,false,()->c.damage(f.target,50,DamageCalc.Kind.PHYSICAL));}
                if(i==21){
                    f.cast.later(4,false,()->f.realtime++);
                    ActionTime.apply(fixtures.get(0).player.getUUID(),p.getUUID(),"owned",.9,p.level().getGameTime()+100);
                }
            }
        }catch(Throwable failure){fixtures.forEach(AbilityContractChecks::cleanup);throw failure;}
        finally{TrainingMode.setNoCooldown(noCd);TrainingMode.setNoMana(noMana);}
        boolean[] done={false};
        for(int tick=1;tick<=22;tick++){
            int elapsed=tick;
            h.runAfterDelay(tick,()->{
                if(done[0])return;
                boolean nc=TrainingMode.isNoCooldown(),nm=TrainingMode.isNoMana();
                TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
                try{
                    for(int i=0;i<fixtures.size();i++){
                        var f=fixtures.get(i);var p=f.player;var d=PlayerDataProvider.get(p);
                        if(elapsed==2)switch(i){
                            case 1->d.setMana(0);
                            case 2->d.setBuff("g1_stun",20);
                            case 4->SilenceSystem.apply(p,20);
                            case 5->KnockbackBuffer.apply(p,new Vec3(.5,.2,0));
                            case 6->AbilityRuntime.cancel(p);
                            case 7->d.setCharacterId("hanze");
                            case 8->p.setHealth(0);
                            case 9->PlayerDataProvider.onPlayerLoggedOut(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
                            case 10->AbilityRuntime.clear(p);
                            case 11->d.setCooldown(f.skill.getId(),100);
                            case 12->{d.setSkillLevel(f.skill.getId(),0);d.setSkillLevel(FirstGroupSkills.node("lingyuan",5).getId(),1);}
                            case 18->{boolean enabled=ModState.isEnabled();ModState.setEnabled(false);AbilityRuntime.tick(p);ModState.setEnabled(enabled);}
                            case 19->{var other=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
                                h.assertTrue(other!=null,"Fresh test server provides a second dimension");
                                p.setServerLevel(other);AbilityRuntime.cleanup();}
                            case 20->p.discard();
                            case 21->AbilityRuntime.clear(p);
                            case 23->d.setBuff("g1_stun",30);
                            case 25->p.level().setBlockAndUpdate(BlockPos.containing(f.point.add(0,-.08,0)),Blocks.AIR.defaultBlockState());
                        }
                        if(i==0&&elapsed==3)ActionTime.remove(p.getUUID(),p.getUUID(),"strong");
                        if(i==0&&elapsed==4)ActionTime.remove(p.getUUID(),p.getUUID(),"weak");
                        if(i==17&&elapsed==5)ActionTime.remove(p.getUUID(),p.getUUID(),"windup");
                        if(i==14&&AbilityRuntime.moving(p))p.move(MoverType.SELF,
                                AbilityTargeting.clampPrediction(p.position(),f.origin.add(0,0,9),p.getDeltaMovement()));
                        if(i==24&&elapsed==1){
                            SkillChargeSystem.beforeMove(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(p));
                            h.assertTrue(SkillChargeSystem.active(p),"One-tick windup stays movement-locked when local progress is unfinished");
                        }
                        AbilityRuntime.tick(p);
                        // Duplicate scheduling in the same world tick cannot advance local actions twice.
                        if(i==16||i==17)AbilityRuntime.tick(p);
                        if(i==0){
                            if(elapsed<6)h.assertTrue(!f.cast.committed()&&d.getMana()==700,"Changing rate preserves completed work and does not pay early");
                            if(elapsed==4)h.assertTrue(f.realtime==1&&f.pulses==2,"Ground warning and DOT interval remain real time");
                            if(elapsed==6){
                                h.assertTrue(f.cast.committed()&&ModShieldSystem.value(p)>0,"Remaining work completes with real shield output");
                                h.assertTrue(d.getMana()==700-f.skill.getManaCost()&&!f.cast.commit(),"Payment commits exactly once");
                                h.assertTrue(passiveCount(p)==1,"Successful-cast passive fires once at commit");
                            }
                            if(elapsed==8)h.assertTrue(d.getCooldown(f.skill.getId())==f.skill.getCooldownTicks(1)-2,"Cooldown ticks in real time after commit");
                        }
                        if(i==14){
                            if(elapsed==3)h.assertTrue(Math.abs(p.getZ()-f.origin.z-3.6)<.001,"Real dash keeps earlier distance, then advances at the strongest local rate");
                            if(elapsed==8)h.assertTrue(Math.abs(p.getZ()-f.origin.z-9)<.001&&!AbilityRuntime.moving(p)&&f.target.getHealth()<1000,"Rate expiry restores full movement next tick, reaches original endpoint and hits once: distance="+(p.getZ()-f.origin.z)+", moving="+AbilityRuntime.moving(p)+", health="+f.target.getHealth());
                        }
                        if(i==16){
                            if(elapsed==4)h.assertTrue(f.target.getHealth()==1000,"Independent slowed projectile has not arrived early");
                            if(elapsed==9)h.assertTrue(f.target.getHealth()<1000,"Projectile arrives using its own flight rate, independent of slower actor");
                        }
                        if(i==17){
                            if(elapsed==6)h.assertTrue(f.target.getHealth()==1000,"Real Thunder Arc windup retains partial work after rate removal");
                            if(elapsed==12)h.assertTrue(f.target.getHealth()<1000,"Real charged skill releases and hits after remaining work completes");
                        }
                        d.tickCooldowns();
                    }
                    if(elapsed==12){
                        for(int i:new int[]{1,2,4,5,6,7,8,9,10,11,12,18,19,20,21}){
                            var f=fixtures.get(i);var d=PlayerDataProvider.get(f.player);
                            h.assertTrue(!f.cast.committed()&&f.cast.phase()==AbilityRuntime.Phase.CANCELLED,"Interrupted/invalid/lifecycle request cannot commit: "+i);
                            h.assertTrue(d.getMana()==(i==1?0:700)&&ModShieldSystem.value(f.player)==0,"No abandoned request pays or produces a shield: "+i);
                            h.assertTrue(!f.cast.commit(),"Old cancelled handles cannot be replayed: "+i);
                        }
                        h.assertTrue(fixtures.get(24).cast.committed(),"One-tick windup completes after two half-speed ticks");
                        var ground=fixtures.get(25);var groundData=PlayerDataProvider.get(ground.player);
                        h.assertTrue(groundData.getMana()==700&&groundData.getCooldown(ground.skill.getId())==0&&passiveCount(ground.player)==0,
                                "Deferred ground commit rechecks the original point after support disappears");
                        var root=fixtures.get(3);
                        h.assertTrue(root.cast.committed()&&ModShieldSystem.value(root.player)>0&&passiveCount(root.player)==1,"External root permits a stationary committed effect and passive");
                        h.assertTrue(fixtures.get(15).target.getHealth()<1000,"Existing SkillSystem dagger entry still hits at default rate");
                        h.assertTrue(fixtures.get(21).realtime==0,"Full cleanup cancels independently scheduled warnings too");
                    }
                    if(elapsed==22){
                        h.assertTrue(fixtures.get(22).target.getHealth()==1000&&ActionTime.multiplier(fixtures.get(22).flight,h.getLevel().getGameTime())==1,
                                "Flight safety lifetime ends at twice original real lifetime, clears rates and never catches up damage");
                        h.assertTrue(fixtures.get(23).target.getHealth()<1000,"Committed ground warning survives later stun and completes independently");
                        done[0]=true;fixtures.forEach(AbilityContractChecks::cleanup);
                        System.out.println("[MYRIAD-ABILITY-CONTRACT] PASS: real input + deferred commit, partial progress, source min/expiry, unpaid cancellation, resource/branch recheck, passive exactly once, root/stun/silence/forced movement, real timers/CD, dash/projectile/Thunder Arc and lifecycle cleanup");
                    }
                }catch(Throwable failure){done[0]=true;fixtures.forEach(AbilityContractChecks::cleanup);throw failure;}
                finally{TrainingMode.setNoCooldown(nc);TrainingMode.setNoMana(nm);}
            });
        }
    }
    private static int passiveCount(ServerPlayer p){return AbilityRuntime.context(p,FirstGroupSkills.node("lingyuan",1)).memory().count;}
    private static Fixture fixture(GameTestHelper h,int index){
        var f=new Fixture();var level=h.getLevel();var at=h.absolutePos(new BlockPos(-30,80+index*6,7));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=17;z++)for(int y=0;y<=3;y++){
            var pos=at.offset(x,y,z);f.blocks.put(pos,level.getBlockState(pos));level.setBlockAndUpdate(pos,(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());
        }
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);f.player=p;
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };
        p.connection.markClientLoaded();p.setNoGravity(true);f.origin=Vec3.atBottomCenterOf(at.above());p.setPos(f.origin);p.setYRot(0);p.setXRot(0);
        String role=index==14||index==15||index==16?"yingxiao":index==17?"tianxing":"lingyuan";
        int node=index==25?5:index==12||index==14?4:index==15||index==16?2:3;
        var d=PlayerDataProvider.get(p);d.setCharacterId(role);d.setMana(700);d.setSkillLevel(FirstGroupSkills.node(role,1).getId(),1);
        f.skill=FirstGroupSkills.node(role,node);d.setSkillLevel(f.skill.getId(),1);p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(100);
        var target=h.spawn(EntityTypes.PIG,new BlockPos(-30,81+index*6,14));target.setPos(f.origin.add(0,.5,index==22?2.5:7));target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);f.target=target;return f;
    }
    private static void cleanup(Fixture f){
        AbilityRuntime.clear(f.player);ModShieldSystem.clearFor(f.player);KnockbackBuffer.clear(f.player.getUUID());SilenceSystem.clearFor(f.player.getUUID());
        f.target.discard();f.blocks.forEach((pos,state)->f.target.level().setBlockAndUpdate(pos,state));f.player.discard();
    }
}
