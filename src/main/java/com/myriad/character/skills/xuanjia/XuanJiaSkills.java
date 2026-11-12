package com.myriad.character.skills.xuanjia;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.client.particle.ParticleEffects;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.player.ModShieldSystem;
import com.myriad.registry.ModAttributes;
import com.myriad.network.NetworkSetup.DashPayload;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 山岳·玄甲：山甲减伤、护盾爆发、推人冲锋与镇岳吸附。 */
public final class XuanJiaSkills extends Skill {
    public enum Type { PASSIVE, Q, W, E, R }
    private static final DustParticleOptions ROCK = new DustParticleOptions(0x8B7248, 1.35F);
    private static final DustParticleOptions RUNE = new DustParticleOptions(0xE4B94F, .9F);
    private static final DustParticleOptions EARTH_GLOW = new DustParticleOptions(0xC99B4B, 1.15F);
    private static final DustParticleOptions EARTH_BLOCK = new DustParticleOptions(0xA87838, 2.8F);
    private static final Identifier SUPER_ARMOR = Identifier.fromNamespaceAndPath("myriad", "xuanjia_super_armor");
    private static final Identifier SLOW_RESIST = Identifier.fromNamespaceAndPath("myriad", "xuanjia_slow_resist");
    private static final Map<UUID,Integer> ARMOR_STACKS = new HashMap<>();
    private static final Map<UUID,Long> NEXT_ARMOR = new HashMap<>();
    private static final Map<UUID,Long> SUPER_ARMOR_UNTIL = new HashMap<>();
    private static final Map<UUID,ShieldState> SHIELDS = new HashMap<>();
    private static final Map<UUID,FistWindup> FIST_WINDUPS = new HashMap<>();
    private static final Map<UUID,ChargeState> CHARGES = new HashMap<>();
    private static final Map<UUID,ImpactRipple> IMPACT_RIPPLES = new HashMap<>();
    private static final Map<UUID,PullState> PULLS = new HashMap<>();
    private static final Map<UUID,DomainState> DOMAINS = new HashMap<>();
    private static final Map<UUID,ReductionState> REDUCTION = new HashMap<>();
    private final Type type;

    public XuanJiaSkills(Type type) {
        super(switch(type){case PASSIVE->"xuanjia_passive";case Q->"hanshanquan";case W->"yanbihuti";case E->"duanmaichongfeng";case R->"zhenyuetianmen";},
                switch(type){case PASSIVE->0;case Q->160;case W->320;case E->360;case R->1700;},
                switch(type){case PASSIVE->0.0F;case Q->90.0F;case W->126.0F;case E->144.0F;case R->324.0F;});
        this.type=type;
    }
    @Override public boolean isPassive(){return type==Type.PASSIVE;}
    @Override public boolean isUltimate(){return type==Type.R;}
    @Override public int getCooldownTicks(int level){
        if(type==Type.W)return Math.max(240,320-(Math.max(0,level-1)/2)*20);
        if(type==Type.R)return Math.max(500,1700-Math.max(0,level-1)*300);
        return super.getCooldownTicks(level);
    }
    @Override public String levelEffects(int level){int lv=Math.max(1,level);return switch(type){
        case PASSIVE->"脱战每5秒获得1层山甲（最多3层），每层抵消下一次伤害的40%";
        case Q->"前方4格、180度扇形造成"+(24+(lv-1)*10)+"+65%物攻，击退"+(2+((lv-1)/2)*.5)+"格";
        case W->"获得"+(60+(lv-1)*20)+"+8%最大生命护盾；结束造成"+(20+(lv-1)*8)+"+4%最大生命";
        case E->"冲锋8格，造成"+(35+(lv-1)*12)+"+70%物攻，撞墙眩晕1.5秒";
        case R->"吸附13格敌方玩家，造成30+2%最大生命；"+(25+(lv-1)*3)+"%减伤持续"+(5+lv-1)+"秒";};}

