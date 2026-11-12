package com.myriad.character.skills.shenli;

import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.combat.WindWallSystem;
import com.myriad.combat.DamageCalc;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.MapSystem;
import com.myriad.network.NetworkSetup.DashPayload;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 机铳·沈砺：服务器权威六发弹仓、节奏装填、三色弹体与反冲 motion。 */
public final class ShenLiSkills extends Skill {
    public enum Type { PASSIVE, Q, W, E, R }
    private enum Color { RED, CYAN, GOLD;
        Color next(){return values()[(ordinal()+1)%3];}
    }
    private enum ShotKind { BASIC, Q, E }
    public static final int W_PLACEMENT=60;
    private static final Identifier FORCED_SLOW=Identifier.fromNamespaceAndPath("myriad","shenli_forced_reload_slow");
    private static final Identifier W_SLOW=Identifier.fromNamespaceAndPath("myriad","shenli_w_reload_slow");
    private static final Identifier AIM_SLOW=Identifier.fromNamespaceAndPath("myriad","shenli_aim_slow");
    private static final Identifier MIND_AS=Identifier.fromNamespaceAndPath("myriad","shenli_mind_as");
    private static final Identifier PRECISE_AS=Identifier.fromNamespaceAndPath("myriad","shenli_precise_as");
    private static final Identifier R_AS=Identifier.fromNamespaceAndPath("myriad","shenli_r_as");
    private static final DustParticleOptions RED=new DustParticleOptions(0xD85A36,1.05F);
    private static final DustParticleOptions CYAN=new DustParticleOptions(0x67EDF2,.95F);
    private static final DustParticleOptions GOLD=new DustParticleOptions(0xF5C451,1.05F);
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final Map<UUID,BasicContext> BASIC_CONTEXTS=new HashMap<>();
    private final Type type;

    public ShenLiSkills(Type type){
        super(switch(type){case PASSIVE->"shenli_passive";case Q->"guanxingjiaoshe";case W->"xuanjidingtang";case E->"tuihuofanchong";case R->"tiangongbuxilun";},
                switch(type){case PASSIVE->0;case Q->140;case W->180;case E->300;case R->1800;},
                switch(type){case PASSIVE->0.0F;case Q->64.8F;case W->72.0F;case E->108.0F;case R->270.0F;});
        this.type=type;
    }
    @Override public boolean isPassive(){return type==Type.PASSIVE;}
    @Override public boolean isUltimate(){return type==Type.R;}
    @Override public int getCooldownTicks(int level){int lv=Math.max(1,level);return switch(type){case Q->Math.max(110,140-(lv-1)*10);case W->Math.max(150,180-(lv-1)*10);case E->Math.max(240,300-(lv-1)*20);case R->Math.max(1200,1800-(lv-1)*300);default->0;};}
    @Override public String levelEffects(int level){int lv=Math.max(1,level);return switch(type){
        case PASSIVE->"射手普攻按80%物攻结算；六发弹仓按赤、青、金循环；空仓后强制装填，连续4次英雄命中获得机心";
        case Q->"高速校射造成 "+(24+(lv-1)*9)+"+70%物攻，可暴击并强化当前弹色";
        case W->"24 tick主动装填；再次施放按指针颜色定膛，精准时获得20%攻速";
        case E->"反向motion位移4格，弹丸造成 "+(18+(lv-1)*10)+"+55%物攻";
        case R->"持续 "+(5+lv)+" 秒，攻速+25%，英雄命中复制已消耗灵丸";};}
    @Override public void cast(ServerPlayer player,PlayerData data){}

    public static void handleCast(ServerPlayer p,PlayerData data,int slot,Skill raw,boolean noCd,boolean noMana){
        if(!(raw instanceof ShenLiSkills skill))return;State s=state(p);long now=p.level().getGameTime();
        if(skill.type!=Type.W&&skill.type!=Type.R&&!canShoot(s,now)){message(p,"message.myriad.shenli_reloading");return;}
        if(skill.type==Type.W){castW(p,data,s,skill,noCd,noMana,now);return;}
        if(!noCd&&data.getCooldown(skill.getId())>0){cooldownMessage(p,data,skill);return;}
        if(!noMana&&data.getMana()<skill.getManaCost()){message(p,"message.myriad.not_enough_mana");return;}
        if(skill.type!=Type.R&&s.ammo.isEmpty()){startForcedReload(p,data,s,now);return;}
        if(!noMana)data.spendMana(skill.getManaCost());int lv=Math.max(1,data.skillLevel(skill.getId()));
        switch(skill.type){
            case Q->fireQ(p,data,s,lv,now);
            case E->fireE(p,data,s,lv,now);
            case R->castR(p,data,s,lv,now);
            default->{}
        }
        if(!noCd)data.setCooldown(skill.getId(),reducedCooldown(p,skill.getCooldownTicks(lv)));
        
        com.myriad.character.SkillPresentation.cast(p, skill);
        PlayerDataProvider.syncToClient(p);
    }

    private static boolean canUseGun(ServerPlayer p, PlayerData data) {
        return com.myriad.game.ModState.isEnabled() && data != null
                && CharacterType.byId(data.getCharacterId()) == CharacterType.SHENLI
                && com.myriad.combat.BasicAttackSystem.canAttackNow(p, data)
                
                && p.getMainHandItem().getItem() instanceof com.myriad.item.ShenLiGunItem;
    }

    public static void toggleAim(ServerPlayer p) {
        PlayerData data = PlayerDataProvider.get(p);
        if (!canUseGun(p, data)) return;
        State s = state(p);
        long now = p.level().getGameTime();
        if (s.lastAimToggle == now) return;
        s.lastAimToggle = now;
        s.aiming = !s.aiming;
        data.setBuff("shenli_aiming", s.aiming ? 1 : 0);
        PlayerDataProvider.syncToClient(p);
    }

