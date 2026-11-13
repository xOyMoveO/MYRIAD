package com.myriad.game;

import com.myriad.character.*;
import com.myriad.character.ability.*;
import com.myriad.character.rework.*;
import com.myriad.character.skills.yike.*;
import com.myriad.combat.*;
import com.myriad.player.*;
import com.myriad.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Real input, real payment boundaries and actual target HP/shields; isolated fresh-world fixtures. */
final class YikeChecks {
    private static final int CASES=52;
    private static final class Fixture {
        ServerPlayer p;Villager target;Vec3 origin;ServerLevel level;int index;
        final Map<BlockPos,BlockState> blocks=new HashMap<>();float paid;int shots;long lastShot=-1000;Vec3 aim;
    }
    static void run(GameTestHelper h){
        boolean enabled=ModState.isEnabled(),training=TrainingMode.isEnabled(),nc=TrainingMode.isNoCooldown(),nm=TrainingMode.isNoMana();
        ModState.setEnabled(true);TrainingMode.setEnabled(true);TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
        var fixtures=new ArrayList<Fixture>();boolean[] done={false};
        try{
            for(int i=0;i<CASES;i++){var f=fixture(h,i);fixtures.add(f);var d=PlayerDataProvider.get(f.p);
                if(i==24||i==25||i==50||i==51){d.setSkillLevel(node(0).getId(),0);d.setSkillLevel(node(1).getId(),1);}
                if(i==26){d.setSkillLevel(node(0).getId(),3);d.setSkillLevel(node(2).getId(),5);d.setSkillLevel(node(4).getId(),7);d.setSkillLevel(node(6).getId(),3);f.p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(230);JiarenManager.setDefenses(f.target,50,50);}
                if(i==27)ModShieldSystem.grant(f.target,"test",10000,160);
                if(i==28)f.target.setInvulnerable(true);
                if(i==6||i==7)d.setCooldown(node(2).getId(),i==6?60:20);
                if(i==8)d.setMana(110);
                if(i==21)f.target.setPos(f.origin.add(1,0,16.45));
                if(i==17||i==18||i==19)aimGround(f,5);
                if(i==20||i==35)wall(f,4,4);
                if(i==30)ActionTime.apply(f.p.getUUID(),f.p.getUUID(),"slow-windup",.5,f.p.level().getGameTime()+50);
                if(i==36)d.setSkillLevel(node(2).getId(),0);
                switch(i){
                    case 0,1,2,3,20,21,28,30,32,33,36,37,44,45,46,48 -> SkillSystem.cast(f.p,0);
                    case 4,6,7,8,9,10,11,12,23,26,27,38,39,40,41,42 -> SkillSystem.cast(f.p,2);
                    case 5 -> {d.setSkillLevel(node(4).getId(),0);d.setSkillLevel(node(5).getId(),1);SkillSystem.cast(f.p,2);}
                    case 13,14,15,16,35,47 -> {d.setSkillLevel(node(6).getId(),0);d.setSkillLevel(node(7).getId(),1);SkillSystem.cast(f.p,3);}
                    case 17,18,19 -> {SkillSystem.cast(f.p,3);AbilityRuntime.confirm(f.p);}
                    case 22,31 -> YikeBasicAttack.tryFire(f.p);
                    case 34,43,49 -> {aimGround(f,5);SkillSystem.cast(f.p,1);AbilityRuntime.confirm(f.p);}
                    default -> { }
                }
                if(i==0||i==1||i==2||i==3||i==30)h.assertTrue(d.getMana()==700&&d.getCooldown(node(2).getId())==0,"Q starts unpaid: "+i);
                if(i==13||i==17||i==18)h.assertTrue(d.getMana()==(i==13?530:540),"Ultimate pays at charge start: "+i);
            }
        }catch(Throwable fail){fixtures.forEach(YikeChecks::cleanup);restore(enabled,training,nc,nm);throw fail;}
        for(int n=1;n<=150;n++){final int tick=n;h.runAfterDelay(n,()->{
            if(done[0])return;TrainingMode.setEnabled(true);ModState.setEnabled(true);TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
            try{
                for(var f:fixtures){var p=f.p;var d=PlayerDataProvider.get(p);int i=f.index;
                    if(i==37&&tick<=8)SkillSystem.cast(p,0);
                    if(i==50&&tick<=26)p.setPos(f.origin.add((tick%2)*.08,0,0));
                    if(i==50&&tick==27)SkillSystem.cast(p,2);
                    if(tick==2)switch(i){
                        case 1->d.setMana(0);case 2,39->d.setBuff("g1_stun",20);case 45->p.setHealth(0);case 46->d.setCharacterId("hanze");case 49->SilenceSystem.apply(p,50);
                        case 3->{aimGround(f,5);SkillSystem.cast(p,1);AbilityRuntime.confirm(p);h.assertTrue(d.getMana()==645&&d.getCooldown(node(2).getId())==0,"W cancels unpaid Q and commits only W");}
                        default->{ }
                    }
                    if(tick==3)switch(i){case 32->AbilityRuntime.clear(p);case 33->{p.setServerLevel(h.getLevel().getServer().getLevel(Level.NETHER));AbilityRuntime.cleanup();}default->{ }}
                    if(tick==5&&i==43)AbilityRuntime.clear(p);
                    if(tick==6&&i==44)AbilityRuntime.clear(p);
                    if(tick==6&&i==48){p.setServerLevel(h.getLevel().getServer().getLevel(Level.NETHER));AbilityRuntime.cleanup();}
                    if(tick==5)switch(i){case 18->d.setBuff("g1_stun",50);case 34->d.setBuff("g1_stun",50);default->{ }}
                    if(tick==5&&i==22)MagicBullet.tryFire(p,p.getMainHandItem());
                    if(tick==6&&i==0)ActionTime.apply(p.getUUID(),p.getUUID(),"actor-only",.1,p.level().getGameTime()+50);
                    if(tick==8&&i==19)wall(f,3,5);
                    if(tick==10&&Set.of(4,8,10,12,26,27,38).contains(i))SkillSystem.cast(p,0);
                    if(i==4&&Set.of(32,54,76,98).contains(tick))SkillSystem.cast(p,0);
                    if(i==26&&Set.of(32,54,76,98).contains(tick))SkillSystem.cast(p,0);
                    if(i==27&&Set.of(32,54).contains(tick))SkillSystem.cast(p,0);
                    if(i==5&&Set.of(8,35,62,89).contains(tick))SkillSystem.cast(p,0);
                    if(tick==10&&(i==6||i==7))SkillSystem.cast(p,0);
                    if(tick==12&&i==12){aimGround(f,5);SkillSystem.cast(p,1);AbilityRuntime.confirm(p);h.assertTrue(!AbilityRuntime.preparingSkill(p).equals(node(2).getId()),"Stance W removes the pending shell");}
                    if(tick==10&&i==42){f.aim=YikeStance.aim(p);p.setYRot(180);}
                    if(tick==13&&i==38)d.setMana(0);
                    if(tick==13&&i==40)KnockbackBuffer.apply(p,new Vec3(.4,.2,0));
                    if(tick==13&&i==41)SilenceSystem.apply(p,30);
                    if(tick==14&&i==42)SkillSystem.cast(p,0);
                    if(tick==13&&i==9)d.setBuff("g1_stun",20);
                    if(tick==13&&i==10)d.setBuff("g1_root",80);
                    if(tick==14&&i==6)SkillSystem.cast(p,2);
                    if(tick==21&&i==7)SkillSystem.cast(p,0);
                    if(tick==30&&i==7)SkillSystem.cast(p,2);
                    if(tick==25&&i==51)SkillSystem.cast(p,0);
                    if(tick==35&&i==51)AbilityRuntime.clear(p);
                    if(tick==36&&i==51)h.assertTrue(ModShieldSystem.value(p)==0,"Full cleanup removes the actual Yike shield source");
                    if(tick==25&&(i==24||i==25))SkillSystem.cast(p,i==24?2:0);
                    if(tick==27&&i==25)AbilityRuntime.cancel(p);
                    if(tick==30&&i==25){aimGround(f,5);SkillSystem.cast(p,1);AbilityRuntime.confirm(p);}
                    if(tick==35&&i==47){ModState.setEnabled(false);AbilityRuntime.tick(p);ModState.setEnabled(true);}
                    if(tick==35)switch(i){case 14->SkillSystem.cast(p,3);case 15->d.setBuff("g1_stun",50);case 16->d.setBuff("g1_root",70);default->{ }}
                    if(tick==45&&i==24){SkillSystem.cast(p,2);h.assertTrue(SteadyBarrier.cooldown(p)>200,"Manual stance exit cannot reset barrier CD");}
                    if(tick==107&&i==26){aimGround(f,5);SkillSystem.cast(p,3);AbilityRuntime.confirm(p);}
                    if(i==31&&tick==1)ActionTime.apply(p.getUUID(),p.getUUID(),"attack-recovery",.5,p.level().getGameTime()+100);
                    if(i==31&&(tick==20||tick==59))YikeBasicAttack.tryFire(p);
                    if(tick==14&&i==23)YikeBasicAttack.tryFire(p);
                    if(i==29&&tick==1){d.setSkillLevel(node(4).getId(),0);d.setSkillLevel(node(6).getId(),0);d.setSkillLevel(node(7).getId(),1);SkillSystem.cast(p,3);}
                    if(i==29&&tick==5)SkillSystem.cast(p,3);
                    // Observe every tick once; duplicate runtime calls must never advance local work twice.
                    AbilityRuntime.tick(p);if(i==30)AbilityRuntime.tick(p);
                    if(i==42&&tick>=10&&tick<=45){
                        var aim=YikeStance.aim(p);h.assertTrue(Math.acos(Math.clamp(f.aim.dot(aim),-1,1))<=Math.toRadians(4.5001),"Independent artillery direction moves at most 90 degrees per second");f.aim=aim;
                    }
                    if(i==4||i==5){
                        int spent=Math.round(700-d.getMana()-(i==4?70:65));int committed=spent/35;
                        if(committed>f.shots){h.assertTrue(committed==f.shots+1,"One input commits one shell");if(f.shots>0)h.assertTrue(tick-f.lastShot==(i==4?22:27),"Exact real muzzle interval");f.lastShot=tick;f.shots=committed;}
                    }
                    if(tick==4&&i==30)h.assertTrue(d.getMana()==700,"Slowed Q cannot commit early");
                    if(tick==5&&i==0)h.assertTrue(d.getMana()==665&&d.getCooldown(node(2).getId())==120,"Normal Q commits mana/CD once at its fifth tick");
                    if(tick==9&&i==4)h.assertTrue(d.getMana()==630&&YikeStance.shots(p)==5&&YikeStance.remaining(p)==120,"Deployment commits after nine local ticks and starts six seconds then");
                    if(tick==7&&i==5)h.assertTrue(d.getMana()==635&&YikeStance.shots(p)==4,"Mobile deployment owns its own seven-tick payment");
                    if(tick==10&&i==30)h.assertTrue(d.getMana()==665,"Half-time Q ends at ten real ticks");
                    if(tick==14&&i==3)h.assertTrue(loss(f)>0&&d.getCooldown(node(2).getId())==0,"Placed W survives later control without a Q expense loss="+loss(f)+" mana="+d.getMana()+" qcd="+d.getCooldown(node(2).getId())+" alive="+f.target.isAlive()+" removed="+f.target.isRemoved()+" bbox="+f.target.getBoundingBox()+" target="+f.target.position()+" point="+AbilityRuntime.context(p,node(3)).point(14));
                    if(tick==15&&i==6)h.assertTrue(!YikeStance.active(p)&&d.getCooldown(node(2).getId())==46,"No-shot exit keeps only original Q remainder");
                    if(tick==16&&i==8)h.assertTrue(!YikeStance.active(p)&&d.getMana()==5&&YikeStance.exitReason(p).equals("mana"),"Low mana exits after exactly one paid shell");
                    if(tick==14&&i==9)h.assertTrue(!YikeStance.active(p),"Stun exits stance without firing");
                    if(tick==20&&i==10)h.assertTrue(YikeStance.active(p)&&loss(f)>0,"Root permits stationary artillery Q");
                    if(tick==31&&i==7)h.assertTrue(d.getCooldown(node(2).getId())==119,"Fired exit supplies a complete current-rank Q cooldown");
                    if(tick==15&&(i==40||i==41))h.assertTrue(!YikeStance.active(p),"Silence/forced movement exits artillery: "+i);
                    if(tick==15&&i==39)h.assertTrue(d.getMana()==700&&d.getCooldown(node(4).getId())==0,"Interrupted deployment never pays the major cost");
                    if(tick==37&&i==50)h.assertTrue(ModShieldSystem.value(p)==0,"Back-and-forth horizontal path cannot fake standing steady");
                    if(tick==34&&i==24)h.assertTrue(Math.abs(ModShieldSystem.value(p)-72.5)<.1,"Standing steady uses actual successful deployment, not request");
                    if(tick==28&&i==25)h.assertTrue(ModShieldSystem.value(p)==0,"Cancelled unpaid action never grants steady shield");
                    if(tick==31&&i==25)h.assertTrue(ModShieldSystem.value(p)>0,"Next successful W consumes the ready barrier");
                    if(tick==50&&(i==14||i==15))h.assertTrue(!PrismaticBeam.active(p)&&Math.abs(loss(f)-155)<.1,"Cancel/control stops the beam after two real jumps");
                    if(tick==74&&(i==13||i==16))h.assertTrue(!PrismaticBeam.active(p)&&Math.abs(loss(f)-465)<.1,"Exactly six real jumps, including rooted channel");
                    if(tick==110&&i==4)h.assertTrue(f.shots==5&&!YikeStance.active(p)&&d.getMana()==455,"Fixed stance delivers five manually requested paid shells");
                    if(tick==100&&i==5)h.assertTrue(f.shots==4&&!YikeStance.active(p)&&d.getMana()==495,"Mobile stance delivers four distinct paid shells");
                    if(tick==130&&i==11)h.assertTrue(!YikeStance.active(p)&&YikeStance.exitReason(p).equals("expired"),"Stance deadline remains 120 real ticks");
                    d.tickCooldowns();ModShieldSystem.tick(p);
                }
                if(tick==150){
                    for(int i:new int[]{1,2,18,19,20,28,29,32,33,35,36,38,39,42,43,44,45,46,48})h.assertTrue(Math.abs(loss(fixtures.get(i)))<.1,"Invalid/unpaid/collision/lifecycle case has no deferred damage: "+i);
                    h.assertTrue(Math.abs(loss(fixtures.get(0))-111.5)<.1,"Normal Q actual chain has no hidden multiplier and actor-only slow does not slow emitted flight");
                    h.assertTrue(Math.abs(loss(fixtures.get(17))-284.5)<.1,"Real heavy arc lands once after charge and flight");
                    h.assertTrue(Math.abs(loss(fixtures.get(21))-111.5)<.1,"Range-end explosion reaches only its actual 1.5 sphere");
                    h.assertTrue(Math.abs(loss(fixtures.get(22))-65)<.1,"Left attack is exactly A physical, free, and right-click emits nothing");
                    h.assertTrue(Math.abs(loss(fixtures.get(23)))<.1,"Artillery cannot add basic attacks");
                    h.assertTrue(Math.abs(loss(fixtures.get(31))-130)<.1,"Local basic recovery preserves work and rejects early repeat input");
                    h.assertTrue(Math.abs(loss(fixtures.get(26))-2070.5/1.5)<.2&&PlayerDataProvider.get(fixtures.get(26).p).getMana()==295,"Actual S230 five-shell + heavy chain: two passives, MR50, 405 total mana");
                    h.assertTrue(RainbowCalibration.count(fixtures.get(27).p,fixtures.get(27).target.getUUID())==0&&ModShieldSystem.value(fixtures.get(27).target)<10000-200,"Fully absorbed paid hits still trigger three-mark proc");
                    h.assertTrue(Math.abs(loss(fixtures.get(37))-111.5)<.1,"Repeated input packets never create extra or queued Q shells");
                    h.assertTrue(Math.abs(loss(fixtures.get(47))-155)<.1&&!PrismaticBeam.active(fixtures.get(47).p),"Turning off gameplay stops future beam jumps");
                    h.assertTrue(loss(fixtures.get(49))>0,"Already placed warning survives later silence");
                    h.assertTrue(loss(fixtures.get(34))>0,"Committed ground warning survives stun");
                    System.out.println("[MYRIAD-YIKE] PASS: 52 real-input cases, unpaid commit/recheck, W cancellation, fixed five/mobile four, real muzzle deadlines, exit CD, root/stun, six-jump cancel, heavy collision, physical basic, rolling barrier, shields/marks, S230 actual chain and cleanup");
                    done[0]=true;fixtures.forEach(YikeChecks::cleanup);restore(enabled,training,nc,nm);h.succeed();
                }
            }catch(Throwable fail){done[0]=true;fixtures.forEach(YikeChecks::cleanup);restore(enabled,training,nc,nm);throw fail;}
        });}
    }
    private static AbilitySkill node(int i){return FirstGroupSkills.node("yike",i);}
    private static Fixture fixture(GameTestHelper h,int i){
        var f=new Fixture();f.index=i;f.level=h.getLevel();BlockPos at=h.absolutePos(new BlockPos(-10,12+i*6,4));
        for(int x=-5;x<=5;x++)for(int z=-2;z<=29;z++)for(int y=0;y<=4;y++){
            BlockPos b=at.offset(x,y,z);f.blocks.put(b,f.level.getBlockState(b));f.level.setBlockAndUpdate(b,(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState());
        }
        f.p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(connection);
        f.p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(f.level.getServer(),connection,f.p,net.minecraft.server.network.CommonListenerCookie.createInitial(f.p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };f.p.connection.markClientLoaded();f.level.getServer().getPlayerList().getPlayersByUUID().put(f.p.getUUID(),f.p);
        f.origin=Vec3.atBottomCenterOf(at.above());f.p.setPos(f.origin);f.p.setYRot(0);f.p.setXRot(0);f.p.setNoGravity(true);
        var d=PlayerDataProvider.get(f.p);d.setCharacterId("yike");d.setLevel(20);
        CharacterType.applyInitialAttributes(f.p,CharacterType.YIKE);FirstGroupGrowth.initialize(f.p,d);PlayerHealthSystem.fill(f.p);
        d.setSkillLevel(node(0).getId(),1);d.setSkillLevel(node(2).getId(),1);d.setSkillLevel(node(3).getId(),1);d.setSkillLevel(node(4).getId(),1);d.setSkillLevel(node(6).getId(),1);d.setMana(700);
        f.p.getInventory().setItem(0,ModItems.HONG_LIE.get().getDefaultInstance());
        JiarenManager.create(f.p);f.target=f.level.getEntitiesOfClass(Villager.class,new net.minecraft.world.phys.AABB(f.origin,f.origin.add(0,2,4)).inflate(1),v->JiarenManager.isJiaren(v.getUUID())).getFirst();
        f.target.setPos(f.origin.add(0,0,5));f.target.setNoGravity(true);f.target.setPersistenceRequired();return f;
    }
    private static void aimGround(Fixture f,double distance){f.p.setYRot(0);f.p.setXRot((float)Math.toDegrees(Math.atan(f.p.getEyeHeight()/distance)));}
    private static void wall(Fixture f,int z,int height){for(int x=-3;x<=3;x++)for(int y=1;y<=height;y++){BlockPos b=BlockPos.containing(f.origin).offset(x,y-1,z);f.blocks.putIfAbsent(b,f.level.getBlockState(b));f.level.setBlockAndUpdate(b,Blocks.STONE.defaultBlockState());}}
    private static float loss(Fixture f){return JiarenManager.MAX_HP-JiarenManager.hp(f.target.getUUID());}
    private static void restore(boolean e,boolean t,boolean nc,boolean nm){ModState.setEnabled(e);TrainingMode.setEnabled(t);TrainingMode.setNoCooldown(nc);TrainingMode.setNoMana(nm);}
    private static void cleanup(Fixture f){AbilityRuntime.clear(f.p);ModShieldSystem.clearFor(f.p);ModShieldSystem.clearFor(f.target.getUUID());SilenceSystem.clearFor(f.p.getUUID());KnockbackBuffer.clear(f.p.getUUID());f.level.getServer().getPlayerList().getPlayersByUUID().remove(f.p.getUUID());f.target.discard();f.blocks.forEach(f.level::setBlockAndUpdate);f.p.discard();}
}