    @Override public void cast(ServerPlayer p,PlayerData data){ServerLevel l=p.level();int lv=Math.max(1,data.skillLevel(getId()));long now=l.getGameTime();
        if(type==Type.Q){
            Vec3 look=horizontal(p.getLookAngle());
            FIST_WINDUPS.put(p.getUUID(),new FistWindup(now+20,lv,
                    p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK),look));
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,20,0,false,false,false));
            l.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.IRON_GOLEM_REPAIR,
                    SoundSource.PLAYERS,.55F,.72F);
        }else if(type==Type.W){
            float shield=(float)(60+(lv-1)*20+data.getModMaxHealth()*.08);
            ModShieldSystem.grant(p,"xuanjia_yanbihuti",shield,80);
            SHIELDS.put(p.getUUID(),new ShieldState(now+80,lv));data.setBuff("yanbihuti",80);
            ring(l,p.position(),1.2,RUNE,34);l.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.SHIELD_BLOCK,SoundSource.PLAYERS,.8F,.72F);
        }else if(type==Type.E){
            Vec3 direction=horizontal(p.getLookAngle());
            CHARGES.put(p.getUUID(),new ChargeState(direction,now+10,lv,p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK)));
            PacketDistributor.sendToPlayer(p,new DashPayload((float)direction.x,0F,(float)direction.z,10));
            p.setDeltaMovement(direction.x*.8,0,direction.z*.8);
            p.resetFallDistance();
            l.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.RAVAGER_ROAR,SoundSource.PLAYERS,.65F,.8F);
        }else if(type==Type.R){
            Set<UUID> targets=new HashSet<>();
            Vec3 center=p.position();
            for(LivingEntity other:enemies(l,p,center,13)){targets.add(other.getUUID());DamageCalc.dealSkillDamage(p,other,(float)(120+data.getModMaxHealth()*.08),Kind.PHYSICAL, 3);}
            PULLS.put(p.getUUID(),new PullState(now+20,targets));int duration=(5+lv-1)*20;
            DOMAINS.put(p.getUUID(),new DomainState(center,now+duration,targets));
            REDUCTION.put(p.getUUID(),new ReductionState(now+duration,.25F+(lv-1)*.03F));data.setBuff("zhenyuetianmen",duration);
            wall(l,center,13);l.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.WARDEN_ROAR,SoundSource.PLAYERS,.85F,.65F);
        }
    }

    public static void tick(ServerPlayer p,PlayerData data){ServerLevel l=p.level();long now=l.getGameTime();
        if(CharacterType.byId(data.getCharacterId())!=CharacterType.XUANJIA)return;
        boolean out=data.getLastCombatGameTime()<0||now-data.getLastCombatGameTime()>200;
        if(out&&now>=NEXT_ARMOR.getOrDefault(p.getUUID(),now)){int n=Math.min(3,ARMOR_STACKS.getOrDefault(p.getUUID(),0)+1);ARMOR_STACKS.put(p.getUUID(),n);NEXT_ARMOR.put(p.getUUID(),now+100);data.setBuff("xuanjia_passive",n);}
        else if(!out)NEXT_ARMOR.put(p.getUUID(),now+100);
        data.setBuff("xuanjia_passive", ARMOR_STACKS.getOrDefault(p.getUUID(),0));
        int armorStacks=ARMOR_STACKS.getOrDefault(p.getUUID(),0);
        if(armorStacks>0&&now%4==0)passiveEarthAura(l,p,armorStacks);
        Long armor=SUPER_ARMOR_UNTIL.get(p.getUUID());if(armor!=null&&now>=armor){SUPER_ARMOR_UNTIL.remove(p.getUUID());var a=p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);if(a!=null)a.removeModifier(SUPER_ARMOR);}
        else if(armor!=null&&p.hasEffect(MobEffects.SLOWNESS))p.removeEffect(MobEffects.SLOWNESS);
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);var slow=p.getEffect(MobEffects.SLOWNESS);
        if(speed!=null){speed.removeModifier(SLOW_RESIST);if(SHIELDS.containsKey(p.getUUID())&&slow!=null){double compensate=.045*(slow.getAmplifier()+1);speed.addTransientModifier(new AttributeModifier(SLOW_RESIST,compensate,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}}
        ShieldState shield=SHIELDS.get(p.getUUID());if(shield!=null&&now>=shield.until){SHIELDS.remove(p.getUUID());ModShieldSystem.removeSource(p,"xuanjia_yanbihuti");data.setBuff("yanbihuti",0);
            float dmg=(float)(80+(shield.level-1)*32+data.getModMaxHealth()*.16);for(LivingEntity e:enemies(l,p,p.position(),3))DamageCalc.dealSkillDamage(p,e,dmg,Kind.PHYSICAL, 1);ring(l,p.position(),3,ROCK,42);l.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.GENERIC_EXPLODE.value(),SoundSource.PLAYERS,.5F,.75F);}
        else if(shield!=null&&now%4==0)shieldSphere(l,p,now);
        tickFist(p,l,now);tickCharge(p,l,now);tickImpactRipple(p,l,now);tickPull(p,l,now);tickDomain(p,l,now);ReductionState red=REDUCTION.get(p.getUUID());if(red!=null&&now>=red.until){REDUCTION.remove(p.getUUID());data.setBuff("zhenyuetianmen",0);}
    }

    private static void tickFist(ServerPlayer p,ServerLevel l,long now){FistWindup windup=FIST_WINDUPS.get(p.getUUID());if(windup==null)return;if(now<windup.releaseAt){double progress=1-(windup.releaseAt-now)/20.0;double radius=1.8-.9*progress;ring(l,p.position().add(windup.look.scale(.7)),radius,EARTH_GLOW,24);ParticleEffects.burst(l,p.position().add(0,1,0),8,1.2-progress*.5,.78F,.52F,.18F);return;}FIST_WINDUPS.remove(p.getUUID());releaseFist(p,l,windup,now);}

    private static void releaseFist(ServerPlayer p,ServerLevel l,FistWindup windup,long now){Vec3 releaseLook=horizontal(p.getLookAngle());int hit=0;double range=4;for(LivingEntity e:enemies(l,p,p.position(),range)){Vec3 to=horizontal(e.position().subtract(p.position()));if(releaseLook.dot(to)<0)continue;DamageCalc.dealSkillDamage(p,e,(float)(96+(windup.level-1)*40+windup.ad*2.60),Kind.PHYSICAL, 0);com.myriad.combat.KnockbackBuffer.apply(e,new Vec3(releaseLook.x*.72,Math.max(.24,e.getDeltaMovement().y),releaseLook.z*.72));hit++;}if(hit>=2){SUPER_ARMOR_UNTIL.put(p.getUUID(),now+20);var a=p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);if(a!=null){a.removeModifier(SUPER_ARMOR);a.addTransientModifier(new AttributeModifier(SUPER_ARMOR,1,AttributeModifier.Operation.ADD_VALUE));}}cone(l,p,releaseLook,range);ParticleEffects.burst(l,p.position().add(releaseLook.scale(2)).add(0,.8,0),36,1.6,.72F,.42F,.14F);l.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.IRON_GOLEM_ATTACK,SoundSource.PLAYERS,.9F,.72F);}

    private static void tickCharge(ServerPlayer p,ServerLevel l,long now){ChargeState c=CHARGES.get(p.getUUID());if(c==null)return;if(now>=c.until){finishCharge(p,l,c,false);return;}
        if(blocked(l,p.position().add(c.direction.scale(.8)))){finishCharge(p,l,c,true);return;}
        p.setDeltaMovement(c.direction.x*.8,0,c.direction.z*.8);p.resetFallDistance();l.sendParticles(ROCK,p.getX(),p.getY()+.2,p.getZ(),8,.5,.15,.5,.04);
        if(c.target==null)for(LivingEntity e:l.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(.65),x->x!=p&&x.isAlive()&&!ally(p,x))){c.target=e.getUUID();DamageCalc.dealSkillDamage(p,e,(float)(140+(c.level-1)*48+c.ad*2.80),Kind.PHYSICAL, 2);if(!(e instanceof ServerPlayer)){finishCharge(p,l,c,false);return;}break;}
        if(c.target!=null){var raw=l.getEntity(c.target);if(raw instanceof ServerPlayer target&&com.myriad.combat.SkillTargets.alive(target)){Vec3 ahead=p.position().add(c.direction.scale(1.05));target.teleportTo(ahead.x,p.getY(),ahead.z);target.setDeltaMovement(c.direction.scale(.75));if(blocked(l,ahead.add(c.direction.scale(.7)))){target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,30,255,false,false,false));finishCharge(p,l,c,true);}}}
    }
    private static void finishCharge(ServerPlayer p,ServerLevel l,ChargeState c,boolean hitWall){CHARGES.remove(p.getUUID());p.setDeltaMovement(Vec3.ZERO);PacketDistributor.sendToPlayer(p,new DashPayload(0,0,0,0));if(hitWall){IMPACT_RIPPLES.put(p.getUUID(),new ImpactRipple(p.position(),l.getGameTime(),l.getGameTime()+12));l.sendParticles(EARTH_BLOCK,p.getX(),p.getY()+1,p.getZ(),28,.75,.8,.75,.1);ParticleEffects.burst(l,p.position().add(0,.7,0),42,1.5,.66F,.39F,.12F);l.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.ANVIL_LAND,SoundSource.PLAYERS,.8F,.7F);}}
    private static void tickImpactRipple(ServerPlayer p,ServerLevel l,long now){ImpactRipple ripple=IMPACT_RIPPLES.get(p.getUUID());if(ripple==null)return;if(now>=ripple.until){IMPACT_RIPPLES.remove(p.getUUID());return;}double progress=(now-ripple.started)/(double)Math.max(1,ripple.until-ripple.started);double radius=.3+progress*4.2;ring(l,ripple.center,radius,ROCK,46);if(progress>.2)ring(l,ripple.center.add(0,.06,0),radius*.72,EARTH_GLOW,34);}
    private static void tickPull(ServerPlayer p,ServerLevel l,long now){PullState pull=PULLS.get(p.getUUID());if(pull==null)return;if(now>=pull.until){PULLS.remove(p.getUUID());return;}for(UUID id:pull.targets){var raw=l.getEntity(id);if(raw instanceof LivingEntity e&&com.myriad.combat.SkillTargets.alive(e)){Vec3 d=p.position().subtract(e.position());if(d.horizontalDistanceSqr()>.8){Vec3 h=horizontal(d).scale(.48);e.setDeltaMovement(h.x,Math.max(.08,e.getDeltaMovement().y),h.z);}}}if(now%3==0)ring(l,p.position(),Math.max(1,(pull.until-now)*.65),RUNE,36);}

    /** 镇岳天门领域：三格高粒子墙持续存在，进入领域的敌方生物均不能越过半径边界。 */
    private static void tickDomain(ServerPlayer owner,ServerLevel level,long now){DomainState domain=DOMAINS.get(owner.getUUID());if(domain==null)return;if(now>=domain.until){DOMAINS.remove(owner.getUUID());return;}
        for(LivingEntity e:enemies(level,owner,domain.center,13))domain.targets.add(e.getUUID());
        for(UUID id:new HashSet<>(domain.targets)){var raw=level.getEntity(id);if(!(raw instanceof LivingEntity e)||!com.myriad.combat.SkillTargets.alive(e)){domain.targets.remove(id);continue;}Vec3 delta=new Vec3(e.getX()-domain.center.x,0,e.getZ()-domain.center.z);double distance=delta.length();if(distance<1e-5)continue;Vec3 normal=delta.scale(1/distance);Vec3 velocity=e.getDeltaMovement();double outward=velocity.x*normal.x+velocity.z*normal.z;if(distance>=12.35&&outward>0)e.setDeltaMovement(velocity.x-normal.x*outward,velocity.y,velocity.z-normal.z*outward);if(distance>12.75){Vec3 inside=domain.center.add(normal.scale(12.55));if(e instanceof ServerPlayer player)player.teleportTo(inside.x,e.getY(),inside.z);else e.teleportTo(inside.x,e.getY(),inside.z);}}
        if(now%4==0)wall(level,domain.center,13);
    }

    public static void onIncomingDamage(ServerPlayer p,LivingIncomingDamageEvent event){if(event.getAmount()<=0)return;int n=ARMOR_STACKS.getOrDefault(p.getUUID(),0);if(n>0){event.getContainer().setNewDamage(event.getAmount()*.60F);n--;ARMOR_STACKS.put(p.getUUID(),n);PlayerDataProvider.get(p).setBuff("xuanjia_passive",n);blockArmorBurst(p.level(),p);}
        ReductionState r=REDUCTION.get(p.getUUID());if(r!=null&&r.until>p.level().getGameTime())event.getContainer().setNewDamage(event.getAmount()*(1-r.reduction));}
    public static void clearFor(ServerPlayer p){UUID id=p.getUUID();ARMOR_STACKS.remove(id);NEXT_ARMOR.remove(id);SUPER_ARMOR_UNTIL.remove(id);SHIELDS.remove(id);FIST_WINDUPS.remove(id);CHARGES.remove(id);IMPACT_RIPPLES.remove(id);PULLS.remove(id);DOMAINS.remove(id);REDUCTION.remove(id);PacketDistributor.sendToPlayer(p,new DashPayload(0,0,0,0));var a=p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);if(a!=null)a.removeModifier(SUPER_ARMOR);var s=p.getAttribute(Attributes.MOVEMENT_SPEED);if(s!=null)s.removeModifier(SLOW_RESIST);}
    private static boolean blocked(ServerLevel l,Vec3 p){return !l.getBlockState(net.minecraft.core.BlockPos.containing(p.x,p.y+.5,p.z)).getCollisionShape(l,net.minecraft.core.BlockPos.containing(p.x,p.y+.5,p.z)).isEmpty();}
    private static java.util.List<LivingEntity> enemies(ServerLevel l,ServerPlayer p,Vec3 c,double r){return l.getEntitiesOfClass(LivingEntity.class,new AABB(c,c).inflate(r),e->e!=p&&com.myriad.combat.SkillTargets.alive(e)&&!ally(p,e)&&e.distanceToSqr(c)<=r*r);}
    private static boolean ally(ServerPlayer a,LivingEntity e){if(e==a)return true;if(!(e instanceof ServerPlayer b))return false;GameManager g=GameManager.get();Faction x=g==null?null:g.factionOf(a),y=g==null?null:g.factionOf(b);return x!=null&&x==y;}
    private static Vec3 horizontal(Vec3 v){Vec3 h=new Vec3(v.x,0,v.z);return h.lengthSqr()<1e-6?new Vec3(0,0,1):h.normalize();}
    private static void ring(ServerLevel l,Vec3 c,double r,DustParticleOptions d,int n){for(int i=0;i<n;i++){double a=Math.PI*2*i/n;l.sendParticles(d,c.x+Math.cos(a)*r,c.y+.1,c.z+Math.sin(a)*r,1,0,0,0,0);}}
    private static void passiveEarthAura(ServerLevel l,ServerPlayer p,int stacks){double phase=l.getGameTime()*.17;for(int i=0;i<4+stacks*3;i++){double angle=phase+i*Math.PI*2/(4+stacks*3);double radius=.55+(i%3)*.2;Vec3 point=p.position().add(Math.cos(angle)*radius,.15+(i%5)*.36,Math.sin(angle)*radius);l.sendParticles(i%4==0?EARTH_GLOW:ROCK,point.x,point.y,point.z,1,.035,.05,.035,.01);if(i%3==0)ParticleEffects.mote(l,point,.72F,.47F,.16F);}}
    private static void blockArmorBurst(ServerLevel l,ServerPlayer p){for(int layer=0;layer<3;layer++){double radius=.75+layer*.38;for(int i=0;i<18;i++){double angle=i*Math.PI*2/18+layer*.35;Vec3 point=p.position().add(Math.cos(angle)*radius,.35+layer*.45+Math.sin(angle*2)*.18,Math.sin(angle)*radius);l.sendParticles(EARTH_BLOCK,point.x,point.y,point.z,1,0,0,0,0);}}ParticleEffects.burst(l,p.position().add(0,1,0),48,1.55,.75F,.48F,.16F);l.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.SHIELD_BLOCK,SoundSource.PLAYERS,.9F,.58F);}
    private static void shieldSphere(ServerLevel l,ServerPlayer p,long now){int points=44;double radius=1.88+.12*Math.sin(now*.45);double rotation=now*.12;for(int i=0;i<points;i++){double y=1-2*(i+.5)/points;double horizontal=Math.sqrt(Math.max(0,1-y*y));double angle=i*Math.PI*(3-Math.sqrt(5))+rotation;Vec3 point=p.position().add(Math.cos(angle)*horizontal*radius,1+y*radius,Math.sin(angle)*horizontal*radius);if((i+now/4)%3!=0)continue;l.sendParticles(i%4==0?RUNE:EARTH_GLOW,point.x,point.y,point.z,1,0,0,0,0);ParticleEffects.mote(l,point,.78F,.56F,.22F);}}
    private static void wall(ServerLevel l,Vec3 c,double r){int points=72;for(int layer=0;layer<=3;layer++){double y=c.y+layer;for(int i=0;i<points;i++){double a=Math.PI*2*i/points;l.sendParticles(i%3==0?RUNE:ROCK,c.x+Math.cos(a)*r,y,c.z+Math.sin(a)*r,1,0,0,0,0);}}}
    private static void cone(ServerLevel l,ServerPlayer p,Vec3 look,double r){double yaw=Math.atan2(look.z,look.x);for(int i=0;i<42;i++){double a=yaw+Math.toRadians(-90+180*i/41.0);double q=r*(.35+.65*(i%5)/4.0);l.sendParticles(ROCK,p.getX()+Math.cos(a)*q,p.getY()+.4,p.getZ()+Math.sin(a)*q,1,0,0,0,0);}}
    private record ShieldState(long until,int level){}
    private record FistWindup(long releaseAt,int level,double ad,Vec3 look){}
    private static final class ChargeState{final Vec3 direction;final long until;final int level;final double ad;UUID target;ChargeState(Vec3 d,long u,int l,double a){direction=d;until=u;level=l;ad=a;}}
    private record ImpactRipple(Vec3 center,long started,long until){}
    private record PullState(long until,Set<UUID> targets){}
    private static final class DomainState{final Vec3 center;final long until;final Set<UUID> targets;DomainState(Vec3 center,long until,Set<UUID> targets){this.center=center;this.until=until;this.targets=new HashSet<>(targets);}}
    private record ReductionState(long until,float reduction){}
}