    /** 左键立即出弹，之后建立服务器权威射击后摇。 */
    public static boolean fireBasic(ServerPlayer p){
        PlayerData data=PlayerDataProvider.get(p);if(!canUseGun(p,data))return false;State s=state(p);long now=p.level().getGameTime();
        if(s.pendingBasic!=null){
            if(now<s.pendingBasic.releaseAt){message(p,"message.myriad.shenli_reloading");return false;}
            s.pendingBasic=null;
        }
        if(!canShoot(s,now)){message(p,"message.myriad.shenli_reloading");return false;}
        if(s.ammo.isEmpty()){startForcedReload(p,data,s,now);return false;}
        boolean fired=fireBasicProjectile(p,data,s,now,1.0F);
        if(fired){
            int recovery=basicRecoveryTicks(p);
            s.pendingBasic=new PendingBasic(now+recovery,recovery);
            data.setBuff("shenli_basic_windup",recovery);
            data.setBuff("shenli_basic_windup_total",recovery);
        }
        return fired;
    }

    private static void fireQ(ServerPlayer p,PlayerData data,State s,int lv,long now){
        Vec3 dir=p.getLookAngle().normalize();Color color=consumeRound(p,data,s,now);boolean tri=s.fireMarks==7;s.fireMarks=tri?0:s.fireMarks;
        Vec3 muzzle=p.getEyePosition().add(dir.scale(.55));spawnBullet(p,s,new Bullet(ShotKind.Q,color,muzzle,dir,2.4,20,.35,lv,ad(p),tri));
        muzzleVfx(p.level(),muzzle,dir,color,ShotKind.Q,tri);fireSound(p,color);
    }
    private static void fireE(ServerPlayer p,PlayerData data,State s,int lv,long now){
        Vec3 dir=p.getLookAngle().normalize();Color color=consumeRound(p,data,s,now);
        Vec3 muzzle=p.getEyePosition().add(dir.scale(.55));spawnBullet(p,s,new Bullet(ShotKind.E,color,muzzle,dir,1.8,14,.45,lv,ad(p),false));
        Vec3 back=p.getLookAngle().normalize().scale(-.8);s.dash=new Dash(back,5);
        p.setDeltaMovement(back);
        PacketDistributor.sendToPlayer(p,new DashPayload((float)back.x,(float)back.y,(float)back.z,5));
        muzzleVfx(p.level(),muzzle,dir,color,ShotKind.E,false);recoilBurst(p.level(),p.position().add(0,1,0),dir,color);
        p.level().playSound(null,p.blockPosition(),SoundEvents.GENERIC_EXPLODE.value(),SoundSource.PLAYERS,.55F,1.7F);
    }
    private static void castR(ServerPlayer p,PlayerData data,State s,int lv,long now){
        cancelWState(data,s);s.pendingBasic=null;data.setBuff("shenli_basic_windup",0);data.setBuff("shenli_basic_windup_total",0);s.forcedUntil=0;fillAmmo(s,Color.RED,6);s.rUntil=now+(100+lv*20L);s.fireMarks=0;
        data.setBuff("tiangongbuxilun",(int)(s.rUntil-now));
        ultimateStartVfx(p.level(),p);
        p.level().playSound(null,p.blockPosition(),SoundEvents.VAULT_ACTIVATE,SoundSource.PLAYERS,1,.85F);
    }

    private static void castW(ServerPlayer p,PlayerData data,State s,ShenLiSkills skill,boolean noCd,boolean noMana,long now){
        if(s.wStarted<=0){
            if(s.forcedUntil>now){message(p,"message.myriad.shenli_reloading");return;}
            if(s.ammo.size()>=6){message(p,"message.myriad.shenli_mag_full");return;}
            if(!noCd&&data.getCooldown(skill.getId())>0){cooldownMessage(p,data,skill);return;}
            if(!noMana&&data.getMana()<skill.getManaCost()){message(p,"message.myriad.not_enough_mana");return;}
            s.wStarted=now;s.wNoCd=noCd;s.wNoMana=noMana;data.setPlacementType(W_PLACEMENT);data.setBuff(skill.getId()+"_recast",24);
            reloadStartVfx(p.level(),p);
            PlayerDataProvider.syncToClient(p);return;
        }
        if(s.wFinishAt>now)return;
        int elapsed=(int)(now-s.wStarted),phase=Math.floorMod(elapsed,4),slot=Math.min(5,elapsed/4);Color chosen=Color.values()[slot%3];
        boolean precise=Math.abs(phase-2)<=((s.mindUntil>now)?2:1);s.wChosen=chosen;
        if(precise){completeW(p,data,s,skill,chosen,true,now);}else{s.wFinishAt=now+8;s.wPrecise=false;data.setBuff(skill.getId()+"_recast",8);}
    }
    public static void cancelW(ServerPlayer p,PlayerData data){State s=STATES.get(p.getUUID());if(s==null||s.wStarted<=0)return;cancelWState(data,s);PlayerDataProvider.syncToClient(p);}
    private static void completeW(ServerPlayer p,PlayerData data,State s,ShenLiSkills skill,Color chosen,boolean precise,long now){
        com.myriad.character.SkillPresentation.cast(p, skill); if(!s.wNoMana)data.spendMana(skill.getManaCost());int count=s.rUntil>now?s.ammo.size():6;fillAmmo(s,chosen,count);
        if(precise){s.preciseUntil=now+30;if(s.rUntil>now&&s.ammo.size()<6)s.ammo.addLast(chosen);}
        if(!s.wNoCd)data.setCooldown(skill.getId(),reducedCooldown(p,skill.getCooldownTicks(Math.max(1,data.skillLevel(skill.getId())))));
        reloadLockVfx(p.level(),p,chosen,precise);cancelWState(data,s);p.level().playSound(null,p.blockPosition(),(precise?SoundEvents.CROSSBOW_LOADING_END:SoundEvents.CROSSBOW_LOADING_MIDDLE).value(),SoundSource.PLAYERS,.8F,precise?1.65F:1.1F);PlayerDataProvider.syncToClient(p);
    }
    private static void cancelWState(PlayerData data,State s){s.wStarted=0;s.wFinishAt=0;s.wChosen=null;s.wPrecise=false;if(data.getPlacementType()==W_PLACEMENT)data.setPlacementType(0);data.setBuff("xuanjidingtang_recast",0);}

