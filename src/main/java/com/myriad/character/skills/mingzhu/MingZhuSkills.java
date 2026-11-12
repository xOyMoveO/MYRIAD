package com.myriad.character.skills.mingzhu;

import java.util.*;
import com.myriad.character.*;
import com.myriad.combat.*;
import com.myriad.game.*;
import com.myriad.player.*;
import com.myriad.registry.*;
import com.myriad.network.NetworkSetup.DashPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned light projectiles, mirror geometry and fixed-point solar resource. */
public final class MingZhuSkills extends Skill {
    public enum Type { PASSIVE, Q, W, E, R }
    public static final int PLACEMENT = 31;
    public static final String[] IDS = {"liuguangguanzhu", "juyaozhejing", "yaobuhuihuan", "yixiantianming"};
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Vec3> INPUT = new HashMap<>();
    private static final Map<UUID,long[]> VISUAL_BUDGET=new HashMap<>();
    private static final Identifier CHANNEL_SPEED = Identifier.fromNamespaceAndPath("myriad", "mingzhu_channel");
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFC45B, .65F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFF4CD, .45F);
    private static final DustParticleOptions RED = new DustParticleOptions(0xE26455, .65F);
    private final Type type;

    public MingZhuSkills(Type type) {
        super(type == Type.PASSIVE ? "mingzhu_passive" : IDS[type.ordinal()-1],
                switch(type){case Q->140;case W->280;case E->240;case R->360;default->0;},
                switch(type){case Q->72.0F;case W->90.0F;case E->108.0F;case R->144.0F;default->0.0F;});
        this.type=type;
    }
    @Override public boolean isPassive(){return type==Type.PASSIVE;}
    @Override public boolean isUltimate(){return type==Type.R;}
    @Override public int getCooldownTicks(int lv){return switch(type){case Q->140-10*(Math.max(1,lv)-1);case W->280-20*(Math.max(1,lv)-1);case E->240-20*(Math.max(1,lv)-1);case R->360-40*(Math.max(1,lv)-1);default->0;};}
    @Override public String levelEffects(int lv){int n=Math.max(1,lv)-1;return switch(type){
        case PASSIVE->"光球命中英雄获得8曜能，贯珠每次最多16；非英雄分别2/4。100上限，引导期间不能积能。";
        case Q->"15格贯穿光球，"+(24+10*n)+"+50%法强魔法伤害；对兵线60%。";
        case W->"8格选点折镜持续4秒；Q增伤"+(30+10*n)+"%、加速并重置15格射程；E穿镜延长4格并加10%法强护盾；R三束散射增伤10%。二段转向。";
        case E->"沿移动输入突进5格，无输入后退；护盾"+(20+10*n)+"+50%法强，持续2秒。穿折镜再延长4格，额外获得10%法强护盾。";
        case R->"主动开启，至少30曜能；每0.25秒造成"+(6+3*n)+"+4%法强，耗6.25能，实际命中英雄仅耗3.125。成功结算每目标+2光耀，满10下一跳为30格、半径1.2格、双倍伤害巨量激光。";};}
    @Override public void cast(ServerPlayer p, PlayerData d){handleCast(p,d,type.ordinal()-1,this,false,false);}
    private static boolean role(PlayerData d){return d!=null&&"mingzhu".equals(d.getCharacterId());}
    private static State state(ServerPlayer p){return STATES.computeIfAbsent(p.getUUID(),id->new State(p));}
    public static boolean channeling(ServerPlayer p){State s=STATES.get(p.getUUID());return s!=null&&s.channel;}
    public static void setRequestedDirection(ServerPlayer p,float x,float z){if(Float.isFinite(x)&&Float.isFinite(z)&&x*x+z*z>1e-6)INPUT.put(p.getUUID(),new Vec3(x,0,z).normalize());}
    public static void clearRequestedDirection(ServerPlayer p){INPUT.remove(p.getUUID());}

    public static void handleCast(ServerPlayer p,PlayerData d,int slot,Skill skill,boolean noCd,boolean noMana){
        if(!role(d)||slot<0||slot>3)return;
        State s=state(p);long now=p.level().getGameTime();
        if(s.channel){if(slot==3)endChannel(p,d,s);return;}
        if(controlled(p)||s.dashTicks!=0||s.qAt>now)return;
        if(slot==1&&s.mirror!=null&&s.mirrorUntil>now&&!s.turned){
            s.mirrorDir=flat(p.getLookAngle());s.turned=true;d.setBuff(IDS[1]+"_recast",0);
            sound(p,s.mirror,"mirror_turn",.45F);sync(p,d,s);return;
        }
        if(slot==1&&d.getPlacementType()==PLACEMENT){cancelPlacement(p,d);return;}
        if(!noCd&&d.getCooldown(skill.getId())>0){message(p,"message.myriad.skill_cd",(d.getCooldown(skill.getId())+19)/20);return;}
        if(!noMana&&d.getMana()<skill.getManaCost()){message(p,"message.myriad.not_enough_mana");return;}
        if(slot==3&&d.getMingzhuEnergy()<120){message(p,"message.myriad.mingzhu_need_energy");return;}
        if(slot==1){s.placeUntil=now+200;s.placeNoCd=noCd;s.placeNoMana=noMana;d.setPlacementType(PLACEMENT);sync(p,d,s);return;}
        if(d.getPlacementType()==PLACEMENT)cancelPlacement(p,d);
        if(!noMana)d.spendMana(skill.getManaCost());
        if(slot!=3&&!noCd)d.setCooldown(skill.getId(),cooldown(p,skill,d));
        int lv=Math.max(1,d.skillLevel(skill.getId()));
        if(slot==0){s.qAt=now+4;s.qLevel=lv;s.qDirection=p.getLookAngle().normalize();sound(p,p.getEyePosition(),"orb_cast",.4F);}
        if(slot==2){
            s.dashDirection=INPUT.getOrDefault(p.getUUID(),flat(p.getLookAngle()).scale(-1));
            s.dashTicks=10;s.dashLimit=5;s.dashMirrored=false;s.dashDistance=0;s.dashPrevious=p.position();
            startDashMotion(p,s);
            ModShieldSystem.grant(p,IDS[2],20+10*(lv-1)+sp(p)*.5F,40);d.setBuff(IDS[2],40);
            sound(p,p.position(),"dash",.5F);
            seal(p,p.position().add(0,.08,0),new Vec3(0,1,0),.9,now*.1);
        }
        if(slot==3){
            s.channel=true;s.radiance=0;s.lastHeroHit=false;s.giantUntil=0;s.beamAt=now+8;s.nextBeam=now+13;s.beamLevel=lv;s.beamTicks=0;s.beamDirection=p.getLookAngle().normalize();
            s.rCooldown=noCd?0:cooldown(p,skill,d);
            var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
            speed.removeModifier(CHANNEL_SPEED);speed.addTransientModifier(new AttributeModifier(CHANNEL_SPEED,-.55,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            sound(p,p.getEyePosition(),"beam_start",.55F);
        }
        sync(p,d,s);
    }
    public static void confirmPlacement(ServerPlayer p,PlayerData d){
        State s=STATES.get(p.getUUID());if(s==null||!role(d)||d.getPlacementType()!=PLACEMENT)return;
        if(controlled(p)||p.level().getGameTime()>s.placeUntil){cancelPlacement(p,d);return;}
        Vec3 point=ground(p);if(point==null){message(p,"message.myriad.mingzhu_bad_ground");return;}
        Skill w=CharacterType.MINGZHU.skill(1);
        if(!s.placeNoMana&&d.getMana()<25){message(p,"message.myriad.not_enough_mana");return;}
        if(!s.placeNoCd&&d.getCooldown(IDS[1])>0){cancelPlacement(p,d);return;}
        if(!s.placeNoMana)d.spendMana(25);
        if(!s.placeNoCd)d.setCooldown(IDS[1],cooldown(p,w,d));
        s.mirror=point;s.mirrorDir=flat(p.getLookAngle());s.mirrorUntil=p.level().getGameTime()+80;s.turned=false;s.wLevel=Math.max(1,d.skillLevel(IDS[1]));
        d.setPlacementType(0);s.placeUntil=0;sound(p,point,"mirror_place",.5F);sync(p,d,s);
    }
    public static void cancelPlacement(ServerPlayer p,PlayerData d){if(d.getPlacementType()==PLACEMENT)d.setPlacementType(0);State s=STATES.get(p.getUUID());if(s!=null)s.placeUntil=0;PlayerDataProvider.syncToClient(p);}
    public static void fireBasic(ServerPlayer p){
        PlayerData d=PlayerDataProvider.get(p);
        if(!role(d)||!ModState.isEnabled()||!p.isAlive()||p.isSpectator()||controlled(p))return;
        GameManager gm=GameManager.get();if(!TrainingMode.isEnabled()&&(gm==null||gm.getPhase()!=GameManager.Phase.PLAYING))return;
        State s=state(p);long now=p.level().getGameTime();
        if(s.channel||s.qAt>now||d.getPlacementType()!=0)return;
        if(s.basicAt>now&&!com.myriad.combat.BasicAttackSystem.isEarlyReady(now,s.basicAt,s.basicTotal))return;
        float attackMultiplier=com.myriad.combat.BasicAttackSystem.recoveryDamageMultiplier(now,s.basicAt);
        int cd=(int)Math.ceil(20/Math.max(.1,p.getAttributeValue(ModAttributes.ATTACK_SPEED)));s.basicAt=now+cd;
        s.basicTotal=cd;
        s.balls.add(new Ball(p.getEyePosition(),p.getLookAngle().normalize(),false,sp(p)*.5F*attackMultiplier));
        PacketDistributor.sendToPlayer(p,new com.myriad.network.NetworkSetup.MagicBulletCooldownPayload(cd,cd));
        sound(p,p.getEyePosition(),"orb_cast",.28F);
    }
    public static void tick(ServerPlayer p,PlayerData d){
        if(!role(d))return;
        if(!p.isAlive()||p.isSpectator()){clearFor(p,true);return;}
        State s=state(p);if(s.world!=p.level()){clearFor(p,false);s=state(p);}
        long now=p.level().getGameTime();
        if(s.placeUntil>0){if(now>=s.placeUntil||controlled(p))cancelPlacement(p,d);else if(now%2==0)preview(p);}
        if(s.mirror!=null&&now>=s.mirrorUntil){burst(p,s.mirror.add(0,1,0),.5);s.mirror=null;d.setBuff(IDS[1]+"_recast",0);}
        if(s.qAt>0){if(controlled(p))s.qAt=0;else if(now>=s.qAt){s.balls.add(new Ball(p.getEyePosition(),s.qDirection,true,96+40*(s.qLevel-1)+sp(p)*2.0F));s.qAt=0;}
            else seal(p,p.getEyePosition().add(s.qDirection.scale(.75)),s.qDirection,.3+(s.qAt-now)*.08,now*.3);}
        for(Iterator<Ball> it=s.balls.iterator();it.hasNext();)if(tickBall(p,d,s,it.next()))it.remove();
        tickDash(p,s);
        if(s.channel){
            if(controlled(p)){endChannel(p,d,s);}else{
                s.beamDirection=turnToward(s.beamDirection,p.getLookAngle().normalize(),Math.PI/60);
                if(p.getDeltaMovement().y>0){p.setDeltaMovement(p.getDeltaMovement().multiply(1,0,1));p.hurtMarked=true;
                    PacketDistributor.sendToPlayer(p,new DashPayload((float)p.getDeltaMovement().x,0,(float)p.getDeltaMovement().z,1));}
                boolean firing=now>=s.beamAt;
                boolean giant=s.radiance>=10||now<s.giantUntil;
                if(now%2==0)for(BeamSegment beam:beamSegments(p,s,giant))beamVisual(p,beam.start,beam.end,firing,now,giant);
                if(firing&&now>=s.nextBeam){
                    pulse(p,d,s,now);
                }
            }
        }
        // Stored energy persists out of combat; only an explicitly started beam spends it.
        if(s.mirror!=null)s.mirrorVisualDir=s.mirrorVisualDir==null?s.mirrorDir:turnToward(s.mirrorVisualDir,s.mirrorDir,Math.PI/8);
        if(now%4==0){if(s.mirror!=null)mirrorVisual(p,s,now);coreVisual(p,d,now);
            if(d.getBuff(IDS[2])>0){ring(p,p.position().add(0,.6,0),.5,GOLD,8,false);ring(p,p.position().add(0,1.25,0),.45,WHITE,8,false);}}
        sync(p,d,s);
    }
    /** Swept plane crossing shared by E and R, never proximity-triggered. */
    private static Vec3 mirrorIntersection(State s,Vec3 a,Vec3 b,double padding){
        if(s.mirror==null||s.world.getGameTime()>=s.mirrorUntil)return null;
        Vec3 delta=b.subtract(a),center=s.mirror.add(0,1.2,0);
        double denom=delta.dot(s.mirrorDir);if(Math.abs(denom)<1e-7)return null;
        double t=center.subtract(a).dot(s.mirrorDir)/denom;if(t<0||t>1)return null;
        Vec3 hit=a.add(delta.scale(t)),side=new Vec3(-s.mirrorDir.z,0,s.mirrorDir.x);
        return Math.abs(hit.subtract(center).dot(side))<=.7+padding&&Math.abs(hit.y-center.y)<=1.2+padding?hit:null;
    }
    private record BeamSegment(Vec3 start,Vec3 end,float multiplier) { }
    private static List<BeamSegment> beamSegments(ServerPlayer p,State s,boolean giant){
        Vec3 a=p.getEyePosition();double range=giant?30:18;
        Vec3 end=clip(p,a,a.add(s.beamDirection.scale(range))),mirror=mirrorIntersection(s,a,end,0);
        if(mirror==null)return List.of(new BeamSegment(a,end,1));
        List<BeamSegment> result=new ArrayList<>();result.add(new BeamSegment(a,mirror,1));
        double remaining=Math.max(0,range-a.distanceTo(mirror));
        for(int side=-1;side<=1;side++){
            Vec3 direction=s.beamDirection.yRot((float)Math.toRadians(side*15));
            Vec3 from=mirror.add(direction.scale(.01));
            result.add(new BeamSegment(from,clip(p,from,from.add(direction.scale(remaining))),1.1F));
        }
        return result;
    }
    private static void pulse(ServerPlayer p,PlayerData d,State s,long now){
        int energy=d.getMingzhuEnergyEighths();
        if(energy<25||s.beamTicks>=32){endChannel(p,d,s);return;}
        boolean giant=s.radiance>=10;
        // Merge paths first: an overlapping scatter fan must never triple-hit a target.
        Map<LivingEntity,Float> hits=new LinkedHashMap<>();
        for(BeamSegment beam:beamSegments(p,s,giant)){
            int index=0;
            for(LivingEntity target:targets(p,beam.start,beam.end,giant?1.45:.7,false))
                hits.merge(target,beam.multiplier*(index++==0?1:.7F),Math::max);
        }
        boolean heroHit=false;int successes=0;
        // At the last half-cost pulse, only a confirmed hero hit unlocks non-hero damage.
        List<Map.Entry<LivingEntity,Float>> ordered=new ArrayList<>(hits.entrySet());
        if(energy<50)ordered.sort(Comparator.comparingInt(e->e.getKey() instanceof ServerPlayer?0:1));
        for(var hit:ordered){
            LivingEntity target=hit.getKey();
            if(energy<50&&!(target instanceof ServerPlayer)&&!heroHit)continue;
            float base=(24+12*(s.beamLevel-1)+sp(p)*.16F)*hit.getValue()*(giant?2:1);
            if(LaneSystem.factionOf(target)!=null&&!(target instanceof ServerPlayer))base*=.5F;
            float hp=health(target),shield=ModShieldSystem.value(target);
            DamageCalc.dealUncriticalSkillDamage(p,target,base,DamageCalc.Kind.MAGIC,3);
            if(health(target)<hp||ModShieldSystem.value(target)<shield){
                successes++;heroHit|=target instanceof ServerPlayer;
                burst(p,target.getBoundingBox().getCenter(),giant?.5:.16);
            }
        }
        if(energy<50&&!heroHit){endChannel(p,d,s);return;}
        d.setMingzhuEnergyEighths(energy-(heroHit?25:50));s.lastHeroHit=heroHit;
        if(giant){
            s.radiance-=10;s.giantUntil=now+5;
            for(BeamSegment beam:beamSegments(p,s,true))beamVisual(p,beam.start,beam.end,true,now,true);
            sound(p,p.getEyePosition(),"beam_start",.65F);
        }
        s.radiance=Math.min(10,s.radiance+successes*2);s.beamTicks++;s.nextBeam=now+5;
        if(s.beamTicks%4==1)sound(p,p.getEyePosition(),"beam_body",.32F);
        if(d.getMingzhuEnergyEighths()<25||s.beamTicks>=32)endChannel(p,d,s);
    }
    private static boolean tickBall(ServerPlayer p,PlayerData d,State s,Ball b){
        double travel=Math.min(b.q?(b.reflected?1.8:1.2):1.5,15-b.travelled);if(travel<=.001)return true;
        Vec3 a=b.pos,wanted=a.add(b.dir.scale(travel)),end=clip(p,a,wanted);
        boolean blocked=end.distanceToSqr(wanted)>.00001;
        // Mirror is a visible 1.4 x 2.4 plane, tested before the remaining segment.
        Vec3 mirrorHit=null;
        if(b.q&&!b.reflected&&s.mirror!=null){Vec3 center=s.mirror.add(0,1.2,0);double denom=b.dir.dot(s.mirrorDir);
            if(Math.abs(denom)>1e-6){double t=center.subtract(a).dot(s.mirrorDir)/denom;Vec3 at=a.add(b.dir.scale(t));Vec3 side=new Vec3(-s.mirrorDir.z,0,s.mirrorDir.x);
                if(t>=0&&t<=a.distanceTo(end)&&Math.abs(at.subtract(center).dot(side))<=.85&&Math.abs(at.y-center.y)<=1.3)mirrorHit=at;}}
        Vec3 stop=mirrorHit==null?end:mirrorHit;
        for(LivingEntity target:targets(p,a,stop,b.q?(b.reflected?1.0:.8):.55,!b.q)){
            if(!b.hit.add(target.getUUID()))continue;
            float health=health(target),shield=target instanceof ServerPlayer sp?ModShieldSystem.value(sp):0;
            float damage=b.damage;
            if(b.q&&LaneSystem.factionOf(target)!=null&&!(target instanceof ServerPlayer))damage*=.6F;
            if(b.q)DamageCalc.dealSkillDamage(p,target,damage,DamageCalc.Kind.MAGIC,0);else DamageCalc.dealBasicDamage(p,target,damage,DamageCalc.Kind.MAGIC);
            boolean hurt=health(target)<health||(target instanceof ServerPlayer sp&&ModShieldSystem.value(sp)<shield);
            if(hurt&&!(target instanceof DefenseTowerEntity)){
                if(target instanceof ServerPlayer)d.setMingzhuHeroCombatAt(p.level().getGameTime());
                if(!s.channel){int grant=target instanceof ServerPlayer?(b.q?64:32):(b.q?16:8);int earned=Math.max(0,grant-b.reward);b.reward=Math.max(grant,b.reward);d.setMingzhuEnergy(d.getMingzhuEnergy()+earned);}
                burst(p,target.getBoundingBox().getCenter(),.25);sound(p,target.position(),"orb_hit",.22F);
            }
            if(!b.q)return true;
        }
        b.travelled+=a.distanceTo(stop);b.pos=stop;
        ballVisual(p,b);
        if(mirrorHit!=null){b.reflected=true;b.damage*=1.30F+.10F*(s.wLevel-1);b.dir=s.mirrorDir;b.pos=b.pos.add(b.dir.scale(.02));b.travelled=0;burst(p,stop,.65);sound(p,stop,"mirror_turn",.3F);return false;}
        if(blocked){burst(p,stop,.3);return true;}
        return b.travelled>=15;
    }
    private static void tickDash(ServerPlayer p,State s){
        if(s.dashTicks==0)return;
        if(!s.dashMirrored&&mirrorIntersection(s,s.dashPrevious.add(0,.9,0),p.position().add(0,.9,0),.3)!=null){
            s.dashMirrored=true;s.dashLimit+=4;s.dashTicks=Math.max(0,s.dashTicks)+8;
            startDashMotion(p,s);
            ModShieldSystem.grant(p,IDS[2]+"_mirror",sp(p)*.1F,40);
            burst(p,p.position().add(0,.9,0),.85);sound(p,p.position(),"mirror_turn",.45F);
        }
        if(s.dashTicks<0){finishDash(p,s);return;}
        Vec3 trailRight=new Vec3(-s.dashDirection.z,0,s.dashDirection.x).scale(.3);
        for(int i=0;i<3;i++){
            Vec3 at=s.dashPrevious.lerp(p.position(),i/3.0).add(0,.4+i*.2,0);
            point(p,at.add(trailRight),GOLD,false);point(p,at.subtract(trailRight),WHITE,false);
        }
        s.dashDistance+=p.position().subtract(s.dashPrevious).multiply(1,0,1).length();s.dashPrevious=p.position();
        Vec3 motion=s.dashDirection.scale(Math.min(.5,Math.max(0,s.dashLimit-s.dashDistance)));
        if(controlled(p)||s.dashDistance>=s.dashLimit||!MapSystem.contains(p.getX()+motion.x,p.getZ()+motion.z)||!p.level().noCollision(p,p.getBoundingBox().expandTowards(motion))){finishDash(p,s);return;}
        p.setDeltaMovement(motion.x,p.getDeltaMovement().y,motion.z);p.hurtMarked=true;
        ring(p,p.position().add(0,.15,0),.38,GOLD,8,false);
        if(--s.dashTicks==0)s.dashTicks=-1;
    }
    private static void startDashMotion(ServerPlayer p,State s){
        Vec3 motion=s.dashDirection.scale(.5);
        p.setDeltaMovement(motion.x,p.getDeltaMovement().y,motion.z);p.hurtMarked=true;
        PacketDistributor.sendToPlayer(p,new DashPayload((float)motion.x,(float)p.getDeltaMovement().y,(float)motion.z,s.dashTicks));
    }
    private static void finishDash(ServerPlayer p,State s){s.dashTicks=0;PacketDistributor.sendToPlayer(p,new DashPayload(0,0,0,0));p.hurtMarked=true;}
    private static void endChannel(ServerPlayer p,PlayerData d,State s){
        if(!s.channel)return;s.channel=false;s.radiance=0;s.giantUntil=0;
        p.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(CHANNEL_SPEED);
        if(s.rCooldown>0)d.setCooldown(IDS[3],s.rCooldown);
        d.setBuff("mingzhu_channel",0);d.setBuff("mingzhu_beam_time",0);d.setBuff("mingzhu_radiance",0);d.setBuff("mingzhu_giant",0);
        sound(p,p.position(),"beam_end",.4F);sync(p,d,s);
    }
    public static void clearFor(ServerPlayer p,boolean resetEnergy){
        PlayerData d=PlayerDataProvider.get(p);State s=STATES.get(p.getUUID());if(s!=null){endChannel(p,d,s);if(s.dashTicks!=0)finishDash(p,s);}
        STATES.remove(p.getUUID());INPUT.remove(p.getUUID());VISUAL_BUDGET.remove(p.getUUID());p.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(CHANNEL_SPEED);
        if(d.getPlacementType()==PLACEMENT)d.setPlacementType(0);
        for(String key:List.of("mingzhu_channel","mingzhu_beam_time","mingzhu_radiance","mingzhu_giant",IDS[1]+"_recast"))d.setBuff(key,0);
        if(resetEnergy){d.setMingzhuEnergy(0);d.setMingzhuHeroCombatAt(0);d.setBuff("mingzhu_energy",0);d.setBuff("mingzhu_energy_eighths",0);}
    }
    public static void clearAll(){STATES.clear();INPUT.clear();VISUAL_BUDGET.clear();}
    private static void sync(ServerPlayer p,PlayerData d,State s){
        long now=p.level().getGameTime();d.setBuff("mingzhu_energy",d.getMingzhuEnergy());
        d.setBuff("mingzhu_energy_eighths",d.getMingzhuEnergyEighths());d.setBuff("mingzhu_radiance",s.radiance);
        d.setBuff("mingzhu_giant",(int)Math.max(0,s.giantUntil-now));
        d.setBuff("mingzhu_channel",s.channel?2:0);d.setBuff("mingzhu_beam_time",s.channel?Math.min(32-s.beamTicks,d.getMingzhuEnergyEighths()/(s.lastHeroHit?25:50))*5:0);
        d.setBuff(IDS[1]+"_recast",s.mirror!=null&&!s.turned?(int)Math.max(0,s.mirrorUntil-now):0);
        if(p.connection!=null)PlayerDataProvider.syncToClient(p);
    }
    public static void onHeroDamage(ServerPlayer victim,LivingEntity attacker){if(attacker instanceof ServerPlayer&&role(PlayerDataProvider.get(victim)))PlayerDataProvider.get(victim).setMingzhuHeroCombatAt(victim.level().getGameTime());}
    private static boolean controlled(ServerPlayer p){return com.myriad.combat.SilenceSystem.isSilenced(p)||p.hasEffect(MobEffects.LEVITATION)||(p.hasEffect(MobEffects.SLOWNESS)&&p.getEffect(MobEffects.SLOWNESS).getAmplifier()>=200);}
    private static float sp(ServerPlayer p){return (float)p.getAttributeValue(ModAttributes.SPELL_POWER);}
    private static float health(LivingEntity t){
        if(MonsterManager.isMonster(t))return MonsterManager.modHealth(t);
        if(LaneSystem.isManaged(t))return LaneSystem.health(t);
        if(JiarenManager.isJiaren(t.getUUID()))return JiarenManager.hp(t.getUUID());
        return t.getHealth();
    }
    private static int cooldown(ServerPlayer p,Skill skill,PlayerData d){return Math.max(1,(int)Math.ceil(skill.getCooldownTicks(Math.max(1,d.skillLevel(skill.getId())))*(1-Math.clamp(p.getAttributeValue(ModAttributes.COOLDOWN_REDUCTION),0,60)/100)));}
    private static void message(ServerPlayer p,String key,Object...args){p.sendSystemMessage(Component.translatable(key,args));}
    private static Vec3 flat(Vec3 v){Vec3 h=v.multiply(1,0,1);return h.lengthSqr()<1e-6?new Vec3(0,0,1):h.normalize();}
    public static Vec3 turnToward(Vec3 from,Vec3 to,double radians) {
        return com.myriad.character.ability.AbilityGeometry.turnToward(from,to,radians);
    }
    private static Vec3 ground(ServerPlayer p){
        Vec3 a=p.getEyePosition(),b=a.add(p.getLookAngle().scale(8));BlockHitResult hit=p.level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=net.minecraft.core.Direction.UP)return null;
        Vec3 point=hit.getLocation().add(0,.025,0);if(!MapSystem.contains(point.x,point.z)||a.distanceTo(point)>8.05)return null;
        return p.level().noCollision(new AABB(point.add(-.3,.02,-.3),point.add(.3,2.4,.3)))?point:null;
    }
    public static Vec3 clip(ServerPlayer p,Vec3 a,Vec3 b) {
        return com.myriad.character.ability.AbilityGeometry.clip(p,a,b);
    }
    private static List<LivingEntity> targets(ServerPlayer p,Vec3 a,Vec3 b,double radius,boolean buildings){
        Faction own=LaneSystem.factionOf(p);
        List<LivingEntity> result=p.level().getEntitiesOfClass(LivingEntity.class,new AABB(a,b).inflate(radius),e->e!=p&&e.isAlive()&&!e.isSpectator()
                &&(buildings||!(e instanceof DefenseTowerEntity))&&!(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
                &&!JiarenManager.isFriendlyDummy(p,e)&&(own==null||LaneSystem.factionOf(e)!=own)
                &&clip(p,a,e.getBoundingBox().getCenter()).distanceToSqr(e.getBoundingBox().getCenter())<.001
                &&(e.getBoundingBox().inflate(radius).contains(a)||e.getBoundingBox().inflate(radius).clip(a,b).isPresent()));
        result.sort(Comparator.comparingDouble(e->e.getBoundingBox().inflate(radius).clip(a,b).orElse(a).distanceToSqr(a)));return result;
    }
    private static void point(ServerPlayer p,Vec3 at,DustParticleOptions color,boolean onlySelf){
        if(!MapSystem.contains(at.x,at.z))return;
        for(ServerPlayer v:p.level().players())if((!onlySelf||v==p)&&v.distanceToSqr(at)<1600&&(v==p||VisionSystem.canSeePosition(v,at))){
            long[] budget=VISUAL_BUDGET.computeIfAbsent(v.getUUID(),id->new long[2]);
            long tick=p.level().getGameTime();if(budget[0]!=tick){budget[0]=tick;budget[1]=0;}
            if(budget[1]++<320)p.level().sendParticles(v,color,false,false,at.x,at.y,at.z,1,0,0,0,0);
        }
    }
    /** Oriented iris, usable vertically at the mirror/muzzle and horizontally at the feet. */
    private static void seal(ServerPlayer p,Vec3 center,Vec3 normal,double radius,double angle){
        Vec3 right=normal.cross(new Vec3(0,1,0));if(right.lengthSqr()<.001)right=new Vec3(1,0,0);
        right=right.normalize();Vec3 up=right.cross(normal).normalize();
        for(int i=0;i<16;i++){
            double t=angle+i*Math.PI/8;
            Vec3 offset=right.scale(Math.cos(t)*radius).add(up.scale(Math.sin(t)*radius));
            point(p,center.add(offset),GOLD,false);
            if(i%2==0)point(p,center.add(offset.scale(.65)),WHITE,false);
        }
    }
    private static void ring(ServerPlayer p,Vec3 c,double radius,DustParticleOptions color,int count,boolean privateOnly){for(int i=0;i<count;i++){double a=i*Math.PI*2/count;point(p,c.add(Math.cos(a)*radius,0,Math.sin(a)*radius),color,privateOnly);}}
    private static void burst(ServerPlayer p,Vec3 c,double r){ring(p,c,r,GOLD,8,false);for(int i=0;i<4;i++)point(p,c.add(0,(i-2)*r,0),WHITE,false);}
    private static void ballVisual(ServerPlayer p,Ball b){
        Vec3 side=b.dir.cross(new Vec3(0,1,0));if(side.lengthSqr()<1e-6)side=new Vec3(1,0,0);side=side.normalize();Vec3 up=side.cross(b.dir).normalize();
        double angle=p.level().getGameTime()*.25,r=b.q?.32:.17;
        for(int layer=0;layer<(b.q?2:1);layer++)for(int i=0;i<8;i++){
            double t=i*Math.PI/4,c=Math.cos(t),sn=Math.sin(t),edge=r/Math.max(Math.abs(c),Math.abs(sn));
            double rotation=angle+layer*Math.PI/4,x=c*edge,y=sn*edge;
            point(p,b.pos.subtract(b.dir.scale(layer*.15)).add(side.scale(x*Math.cos(rotation)-y*Math.sin(rotation)))
                    .add(up.scale(x*Math.sin(rotation)+y*Math.cos(rotation))),layer==0?GOLD:WHITE,false);
        }
        point(p,b.pos,WHITE,false);point(p,b.pos.subtract(b.dir.scale(.45)),GOLD,false);
        if(b.reflected){point(p,b.pos.subtract(b.dir.scale(.85)).add(side.scale(.18)),WHITE,false);point(p,b.pos.subtract(b.dir.scale(1.2)).subtract(side.scale(.18)),GOLD,false);}
    }
    private static void preview(ServerPlayer p){Vec3 c=ground(p);boolean ok=c!=null;if(!ok)c=p.getEyePosition().add(p.getLookAngle().scale(8));ring(p,c,.6,ok?GOLD:RED,12,true);Vec3 dir=flat(p.getLookAngle());for(int i=0;i<5;i++)point(p,c.add(dir.scale(i*.25)),WHITE,true);}
    private static void mirrorVisual(ServerPlayer p,State s,long now){
        Vec3 visible=s.mirrorVisualDir==null?s.mirrorDir:s.mirrorVisualDir;
        Vec3 right=new Vec3(-visible.z,0,visible.x),base=s.mirror;
        for(int i=0;i<=6;i++){double t=i/6.0;for(int sign:new int[]{-1,1}){point(p,base.add(right.scale(sign*.7)).add(0,.08+t*2.3,0),GOLD,false);point(p,base.add(right.scale((t*2-1)*.7)).add(0,sign==1?2.38:.08,0),WHITE,false);}}
        ring(p,base,.75,GOLD,8,false);for(int i=1;i<=4;i++)point(p,base.add(s.mirrorDir.scale(i*.28)),WHITE,false);
        seal(p,base.add(0,1.2,0),visible,.53,now*.04);
    }
    private static void coreVisual(ServerPlayer p,PlayerData d,long now){int lit=(d.getMingzhuEnergy()+132)/133;Vec3 c=p.position().add(0,.75,0);for(int i=0;i<lit;i++)for(int j=0;j<3;j++){double a=now*.035+i*Math.PI*2/3+j*.15;Vec3 at=c.add(Math.cos(a)*.65,i*.07,Math.sin(a)*.65);point(p,at,d.getMingzhuEnergy()>=320?WHITE:GOLD,false);}}
    private static void beamVisual(ServerPlayer p,Vec3 a,Vec3 b,boolean firing,long now,boolean giant){
        double length=a.distanceTo(b);Vec3 dir=b.subtract(a).normalize();
        if(!firing){
            int count=Math.max(1,(int)Math.ceil(length));
            for(int i=1;i<=count;i++)point(p,a.lerp(b,i/(double)count),WHITE,false);
        }else{
            // Split before visibility filtering: never transmit a whole beam through unseen terrain.
            int parts=Math.max(1,(int)Math.ceil(length/3));
            for(int i=0;i<parts;i++){
                Vec3 from=a.lerp(b,i/(double)parts),to=a.lerp(b,(i+1)/(double)parts),delta=to.subtract(from);
                for(ServerPlayer viewer:p.level().players()){
                    if(viewer.distanceToSqr(from)>1600||!MapSystem.contains(from.x,from.z)||!MapSystem.contains(to.x,to.z))continue;
                    if(viewer!=p&&(!VisionSystem.canSeePosition(viewer,from)||!VisionSystem.canSeePosition(viewer,to)
                            ||!VisionSystem.canSeePosition(viewer,from.lerp(to,.5))))continue;
                    p.level().sendParticles(viewer,giant?ModParticles.MINGZHU_GIANT_RAY.get():ModParticles.MINGZHU_RAY.get(),
                            false,false,from.x,from.y,from.z,0,delta.x,delta.y,delta.z,1);
                }
            }
        }
        if(firing){burst(p,b,.16);ring(p,p.position().add(0,.06,0),.65,GOLD,8,false);}
        if(firing&&giant){seal(p,a.add(dir.scale(.75)),dir,1.1,now*.1);seal(p,a.add(dir.scale(1.5)),dir,.8,-now*.1);}
    }
    private static void sound(ServerPlayer p,Vec3 at,String name,float volume){
        for(ServerPlayer v:p.level().players())if(v.connection!=null&&v.distanceToSqr(at)<1024&&(v==p||VisionSystem.canSeePosition(v,at)))
            v.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.Holder.direct(ModSounds.mingzhu(name)),SoundSource.PLAYERS,at.x,at.y,at.z,volume,1F,p.getRandom().nextLong()));
    }
    private static final class State {
        final net.minecraft.server.level.ServerLevel world;long basicAt,qAt,placeUntil,mirrorUntil,beamAt,nextBeam,decayAt,giantUntil;int basicTotal=1,qLevel,wLevel,beamLevel,beamTicks,rCooldown,dashTicks,radiance;boolean placeNoCd,placeNoMana,turned,channel,dashMirrored,lastHeroHit;double dashDistance,dashLimit;Vec3 qDirection,mirror,mirrorDir,mirrorVisualDir,beamDirection,dashDirection,dashPrevious;final List<Ball> balls=new ArrayList<>();
        State(ServerPlayer p){world=p.level();decayAt=p.level().getGameTime()+20;}
    }
    private static final class Ball {Vec3 pos,dir;final boolean q;float damage;double travelled;boolean reflected;int reward;final Set<UUID> hit=new HashSet<>();Ball(Vec3 pos,Vec3 dir,boolean q,float damage){this.pos=pos;this.dir=dir;this.q=q;this.damage=damage;}}
}