    public static void tick(ServerPlayer p,PlayerData data){
        if(CharacterType.byId(data.getCharacterId())!=CharacterType.SHENLI)return;State s=state(p);long now=p.level().getGameTime();
        if (!canUseGun(p, data)) {
            s.aiming = false;
            s.pendingBasic = null;
            data.setBuff("shenli_aiming", 0);
            data.setBuff("shenli_basic_windup", 0);
            data.setBuff("shenli_basic_windup_total", 0);
        }
        if(s.forcedUntil>0&&now>=s.forcedUntil){s.forcedUntil=0;fillAmmo(s,Color.RED,6);p.level().playSound(null,p.blockPosition(),SoundEvents.CROSSBOW_LOADING_END.value(),SoundSource.PLAYERS,.8F,1.25F);}
        if(s.wStarted>0){
            if(s.wFinishAt>0&&now>=s.wFinishAt)completeW(p,data,s,new ShenLiSkills(Type.W),s.wChosen,false,now);
            else if(s.wFinishAt<=0&&now-s.wStarted>=24){s.wChosen=Color.RED;completeW(p,data,s,new ShenLiSkills(Type.W),Color.RED,false,now);}
            else reloadTickVfx(p.level(),p,s,now);
        }
        tickBasicRecovery(data,s,now);tickDash(p,s);tickBullets(p,data,s,now);tickDebuffs(p,s,now);updateModifiers(p,s,now);syncState(data,s,now);
        if(s.rUntil>now)ultimateTickVfx(p.level(),p,s,now);
        PlayerDataProvider.syncToClient(p);
    }
    private static void tickBasicRecovery(PlayerData data,State s,long now){
        PendingBasic pending=s.pendingBasic;if(pending==null)return;int remaining=Math.max(0,(int)(pending.releaseAt-now));data.setBuff("shenli_basic_windup",remaining);
        if(now>=pending.releaseAt){s.pendingBasic=null;data.setBuff("shenli_basic_windup",0);data.setBuff("shenli_basic_windup_total",0);}
    }
    private static boolean fireBasicProjectile(ServerPlayer p,PlayerData data,State s,long now,float attackMultiplier){
        if(!canUseGun(p,data))return false;
        if(!canShoot(s,now)||s.ammo.isEmpty()){if(s.ammo.isEmpty())startForcedReload(p,data,s,now);return false;}
        Vec3 direction=ShenLiBallistics.direction(p.getLookAngle(),s.aiming,p.getRandom().nextDouble(),p.getRandom().nextDouble());
        Color color=consumeRound(p,data,s,now);
        double speed=s.aiming?ShenLiBallistics.AIM_SPEED:s.rUntil>now?1.68:1.4;
        double range=s.aiming?ShenLiBallistics.AIM_RANGE:16;
        Vec3 muzzle=p.getEyePosition().add(direction.scale(.55));
        Bullet bullet=new Bullet(ShotKind.BASIC,color,muzzle,direction,speed,range,.42,1,ad(p),false);
        bullet.basicMultiplier=attackMultiplier;bullet.traveled=.55;
        spawnBullet(p,s,bullet);muzzleVfx(p.level(),muzzle,direction,color,ShotKind.BASIC,false);fireSound(p,color);return true;
    }
    private static void tickDash(ServerPlayer p,State s){if(s.dash==null)return;if(s.dash.ticks--<=0){s.dash=null;p.setDeltaMovement(Vec3.ZERO);PacketDistributor.sendToPlayer(p,new DashPayload(0,0,0,0));return;}Vec3 step=s.dash.step,next=p.position().add(step);if(!MapSystem.contains(next.x,next.z)||!p.level().noCollision(p,p.getBoundingBox().move(step))){s.dash=null;p.setDeltaMovement(Vec3.ZERO);PacketDistributor.sendToPlayer(p,new DashPayload(0,0,0,0));return;}p.setDeltaMovement(step);p.resetFallDistance();p.level().sendParticles(ParticleTypes.SMOKE,p.getX(),p.getY()+.55,p.getZ(),3,.18,.12,.18,.015);orientedRing(p.level(),p.position().add(0,.8,0),step,.45,CYAN,10,0);}
    private static void tickBullets(ServerPlayer p,PlayerData data,State s,long now){
        s.bullets.removeIf(b->{double step=b.kind==ShotKind.BASIC?Math.min(b.speed,Math.max(0,b.range-b.traveled)):b.speed;Vec3 next=b.pos.add(b.dir.scale(step));
            if(blocked(p.level(),p,b.pos,next)){finishShot(p,data,s,b,false,null,now);return true;}
            // 首帧从眼部而不是枪口前端开始判定，贴脸目标不会落在弹道起点后方。
            Vec3 collisionFrom=b.age==0?p.getEyePosition():b.pos;
            LivingEntity target=firstTarget(p,collisionFrom,next,b.width);
            if(target!=null){hitBullet(p,data,s,b,target,now);return true;}
            b.pos=next;b.traveled+=step;b.age++;trail(p.level(),b);
            if(b.traveled>=b.range){finishShot(p,data,s,b,false,null,now);return true;}return false;});
    }
    private static void hitBullet(ServerPlayer p,PlayerData data,State s,Bullet b,LivingEntity target,long now){
        if (b.kind != ShotKind.BASIC && target instanceof com.myriad.game.DefenseTowerEntity) return;
        boolean hero=target instanceof ServerPlayer;
        if(b.kind==ShotKind.BASIC){BASIC_CONTEXTS.put(target.getUUID(),new BasicContext(p.getUUID(),b.color));DamageCalc.markBasicAttack(p,target,b.basicMultiplier);target.hurt(target.damageSources().playerAttack(p),1F);DamageCalc.clearBasicAttack(p,target);BASIC_CONTEXTS.remove(target.getUUID(),new BasicContext(p.getUUID(),b.color));}
        else{
            float base=b.kind==ShotKind.Q?24+(b.level-1)*9+b.ad*.70F:72+(b.level-1)*40+b.ad*2.20F;
            boolean red=b.tristar||b.color==Color.RED,cyan=b.tristar||b.color==Color.CYAN,gold=b.tristar||b.color==Color.GOLD;
            if(b.kind==ShotKind.E&&red)base+=b.ad*.32F;
            DamageCalc.Roll roll=b.kind==ShotKind.Q?dealCrittable(p,target,base,red):DamageCalc.dealSkillDamage(p,target,base,DamageCalc.Kind.PHYSICAL, 2);
            if(b.kind==ShotKind.E&&red)applyRedDebuff(p,target,s,now);
            if(cyan)applySlow(p,target,s,b.kind==ShotKind.Q?.35:.18,b.kind==ShotKind.Q?30:20,now);
            if(gold)splash(p,target,roll.damage()*(b.kind==ShotKind.Q?.40F:.25F),b.kind==ShotKind.Q?2.5:1.8);
            if(hero&&b.kind==ShotKind.Q)data.setCooldown("xuanjidingtang",Math.max(0,data.getCooldown("xuanjidingtang")-20));
        }
        finishShot(p,data,s,b,hero,target,now);impact(p.level(),target,b);
    }
    private static void finishShot(ServerPlayer p,PlayerData data,State s,Bullet b,boolean hero,LivingEntity target,long now){
        if(hero){
            if(now-s.lastHeroHit>50)s.combo=0;s.combo=Math.min(4,s.combo+1);s.lastHeroHit=now;if(s.combo>=4)s.mindUntil=now+80;
            if(s.rUntil>now){refund(s,b.color);s.fireMarks|=1<<b.color.ordinal();}
            if(b.kind==ShotKind.E)refund(s,b.color);
            if(s.rUntil>now||b.kind==ShotKind.E)refundVfx(p.level(),p,b.color);
        }else s.combo=0;
        if(!s.ammo.isEmpty()&&s.forcedUntil>now)s.forcedUntil=0;
    }

    /** 在最终普攻数值形成后应用三色普通灵丸。 */
    public static void onBasicAttack(ServerPlayer attacker,LivingEntity target,LivingIncomingDamageEvent event){
        BasicContext ctx=BASIC_CONTEXTS.remove(target.getUUID());if(ctx==null||!ctx.owner.equals(attacker.getUUID()))return;State s=state(attacker);long now=attacker.level().getGameTime();
        if(ctx.color==Color.RED){float extra=DamageCalc.applyMitigation(attacker,target,ad(attacker)*.08F,DamageCalc.Kind.PHYSICAL);event.getContainer().setNewDamage(event.getAmount()+extra);applyRedDebuff(attacker,target,s,now);}
        else if(ctx.color==Color.CYAN)applySlow(attacker,target,s,.18,20,now);
        else splash(attacker,target,event.getAmount()*.25F,1.8);
    }
    private static void applyRedDebuff(ServerPlayer p,LivingEntity target,State s,long now){
        if (target instanceof com.myriad.game.DefenseTowerEntity) return;
        Identifier id=redDebuffId(p);var attr=target.getAttribute(ModAttributes.PHYSICAL_DEFENSE);if(attr!=null){attr.removeModifier(id);attr.addTransientModifier(new AttributeModifier(id,-.12,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));s.redDebuffs.put(target.getUUID(),now+60);}
    }
    private static void applySlow(ServerPlayer p,LivingEntity target,State s,double amount,int ticks,long now){if(target instanceof com.myriad.game.DefenseTowerEntity)return;Identifier id=slowDebuffId(p);var attr=target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(attr!=null){attr.removeModifier(id);attr.addTransientModifier(new AttributeModifier(id,-amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));s.slows.put(target.getUUID(),now+ticks);}}
    private static void tickDebuffs(ServerPlayer p,State s,long now){s.redDebuffs.entrySet().removeIf(e->{if(now<e.getValue())return false;if(p.level().getEntity(e.getKey()) instanceof LivingEntity target){var a=target.getAttribute(ModAttributes.PHYSICAL_DEFENSE);if(a!=null)a.removeModifier(redDebuffId(p));}return true;});s.slows.entrySet().removeIf(e->{if(now<e.getValue())return false;if(p.level().getEntity(e.getKey()) instanceof LivingEntity target){var a=target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(a!=null)a.removeModifier(slowDebuffId(p));}return true;});}
    private static Identifier redDebuffId(ServerPlayer p){return Identifier.fromNamespaceAndPath("myriad","shenli_red_"+p.getUUID().toString().replace("-",""));}
    private static Identifier slowDebuffId(ServerPlayer p){return Identifier.fromNamespaceAndPath("myriad","shenli_slow_"+p.getUUID().toString().replace("-",""));}

    private static DamageCalc.Roll dealCrittable(ServerPlayer p,LivingEntity target,float base,boolean redPen){
        var pen=p.getAttribute(ModAttributes.PERCENT_ARMOR_PEN);Identifier temp=Identifier.fromNamespaceAndPath("myriad","shenli_q_red_pen");if(redPen&&pen!=null){pen.removeModifier(temp);pen.addTransientModifier(new AttributeModifier(temp,18,AttributeModifier.Operation.ADD_VALUE));}
        double chance=Mth.clamp(p.getAttributeValue(ModAttributes.CRIT_CHANCE)+0.0+0.0,0,100);
        boolean crit=chance>0&&p.getRandom().nextDouble()*100<chance;float raw=base*(crit?(float)(DamageCalc.effectiveCritDamage(p)/100.0):1F);float damage=DamageCalc.applyMitigation(p,target,raw,DamageCalc.Kind.PHYSICAL);damage=DamageCalc.applyMonsterCritReduction(target,damage,crit);
        if(pen!=null)pen.removeModifier(temp);DamageCalc.markSkillHit(target,crit,DamageCalc.Kind.PHYSICAL,0);target.hurt(target.damageSources().playerAttack(p),damage);DamageCalc.clearSkillHit(target);DamageCalc.applySkillLifesteal(p,damage,DamageCalc.Kind.PHYSICAL);return new DamageCalc.Roll(damage,crit);
    }
    private static void splash(ServerPlayer p,LivingEntity main,float damage,double radius){for(LivingEntity e:p.level().getEntitiesOfClass(LivingEntity.class,main.getBoundingBox().inflate(radius),e->e!=p&&e!=main&&e.isAlive()&&!ally(p,e)))DamageCalc.dealDamage(p,e,damage,DamageCalc.Kind.PHYSICAL);}

    private static Color consumeRound(ServerPlayer p,PlayerData data,State s,long now){Color c=s.ammo.removeFirst();if(s.ammo.isEmpty())startForcedReload(p,data,s,now);return c;}
    private static void refund(State s,Color c){if(s.ammo.size()<6)s.ammo.addLast(c);}
    private static void startForcedReload(ServerPlayer p,PlayerData data,State s,long now){if(s.forcedUntil>now)return;int ticks=forcedReloadTicks(p);s.forcedUntil=now+ticks;data.setBuff("shenli_forced_reload",ticks);p.level().playSound(null,p.blockPosition(),SoundEvents.CROSSBOW_LOADING_START.value(),SoundSource.PLAYERS,.7F,.9F);}
    private static void fillAmmo(State s,Color start,int count){s.ammo.clear();Color c=start;for(int i=0;i<count;i++){s.ammo.addLast(c);c=c.next();}}
    private static boolean canShoot(State s,long now){return s.forcedUntil<=now&&s.wStarted<=0&&s.wFinishAt<=now;}
    private static void spawnBullet(ServerPlayer p,State s,Bullet b){s.bullets.add(b);}
    private static boolean blocked(ServerLevel level,ServerPlayer p,Vec3 from,Vec3 to){return WindWallSystem.blocksProjectile(level,from,to)||com.myriad.character.skills.feiyu.FeiYuSkills.blocksProjectile(level,p,from,to)||level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS;}
    private static LivingEntity firstTarget(ServerPlayer p,Vec3 from,Vec3 to,double width){double heroPadding=.35;AABB box=new AABB(Math.min(from.x,to.x),Math.min(from.y,to.y),Math.min(from.z,to.z),Math.max(from.x,to.x),Math.max(from.y,to.y),Math.max(from.z,to.z)).inflate(width+heroPadding);LivingEntity best=null;double d=Double.MAX_VALUE;for(LivingEntity e:p.level().getEntitiesOfClass(LivingEntity.class,box,e->e!=p&&e.isAlive()&&!ally(p,e))){double padding=width+(e instanceof ServerPlayer?heroPadding:0);if(e.getBoundingBox().inflate(padding).clip(from,to).isEmpty())continue;double x=e.distanceToSqr(from);if(x<d){d=x;best=e;}}return best;}
    private static boolean ally(ServerPlayer p,LivingEntity e){if(com.myriad.game.JiarenManager.isFriendlyDummy(p,e))return true;if(!(e instanceof ServerPlayer other)){Faction a=GameManager.get()==null?null:GameManager.get().factionOf(p),b=com.myriad.game.LaneSystem.factionOf(e);return a!=null&&a==b;}GameManager gm=GameManager.get();return gm!=null&&gm.factionOf(p)!=null&&gm.factionOf(p)==gm.factionOf(other);}

    private static void updateModifiers(ServerPlayer p,State s,long now){var ms=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);var as=p.getAttribute(ModAttributes.ATTACK_SPEED);if(ms!=null){setModifier(ms,FORCED_SLOW,s.forcedUntil>now?-.15:0);setModifier(ms,W_SLOW,s.wStarted>0?-.10:0);setModifier(ms,AIM_SLOW,s.aiming?-.35:0);}if(as!=null){setModifier(as,MIND_AS,s.mindUntil>now?.12:0);setModifier(as,PRECISE_AS,s.preciseUntil>now?.20:0);setModifier(as,R_AS,s.rUntil>now?.25:0);}}
    private static void setModifier(net.minecraft.world.entity.ai.attributes.AttributeInstance attr,Identifier id,double value){attr.removeModifier(id);if(value!=0)attr.addTransientModifier(new AttributeModifier(id,value,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}
    private static void syncState(PlayerData data,State s,long now){
        data.setBuff("shenli_aiming",s.aiming?1:0);
        data.setBuff("shenli_ammo",s.ammo.size());int i=1;for(Color c:s.ammo)data.setBuff("shenli_ammo_"+(i++),c.ordinal()+1);while(i<=6)data.setBuff("shenli_ammo_"+(i++),0);
        data.setBuff("shenli_combo",s.combo);data.setBuff("shenli_forced_reload",s.forcedUntil>now?(int)(s.forcedUntil-now):0);data.setBuff("shenli_mind",s.mindUntil>now?(int)(s.mindUntil-now):0);
        data.setBuff("shenli_basic_windup",s.pendingBasic==null?0:Math.max(0,(int)(s.pendingBasic.releaseAt-now)));
        data.setBuff("shenli_basic_windup_total",s.pendingBasic==null?0:s.pendingBasic.totalTicks);
        data.setBuff("shenli_r",s.rUntil>now?(int)(s.rUntil-now):0);data.setBuff("shenli_fire_marks",s.fireMarks);data.setBuff("shenli_tristar",s.fireMarks==7?1:0);
        if(s.wStarted>0){int elapsed=(int)(now-s.wStarted),slot=Math.min(5,Math.max(0,elapsed/4)),phase=Math.floorMod(elapsed,4),window=s.mindUntil>now?2:1;data.setBuff("shenli_w_time",s.wFinishAt>now?(int)(s.wFinishAt-now):Math.max(1,24-elapsed));data.setBuff("shenli_w_color",s.wChosen!=null?s.wChosen.ordinal()+1:slot%3+1);data.setBuff("shenli_w_precise",Math.abs(phase-2)<=window?1:0);data.setBuff("shenli_w_pointer",Math.min(6,slot+1));}
        else{data.setBuff("shenli_w_time",0);data.setBuff("shenli_w_color",0);data.setBuff("shenli_w_precise",0);data.setBuff("shenli_w_pointer",0);}
    }
    private static void trail(ServerLevel l,Bullet b){
        DustParticleOptions c=colorParticle(b.color);int core=b.kind==ShotKind.Q?4:b.kind==ShotKind.E?3:2;
        l.sendParticles(c,b.pos.x,b.pos.y,b.pos.z,b.tristar?7:core,.025,.025,.025,0);
        if(b.kind==ShotKind.Q){double spin=b.age*.62;orientedRing(l,b.pos,b.dir,.16,c,8,spin);orientedRing(l,b.pos.subtract(b.dir.scale(.16)),b.dir,.27,b.tristar?CYAN:GOLD,10,-spin*.75);l.sendParticles(ParticleTypes.END_ROD,b.pos.x,b.pos.y,b.pos.z,2,.02,.02,.02,0);}
        else if(b.kind==ShotKind.E){orientedSquare(l,b.pos,b.dir,.25,c,3,b.age*.35);Vec3 wake=b.pos.subtract(b.dir.scale(.3));l.sendParticles(ParticleTypes.SMOKE,wake.x,wake.y,wake.z,2,.05,.05,.05,.005);}
        else if(b.color==Color.RED){Vec3 back=b.pos.subtract(b.dir.scale(.18));l.sendParticles(ParticleTypes.CRIT,back.x,back.y,back.z,2,.025,.025,.025,.01);}
        else if(b.color==Color.CYAN){helix(l,b.pos,b.dir,.12,CYAN,b.age*.7);l.sendParticles(ParticleTypes.END_ROD,b.pos.x,b.pos.y,b.pos.z,1,0,0,0,0);}
        else orientedSquare(l,b.pos,b.dir,.2,GOLD,2,b.age*.45);
        if(b.tristar){orientedRing(l,b.pos,b.dir,.34,CYAN,12,b.age*.7);Vec3 tail=b.pos.subtract(b.dir.scale(.4));l.sendParticles(GOLD,tail.x,tail.y,tail.z,4,.035,.035,.035,0);l.sendParticles(RED,b.pos.x,b.pos.y,b.pos.z,3,.02,.02,.02,0);}
    }
    private static void impact(ServerLevel l,LivingEntity e,Bullet b){Vec3 c=e.getBoundingBox().getCenter();DustParticleOptions color=colorParticle(b.color);l.sendParticles(color,c.x,c.y,c.z,b.tristar?34:b.kind==ShotKind.Q?20:14,.25,.25,.25,.025);if(b.kind==ShotKind.Q){for(int i=-3;i<=3;i++){Vec3 p=c.add(b.dir.scale(i*.22));l.sendParticles(color,p.x,p.y,p.z,1,0,0,0,0);}orientedRing(l,c,b.dir,.48,b.tristar?GOLD:color,16,b.age*.3);}else if(b.kind==ShotKind.E){orientedSquare(l,c,b.dir,.62,color,5,Math.PI/4);l.sendParticles(ParticleTypes.SMOKE,c.x,c.y,c.z,8,.3,.25,.3,.03);}if(b.tristar){orientedRing(l,c,b.dir,.7,RED,18,0);orientedRing(l,c,b.dir,.9,CYAN,20,.35);orientedSquare(l,c,b.dir,1.05,GOLD,6,.25);}}
    private static int basicRecoveryTicks(ServerPlayer p){double bonus=bonusAttackSpeed(p);return Math.max(2,20-(int)Math.floor(bonus/.10+1.0E-6));}
    private static int forcedReloadTicks(ServerPlayer p){double bonus=bonusAttackSpeed(p);return Math.max(10,46-(int)Math.floor(bonus/.13+1.0E-6)*2);}
    private static double bonusAttackSpeed(ServerPlayer p){PlayerData data=PlayerDataProvider.get(p);CharacterType type=data==null?null:CharacterType.byId(data.getCharacterId());double initial=type==null?.66:Math.max(.01,type.initialAs());return Math.max(0,p.getAttributeValue(ModAttributes.ATTACK_SPEED)/initial-1.0);}
    private static void muzzleVfx(ServerLevel l,Vec3 muzzle,Vec3 dir,Color color,ShotKind kind,boolean tri){DustParticleOptions c=colorParticle(color);double size=kind==ShotKind.Q?.52:kind==ShotKind.E?.66:.34;orientedRing(l,muzzle,dir,size,c,kind==ShotKind.BASIC?12:18,0);orientedSquare(l,muzzle.add(dir.scale(.08)),dir,size*.72,tri?GOLD:c,kind==ShotKind.BASIC?3:5,Math.PI/4);for(int i=0;i<(kind==ShotKind.E?7:4);i++){Vec3 p=muzzle.add(dir.scale(i*.12));l.sendParticles(c,p.x,p.y,p.z,2,.03,.03,.03,0);}if(tri){orientedRing(l,muzzle,dir,size*1.25,CYAN,20,.25);l.sendParticles(RED,muzzle.x,muzzle.y,muzzle.z,12,.16,.16,.16,.02);}}
    private static void recoilBurst(ServerLevel l,Vec3 center,Vec3 shotDir,Color color){Vec3 back=shotDir.normalize().scale(-1);for(int i=1;i<=7;i++){Vec3 p=center.add(back.scale(i*.16));l.sendParticles(colorParticle(color),p.x,p.y,p.z,2,.06,.06,.06,.01);l.sendParticles(ParticleTypes.SMOKE,p.x,p.y,p.z,2,.08,.08,.08,.018);}orientedRing(l,center,shotDir,.8,colorParticle(color),20,0);}
    private static void reloadStartVfx(ServerLevel l,ServerPlayer p){Vec3 center=weaponWheelCenter(p);orientedRing(l,center,p.getLookAngle(),.66,GOLD,24,0);orientedRing(l,center,p.getLookAngle(),.38,CYAN,18,.3);l.sendParticles(ParticleTypes.COPPER_FIRE_FLAME,center.x,center.y,center.z,12,.25,.25,.25,.025);}
    private static void reloadTickVfx(ServerLevel l,ServerPlayer p,State s,long now){Vec3 dir=p.getLookAngle().normalize();Vec3 center=weaponWheelCenter(p);Vec3[] q=basis(dir);long elapsed=now-s.wStarted;double rotation=elapsed*.22;int pointer=Math.min(5,Math.max(0,(int)elapsed/4));int phase=Math.floorMod((int)elapsed,4),window=s.mindUntil>now?2:1;boolean precise=Math.abs(phase-2)<=window;for(int i=0;i<6;i++){double a=rotation+i*Math.PI/3;Vec3 pos=center.add(q[0].scale(Math.cos(a)*.48)).add(q[1].scale(Math.sin(a)*.48));DustParticleOptions c=colorParticle(Color.values()[i%3]);l.sendParticles(c,pos.x,pos.y,pos.z,i==pointer?3:1,i==pointer?.025:0,i==pointer?.025:0,i==pointer?.025:0,0);}orientedRing(l,center,dir,.58,precise?GOLD:CYAN,18,-rotation*.5);}
    private static void reloadLockVfx(ServerLevel l,ServerPlayer p,Color color,boolean precise){Vec3 center=weaponWheelCenter(p);DustParticleOptions c=colorParticle(color);orientedRing(l,center,p.getLookAngle(),precise?.95:.68,precise?GOLD:c,precise?30:20,0);orientedSquare(l,center,p.getLookAngle(),precise?.7:.5,c,precise?7:5,Math.PI/4);l.sendParticles(precise?ParticleTypes.END_ROD:ParticleTypes.CRIT,center.x,center.y,center.z,precise?22:10,.28,.28,.28,.03);}
    private static void ultimateStartVfx(ServerLevel l,ServerPlayer p){Vec3 center=weaponWheelCenter(p);orientedRing(l,center,p.getLookAngle(),1.25,GOLD,36,0);orientedRing(l,center,p.getLookAngle(),.88,CYAN,30,.3);orientedSquare(l,center,p.getLookAngle(),.72,RED,8,Math.PI/4);l.sendParticles(ParticleTypes.END_ROD,center.x,center.y,center.z,32,.65,.65,.65,.04);}
    private static void ultimateTickVfx(ServerLevel l,ServerPlayer p,State s,long now){if((now&1)!=0)return;Vec3 dir=p.getLookAngle().normalize(),center=weaponWheelCenter(p);Vec3[] q=basis(dir);double rotation=now*.17;for(int i=0;i<6;i++){double a=rotation+i*Math.PI/3;Vec3 pos=center.add(q[0].scale(Math.cos(a)*.55)).add(q[1].scale(Math.sin(a)*.55));Color c=Color.values()[i%3];l.sendParticles(colorParticle(c),pos.x,pos.y,pos.z,2,.015,.015,.015,0);}orientedRing(l,center,dir,.68,GOLD,18,-rotation*.4);if(s.fireMarks!=0){for(int i=0;i<3;i++)if((s.fireMarks&(1<<i))!=0){double a=rotation+i*Math.PI*2/3;Vec3 pos=center.add(q[0].scale(Math.cos(a)*.88)).add(q[1].scale(Math.sin(a)*.88));l.sendParticles(colorParticle(Color.values()[i]),pos.x,pos.y,pos.z,2,.02,.02,.02,0);}}}
    private static void refundVfx(ServerLevel l,ServerPlayer p,Color color){Vec3 center=weaponWheelCenter(p);DustParticleOptions c=colorParticle(color);orientedRing(l,center,p.getLookAngle(),.42,c,14,0);l.sendParticles(c,center.x,center.y,center.z,8,.16,.16,.16,.015);}
    private static Vec3 weaponWheelCenter(ServerPlayer p){Vec3 dir=p.getLookAngle().normalize();Vec3[] q=basis(dir);return p.getEyePosition().add(dir.scale(.38)).add(q[0].scale(.34)).subtract(q[1].scale(.22));}
    private static Vec3[] basis(Vec3 direction){Vec3 dir=direction.lengthSqr()<1.0E-6?new Vec3(0,0,1):direction.normalize();Vec3 right=dir.cross(new Vec3(0,1,0));if(right.lengthSqr()<1.0E-6)right=new Vec3(1,0,0);else right=right.normalize();Vec3 up=right.cross(dir).normalize();return new Vec3[]{right,up};}
    private static void orientedRing(ServerLevel l,Vec3 c,Vec3 direction,double r,DustParticleOptions particle,int points,double offset){Vec3[] q=basis(direction);for(int i=0;i<points;i++){double a=Math.PI*2*i/points+offset;Vec3 p=c.add(q[0].scale(Math.cos(a)*r)).add(q[1].scale(Math.sin(a)*r));l.sendParticles(particle,p.x,p.y,p.z,1,0,0,0,0);}}
    private static void orientedRingForOthers(ServerPlayer owner,Vec3 c,Vec3 direction,double r,DustParticleOptions particle,int points,double offset){Vec3[] q=basis(direction);for(int i=0;i<points;i++){double a=Math.PI*2*i/points+offset;Vec3 p=c.add(q[0].scale(Math.cos(a)*r)).add(q[1].scale(Math.sin(a)*r));particleForOthers(owner,p,particle,1,0);}}
    private static void particleForOthers(ServerPlayer owner,Vec3 pos,DustParticleOptions particle,int count,double spread){for(ServerPlayer viewer:owner.level().players())if(viewer!=owner&&com.myriad.game.VisionSystem.canSeePosition(viewer,pos))owner.level().sendParticles(viewer,particle,false,false,pos.x,pos.y,pos.z,count,spread,spread,spread,0);}
    private static void orientedSquare(ServerLevel l,Vec3 c,Vec3 direction,double r,DustParticleOptions particle,int pointsPerEdge,double offset){Vec3[] q=basis(direction);double co=Math.cos(offset),si=Math.sin(offset);Vec3 a=q[0].scale(co).add(q[1].scale(si)),b=q[1].scale(co).subtract(q[0].scale(si));Vec3[] corners={c.add(a.scale(r)).add(b.scale(r)),c.subtract(a.scale(r)).add(b.scale(r)),c.subtract(a.scale(r)).subtract(b.scale(r)),c.add(a.scale(r)).subtract(b.scale(r))};for(int edge=0;edge<4;edge++)for(int i=0;i<pointsPerEdge;i++){Vec3 p=corners[edge].lerp(corners[(edge+1)%4],i/(double)pointsPerEdge);l.sendParticles(particle,p.x,p.y,p.z,1,0,0,0,0);}}
    private static void helix(ServerLevel l,Vec3 c,Vec3 direction,double r,DustParticleOptions particle,double angle){Vec3[] q=basis(direction);for(int side=0;side<2;side++){double a=angle+side*Math.PI;Vec3 p=c.add(q[0].scale(Math.cos(a)*r)).add(q[1].scale(Math.sin(a)*r));l.sendParticles(particle,p.x,p.y,p.z,1,0,0,0,0);}}
    private static void ring(ServerLevel l,Vec3 c,double r,DustParticleOptions p,int points){for(int i=0;i<points;i++){double a=Math.PI*2*i/points;l.sendParticles(p,c.x+Math.cos(a)*r,c.y,c.z+Math.sin(a)*r,1,0,0,0,0);}}
    private static DustParticleOptions colorParticle(Color c){return c==Color.RED?RED:c==Color.CYAN?CYAN:GOLD;}
    private static void fireSound(ServerPlayer p,Color c){p.level().playSound(null,p.blockPosition(),SoundEvents.FIREWORK_ROCKET_BLAST,SoundSource.PLAYERS,.55F,c==Color.RED?.8F:c==Color.CYAN?1.25F:1F);}
    private static float ad(ServerPlayer p){return(float)p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);}
    private static Vec3 horizontal(Vec3 v){Vec3 h=new Vec3(v.x,0,v.z);return h.lengthSqr()<1e-6?new Vec3(0,0,1):h.normalize();}
    private static int reducedCooldown(ServerPlayer p,int base){double cdr=Mth.clamp(p.getAttributeValue(ModAttributes.COOLDOWN_REDUCTION),0,60);return Math.max(1,(int)Math.ceil(base*(1-cdr/100.0)));}
    private static void cooldownMessage(ServerPlayer p,PlayerData d,Skill s){p.sendSystemMessage(Component.translatable("message.myriad.skill_cd",(d.getCooldown(s.getId())+19)/20));}
    private static void message(ServerPlayer p,String key){p.sendSystemMessage(Component.translatable(key));}
    private static State state(ServerPlayer p){return STATES.computeIfAbsent(p.getUUID(),id->{State s=new State();fillAmmo(s,Color.RED,6);return s;});}
    public static void clearFor(ServerPlayer p){PlayerData data=PlayerDataProvider.get(p);if(data!=null){data.setBuff("shenli_aiming",0);data.setBuff("shenli_basic_windup",0);data.setBuff("shenli_basic_windup_total",0);}State s=STATES.remove(p.getUUID());if(s!=null){updateModifiers(p,new State(),Long.MAX_VALUE);for(UUID id:s.redDebuffs.keySet())if(p.level().getEntity(id) instanceof LivingEntity e){var a=e.getAttribute(ModAttributes.PHYSICAL_DEFENSE);if(a!=null)a.removeModifier(redDebuffId(p));}for(UUID id:s.slows.keySet())if(p.level().getEntity(id) instanceof LivingEntity e){var a=e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(a!=null)a.removeModifier(slowDebuffId(p));}}BASIC_CONTEXTS.entrySet().removeIf(e->e.getValue().owner.equals(p.getUUID()));}
    public static void clearFor(UUID id){STATES.remove(id);BASIC_CONTEXTS.entrySet().removeIf(e->e.getValue().owner.equals(id));}

    private static final class State{final ArrayDeque<Color> ammo=new ArrayDeque<>();final List<Bullet> bullets=new ArrayList<>();final Map<UUID,Long> redDebuffs=new HashMap<>(),slows=new HashMap<>();long forcedUntil,wStarted,wFinishAt,mindUntil,preciseUntil,rUntil,lastHeroHit;long lastAimToggle=Long.MIN_VALUE;boolean aiming,wNoCd,wNoMana,wPrecise;Color wChosen;int combo,fireMarks;Dash dash;PendingBasic pendingBasic;}
    private static final class Bullet{final ShotKind kind;final Color color;Vec3 pos;final Vec3 dir;final double speed,range,width;final int level;final float ad;final boolean tristar;float basicMultiplier=1F;double traveled;int age;Bullet(ShotKind kind,Color color,Vec3 pos,Vec3 dir,double speed,double range,double width,int level,float ad,boolean tristar){this.kind=kind;this.color=color;this.pos=pos;this.dir=dir;this.speed=speed;this.range=range;this.width=width;this.level=level;this.ad=ad;this.tristar=tristar;}}
    private static final class Dash{final Vec3 step;int ticks;Dash(Vec3 step,int ticks){this.step=step;this.ticks=ticks;}}
    private record PendingBasic(long releaseAt,int totalTicks){}
    private record BasicContext(UUID owner,Color color){}
}
