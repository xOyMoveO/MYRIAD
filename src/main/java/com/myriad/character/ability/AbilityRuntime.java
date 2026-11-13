package com.myriad.character.ability;

import com.myriad.character.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.*;
import com.myriad.game.*;
import com.myriad.network.NetworkSetup;
import com.myriad.player.*;
import com.myriad.registry.ModAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.function.*;

/** Shared lifecycle, targeting, swept projectiles and motion. No role-specific effect switch. */
public final class AbilityRuntime {
    private AbilityRuntime(){}
    private static final Map<UUID,Owner> OWNERS=new HashMap<>();
    private static final List<Debuff> DEBUFFS=new ArrayList<>();
    private static final java.util.concurrent.atomic.AtomicInteger VISUAL_SEQUENCE=new java.util.concurrent.atomic.AtomicInteger();
    private record Source(String skill,boolean direct,int cast){}
    private static final ThreadLocal<Source> SOURCE=new ThreadLocal<>();
    public static String sourceSkill(){var s=SOURCE.get();return s==null?"":s.skill;}
    public static boolean sourceDirect(){var s=SOURCE.get();return s==null||s.direct;}
    public static int sourceCast(){var s=SOURCE.get();return s==null?0:s.cast;}
    private interface Task {boolean tick(long now);default void cancel(){}}
    private static final class Owner {
        final ServerPlayer player;final String role;final ServerLevel level;
        final List<Task> tasks=new ArrayList<>();final Map<String,Memory> memory=new HashMap<>();
        Cast preparing,winding;
        int pendingWindup;String pending="";long pendingUntil,channelUntil,motionUntil,lastCast=-100,channelImpulse;int channelToken,motionToken,castToken;Vec3 channelOrigin;
        Owner(ServerPlayer p){player=p;role=PlayerDataProvider.get(p).getCharacterId();level=p.level();}
    }
    /** Skill-private scratch space. Removing one skill never removes another skill's state. */
    public static final class Memory {
        public long until,cooldown,last;public int count;public double distance;
        public UUID target;public String tag="";public Vec3 point,lastPosition;
        public final Map<UUID,Long> targets=new HashMap<>();
    }
    private record Debuff(LivingEntity target,Identifier id,long until){}
    private static Owner owner(ServerPlayer p){
        Owner o=OWNERS.get(p.getUUID());String role=PlayerDataProvider.get(p).getCharacterId();
        // First use creates runtime bookkeeping only: it must not cleanse the player's CC.
        if(o==null){o=new Owner(p);OWNERS.put(p.getUUID(),o);}
        else if(o.player!=p||o.level!=p.level()||!Objects.equals(o.role,role)){clear(p);o=new Owner(p);OWNERS.put(p.getUUID(),o);}return o;
    }
    public static void clear(ServerPlayer p){AbilityVisuals.cancel(p);
        com.myriad.combat.MagicBullet.clearFor(p.getUUID());
        if(p.connection!=null)PacketDistributor.sendToPlayer(p,new NetworkSetup.MagicBulletCooldownPayload(0,1));
        com.myriad.character.skills.shuo.ShuoCombat.clear(p);
        com.myriad.character.skills.yike.YikeRework.clear(p);
        com.myriad.character.skills.fenjue.rework.FlameHitVisuals.clear(p);
        SkillChargeSystem.clear(p);
        com.myriad.character.skills.keheng.KehengTimeFields.clear(p);
        ActionTime.clearOwner(p.getUUID());
        Owner old=OWNERS.remove(p.getUUID());
        if(old!=null){if(old.preparing!=null)old.preparing.cancelCast();
            for(var task:new ArrayList<>(old.tasks))task.cancel();old.tasks.clear();old.memory.clear();p.removeEffect(MobEffects.INVISIBILITY);
            for(var debuff:new ArrayList<>(DEBUFFS))if(FirstGroupSkills.candidates(old.role).stream()
                    .anyMatch(skill->debuff.id.getPath().equals("ability_slow_"+skill.getId()+"_"+p.getUUID()))){
                var attr=debuff.target.getAttribute(Attributes.MOVEMENT_SPEED);if(attr!=null)attr.removeModifier(debuff.id);DEBUFFS.remove(debuff);
            }
            if(old.motionUntil>p.level().getGameTime())p.setDeltaMovement(Vec3.ZERO);
            com.myriad.combat.WindWallSystem.clearFor(p.getUUID());
            PacketDistributor.sendToPlayer(p,new NetworkSetup.DashPayload(0,0,0,-1));}
        var d=PlayerDataProvider.get(p);
        for(String key:new ArrayList<>(d.getBuffs().keySet()))if((key.startsWith("g1_")&&!key.startsWith("g1_attribute_"))||key.startsWith("g2_"))d.setBuff(key,0);
        PlayerDataProvider.get(p).setPlacementType(0);
    }
    public static void clearAll(){for(var owner:new ArrayList<>(OWNERS.values()))clear(owner.player);
        OWNERS.clear();ActionTime.clearAll();for(var d:DEBUFFS){var a=d.target.getAttribute(Attributes.MOVEMENT_SPEED);if(a!=null)a.removeModifier(d.id);}DEBUFFS.clear();}
    public static void cleanup(){
        for(var debuff:new ArrayList<>(DEBUFFS))if(!ModState.isEnabled()||!debuff.target.isAlive()||debuff.target.isRemoved()
                ||debuff.target.level().getGameTime()>=debuff.until){
            var a=debuff.target.getAttribute(Attributes.MOVEMENT_SPEED);if(a!=null)a.removeModifier(debuff.id);DEBUFFS.remove(debuff);
        }
        for(var owner:new ArrayList<>(OWNERS.values()))if(!valid(owner.player)||owner.level!=owner.player.level()||!Objects.equals(PlayerDataProvider.get(owner.player).getCharacterId(),owner.role))clear(owner.player);
    }
    public static boolean valid(ServerPlayer p){
        return ModState.isEnabled()&&p.isAlive()&&!p.isRemoved()&&!p.isSpectator()
                &&(TrainingMode.isEnabled()||GameManager.get()!=null&&GameManager.get().getPhase()==GameManager.Phase.PLAYING);
    }
    /** Independent flights consume both explicit action sources and current spatial fields. */
    public static double projectileRate(ServerPlayer p,UUID flight,Vec3 position,long now){
        return Math.min(ActionTime.multiplier(flight,now),com.myriad.character.skills.keheng.KehengTimeFields.projectileRate(p,position,now));
    }
    public static Cast context(ServerPlayer p,AbilitySkill skill){return new Cast(p,PlayerDataProvider.get(p),skill,owner(p));}
    private static AbilitySkill passive(ServerPlayer p){var d=PlayerDataProvider.get(p);if(!FirstGroupSkills.active(d.getCharacterId()))return null;
        var n=FirstGroupSkills.selected(d.getCharacterId(),0,d.getSkillLevels());return n!=null&&d.skillLevel(n.getId())>0?n:null;}
    public static void request(ServerPlayer p,PlayerData d,AbilitySkill skill){
        request(p,d,skill,skill.commitWindupTicks());
    }
    /** Server-owned delayed-payment policy. The ordinary input entry remains immediate.
     * No client packet supplies this duration; future skill modules may select it explicitly. */
    public static Cast request(ServerPlayer p,PlayerData d,AbilitySkill skill,int windupTicks){
        if(windupTicks<0)throw new IllegalArgumentException("Negative windup");
        if(!valid(p)||!skill.role().equals(d.getCharacterId())||skill.isPassive())return null;
        var selected=FirstGroupSkills.resolve(CharacterType.byId(d.getCharacterId()),skill.slot(),d.getSkillLevels());
        if(selected!=skill)return null;
        if(!skill.canCastWhileControlled()&&(d.getBuff("g1_stun")>0||com.myriad.combat.SilenceSystem.isSilenced(p)))return null;
        if(!TrainingMode.isEnabled()&&d.skillLevel(skill.getId())<=0){message(p,"message.myriad.skill_not_learned");return null;}
        Cast c=context(p,skill);Owner o=c.owner;
        if(com.myriad.character.skills.shuo.ShuoCombat.blocked(p))return null;
        if(skill.recastAvailable(c)&&(!SkillChargeSystem.active(p)||skill.recastDuringChannel())){
            if((d.getBuff("g1_root")>0&&!skill.recastWhileRooted()||moving(p))&&!skill.role().equals("keheng"))return null;
            skill.recast(c);SkillPresentation.cast(p,skill);PlayerDataProvider.syncToClient(p);return null;}
        if(o.preparing!=null&&skill.interruptPreparation(c))o.preparing.cancelCast();
        if(SkillChargeSystem.active(p))return null;
        boolean controlBreak=skill.canCastWhileControlled()&&controlled(p);
        if(o.preparing!=null||o.channelUntil>c.now()||moving(p)&&!controlBreak||c.now()-o.lastCast<skill.inputRecoveryTicks()&&!controlBreak)return null;
        boolean noCd=TrainingMode.isEnabled()&&TrainingMode.isNoCooldown(),noMana=TrainingMode.isEnabled()&&TrainingMode.isNoMana();
        if(!noCd&&!skill.cooldownReady(c)){message(p,"message.myriad.g1.not_ready");return null;}
        if(!noMana&&d.getMana()<skill.getManaCost()){message(p,"message.myriad.not_enough_mana");return null;}
        if(skill.needsPoint()){
            o.pending=skill.getId();o.pendingWindup=windupTicks;o.pendingUntil=c.now()+200;d.setPlacementType(100+skill.index());PlayerDataProvider.syncToClient(p);return null;
        }
        begin(c,windupTicks);
        return c;
    }
    public static void confirm(ServerPlayer p){
        var o=owner(p);var skill=FirstGroupSkills.byId(o.pending);if(skill==null)return;
        var d=PlayerDataProvider.get(p);if(!valid(p)||!skill.role().equals(d.getCharacterId())){cancel(p);return;}
        if(FirstGroupSkills.resolve(CharacterType.byId(d.getCharacterId()),skill.slot(),d.getSkillLevels())!=skill){cancel(p);return;}
        if(!TrainingMode.isEnabled()&&d.skillLevel(skill.getId())<=0){cancel(p);return;}
        Cast c=context(p,skill);
        if(o.preparing!=null&&skill.interruptPreparation(c))o.preparing.cancelCast();
        if(d.getBuff("g1_stun")>0||com.myriad.combat.SilenceSystem.isSilenced(p)
                ||moving(p)||o.channelUntil>p.level().getGameTime()||p.level().getGameTime()-o.lastCast<skill.inputRecoveryTicks())return;
        boolean noCd=TrainingMode.isEnabled()&&TrainingMode.isNoCooldown(),noMana=TrainingMode.isEnabled()&&TrainingMode.isNoMana();
        if(!noCd&&!skill.cooldownReady(c)||!noMana&&d.getMana()<skill.getManaCost())return;
        c.point=c.point(skill.pointRange(c));if(c.point==null){message(p,"message.myriad.g1.invalid_point");return;}
        begin(c,o.pendingWindup);
    }
    public static void cancel(ServerPlayer p){var o=owner(p);if(o.preparing!=null)o.preparing.cancelCast();if(o.winding!=null)o.winding.cancelCast();o.pending="";PlayerDataProvider.get(p).setPlacementType(0);PlayerDataProvider.syncToClient(p);}
    private static void begin(Cast c,int windupTicks){
        if(!c.skill.canRelease(c)){message(c.player,"message.myriad.no_skill_target");c.phase=Phase.CANCELLED;return;}
        c.owner.pending="";c.owner.lastCast=c.now();c.castToken=++c.owner.castToken;c.data.setPlacementType(0);
        c.accepted=true;c.owner.preparing=c;
        c.skill.preparing(c);
        if(windupTicks>0)c.windup(windupTicks,c.skill.windupWalkFactor(c),c::commit);
        else c.commit();
    }
    public enum Phase { REQUESTED, WINDUP, COMMITTED, COMPLETED, CANCELLED }
    private static void message(ServerPlayer p,String key){p.sendSystemMessage(Component.translatable(key));}
    public static void tick(ServerPlayer p){
        if(!valid(p)||!FirstGroupSkills.active(PlayerDataProvider.get(p).getCharacterId())){if(OWNERS.containsKey(p.getUUID()))clear(p);return;}
        com.myriad.character.skills.keheng.KehengTimeFields.refreshActor(p);
        Owner o=owner(p);long now=p.level().getGameTime();var d=PlayerDataProvider.get(p);
        if(o.channelUntil>=now&&o.channelUntil>0&&(d.getBuff("g1_stun")>0
                ||com.myriad.combat.SilenceSystem.isSilenced(p)
                ||o.channelOrigin!=null&&p.position().subtract(o.channelOrigin).multiply(1,0,1).lengthSqr()>.64
                ||KnockbackBuffer.recentlyForced(p)&&KnockbackBuffer.impulseToken(p)!=o.channelImpulse)){
            o.channelToken++;o.channelUntil=0;d.setBuff("g1_channel",0);
            if(o.preparing!=null)o.preparing.cancelCast();
            SkillChargeSystem.clear(p);
        }
        for(Task task:new ArrayList<>(o.tasks)){
            if(OWNERS.get(p.getUUID())!=o)break;
            if(o.tasks.contains(task)&&!task.tick(now))o.tasks.remove(task);
        }
        com.myriad.character.skills.shuo.ShuoCombat.tick(p);
        com.myriad.character.skills.yike.YikeRework.tick(p);
        var passive=passive(p);if(passive!=null)passive.tickPassive(context(p,passive));
        if(!o.pending.isEmpty()&&now>o.pendingUntil){cancel(p);}
        if(!o.pending.isEmpty()&&now%4==0){var skill=FirstGroupSkills.byId(o.pending);if(skill!=null){Cast c=context(p,skill);Vec3 point=c.point(skill.pointRange(c));
            c.point=point;if(point!=null&&!skill.canRelease(c))point=null;
            c.ring(p.position(),skill.pointRange(c),true);
            if(point!=null){c.ring(point,skill.previewRadius(c),true);AbilityVisuals.reticle(c,point,skill.previewRadius(c));}
            else {Vec3 candidate=AbilityTargeting.ground(p,skill.pointRange(c),false);
                if(candidate!=null)AbilityVisuals.invalidReticle(c,candidate);}}}
        if(now%10==0){for(var debuff:new ArrayList<>(DEBUFFS))if(!debuff.target.isAlive()||debuff.target.level()!=p.level()&&debuff.target.isRemoved()||debuff.target.level().getGameTime()>=debuff.until){
            var a=debuff.target.getAttribute(Attributes.MOVEMENT_SPEED);if(a!=null)a.removeModifier(debuff.id);DEBUFFS.remove(debuff);}}
    }
    public static double ordinarySlowRate(LivingEntity t){
        var a=t.getAttribute(Attributes.MOVEMENT_SPEED);if(a==null)return 1;
        double rate=1;
        for(var modifier:a.getModifiers())if(modifier.amount()<0&&modifier.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                &&(modifier.id().getPath().startsWith("ability_slow_")||modifier.id().getPath().equals("effect.slowness")))rate*=1+modifier.amount();
        return Math.max(.0001,rate);
    }
    public static Cast winding(ServerPlayer p){var o=OWNERS.get(p.getUUID());return o==null?null:o.winding;}
    public static String preparingSkill(ServerPlayer p){var o=OWNERS.get(p.getUUID());return o==null||o.preparing==null?"":o.preparing.skill.getId();}
    public static boolean channeling(ServerPlayer p){var o=OWNERS.get(p.getUUID());return o!=null&&o.channelUntil>p.level().getGameTime();}
    public static boolean moving(ServerPlayer p){var o=OWNERS.get(p.getUUID());return o!=null&&o.motionUntil>p.level().getGameTime();}
    /** Control only; damage-over-time and unrelated negative/positive attributes are not cleansed. */
    public static boolean controlled(ServerPlayer p){
        var d=PlayerDataProvider.get(p);
        if(d.getBuff("g1_stun")>0||d.getBuff("g1_root")>0
                ||com.myriad.combat.SilenceSystem.isSilenced(p)
                ||KnockbackBuffer.recentlyForced(p))return true;
        for(var effect:List.of(MobEffects.SLOWNESS,MobEffects.LEVITATION,MobEffects.BLINDNESS,MobEffects.DARKNESS,MobEffects.MINING_FATIGUE))
            if(p.hasEffect(effect))return true;
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed!=null&&DEBUFFS.stream().anyMatch(down->down.target==p&&speed.getModifier(down.id)!=null&&speed.getModifier(down.id).amount()<0);
    }
    /** One-time cleanse, not future control immunity; invoked only after normal cast validation/payment. */
    public static void cleanseControls(ServerPlayer p){
        boolean stop=controlled(p);
        com.myriad.character.skills.keheng.KehengTimeFields.cleanse(p);
        var d=PlayerDataProvider.get(p);d.setBuff("g1_stun",0);d.setBuff("g1_root",0);
        for(var effect:List.of(MobEffects.SLOWNESS,MobEffects.LEVITATION,MobEffects.BLINDNESS,MobEffects.DARKNESS,MobEffects.MINING_FATIGUE))p.removeEffect(effect);
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
        for(var down:new ArrayList<>(DEBUFFS))if(down.target==p&&speed!=null){
            var modifier=speed.getModifier(down.id);
            if(modifier!=null&&modifier.amount()<0){speed.removeModifier(down.id);DEBUFFS.remove(down);}
        }
        com.myriad.combat.SilenceSystem.cleanse(p);
        com.myriad.combat.FrozenControlSystem.cleanse(p.getUUID());
        if(stop){
            var o=OWNERS.get(p.getUUID());if(o!=null){o.motionToken++;o.motionUntil=0;}
            // Stop a currently forced trajectory on both sides, while retaining downward gravity.
            Vec3 v=new Vec3(0,Math.min(0,p.getDeltaMovement().y),0);p.setDeltaMovement(v);p.hurtMarked=true;
            PacketDistributor.sendToPlayer(p,new NetworkSetup.DashPayload((float)v.x,(float)v.y,(float)v.z,-1));
            PacketDistributor.sendToPlayer(p,new NetworkSetup.StarPullMotionPayload(0,0,0,0,0));
        }
    }
    public record Hit(ServerPlayer player,LivingEntity target,float amount,boolean basic,String skillId,boolean direct,DamageCalc.Kind kind){
        public Cast passiveContext(){var passive=AbilityRuntime.passive(player);return passive==null?null:context(player,passive);}
    }
    public static double modifyDamage(ServerPlayer p,LivingEntity target,float damage,boolean basic,String source,boolean direct,DamageCalc.Kind kind){
        if(!direct||kind==DamageCalc.Kind.TRUE||!SkillTargets.enemy(p,target))return 1;
        var d=PlayerDataProvider.get(p);if(!FirstGroupSkills.active(d.getCharacterId()))return 1;
        var hit=new Hit(p,target,damage,basic,source,direct,kind);double scale=1;
        var passive=passive(p);if(passive!=null)scale*=passive.modifyDamage(hit);
        for(int slot=0;slot<4;slot++)if(FirstGroupSkills.resolve(CharacterType.byId(d.getCharacterId()),slot,d.getSkillLevels()) instanceof AbilitySkill skill
                &&d.skillLevel(skill.getId())>0)scale*=skill.modifyDamage(hit);
        return scale;
    }
    public static void hit(ServerPlayer p,LivingEntity target,float damage,boolean basic,String source,boolean direct,int kind){
        // Post-hit runs after health deduction. A lethal hit still earns attack resources,
        // while damage(target,...) independently refuses extra damage to an already dead unit.
        if(!valid(p)||damage<=0||!direct||SkillTargets.ally(p,target)||target instanceof DefenseTowerEntity)return;
        if(basic){p.removeEffect(MobEffects.INVISIBILITY);PlayerDataProvider.get(p).setStealthBonus(false);}
        var passive=passive(p);if(passive!=null)passive.onHit(new Hit(p,target,damage,basic,source,direct,
                kind==2?DamageCalc.Kind.TRUE:kind==1?DamageCalc.Kind.MAGIC:DamageCalc.Kind.PHYSICAL));
        var data=PlayerDataProvider.get(p);
        if(FirstGroupSkills.active(data.getCharacterId()))for(int slot=0;slot<4;slot++){
            var active=FirstGroupSkills.resolve(CharacterType.byId(data.getCharacterId()),slot,data.getSkillLevels());
            if(active instanceof AbilitySkill a&&data.skillLevel(a.getId())>0)a.onHit(new Hit(p,target,damage,basic,source,direct,
                    kind==2?DamageCalc.Kind.TRUE:kind==1?DamageCalc.Kind.MAGIC:DamageCalc.Kind.PHYSICAL));
        }
    }
    public static double incoming(ServerPlayer p,DamageCalc.Kind kind){
        if(kind==DamageCalc.Kind.TRUE)return 1;var d=PlayerDataProvider.get(p);
        double scale=d.getBuff("g1_guard15")>0?.85:d.getBuff("g1_guard12")>0?.88:1;
        if(FirstGroupSkills.active(d.getCharacterId()))for(var skill:FirstGroupSkills.candidates(d.getCharacterId()))
            if(d.skillLevel(skill.getId())>0)scale*=skill.incomingMultiplier(context(p,skill));
        return Math.clamp(scale,.3,1);
    }
    public static final class Cast {
        public final ServerPlayer player;public final PlayerData data;public final AbilitySkill skill;public final int rank;public final float strength;
        private final Owner owner;private int castToken;public Vec3 point;
        private float windupProgress;
        private Phase phase=Phase.REQUESTED;private boolean accepted,committed;private int windupToken;
        public Phase phase(){return phase;}
        public boolean accepted(){return accepted;}
        public float windupProgress(){return windupProgress;}
        public boolean committed(){return committed;}
        /** Revalidate at the actual payment boundary; rejected/cancelled handles cannot be replayed. */
        public boolean commit(){
            if(!accepted||committed||phase==Phase.WINDUP||phase==Phase.CANCELLED||phase==Phase.COMPLETED)return false;
            boolean noCd=TrainingMode.isEnabled()&&TrainingMode.isNoCooldown();
            boolean noMana=TrainingMode.isEnabled()&&TrainingMode.isNoMana();
            if(OWNERS.get(player.getUUID())!=owner||owner.preparing!=this||owner.level!=player.level()
                    ||!valid(player)||!skill.role().equals(data.getCharacterId())
                    ||FirstGroupSkills.resolve(CharacterType.byId(data.getCharacterId()),skill.slot(),data.getSkillLevels())!=skill
                    ||!TrainingMode.isEnabled()&&data.skillLevel(skill.getId())<=0
                    ||!skill.canCastWhileControlled()&&(data.getBuff("g1_stun")>0||SilenceSystem.isSilenced(player))
                    ||!noCd&&!skill.cooldownReady(this)||!noMana&&data.getMana()<skill.getManaCost()
                    ||skill.needsPoint()&&!AbilityTargeting.legalGround(player,point,skill.pointRange(this),skill.requiresStandingPoint())
                    ||!skill.canRelease(this)){cancelCast();return false;}
            committed=true;phase=Phase.COMMITTED;owner.preparing=null;
            if(!noMana)data.spendMana(skill.getManaCost());
            if(!noCd&&skill.cooldownTicks(this)>0){double reduction=Math.clamp(player.getAttributeValue(ModAttributes.COOLDOWN_REDUCTION),0,60);
                data.setCooldown(skill.getId(),Math.max(1,(int)(skill.cooldownTicks(this)*(1-reduction/100))));}
            player.removeEffect(MobEffects.INVISIBILITY);data.setStealthBonus(false);
            skill.release(this);skill.present(this);SkillPresentation.cast(player,skill);
            AbilitySkill passive=passive(player);if(passive!=null)passive.onCast(this);
            if(phase==Phase.COMMITTED)phase=Phase.COMPLETED;
            PlayerDataProvider.syncToClient(player);return true;
        }
        public void cancelCast(){
            if(phase==Phase.CANCELLED||phase==Phase.COMPLETED)return;
            phase=Phase.CANCELLED;if(owner.winding==this)owner.winding=null;
            if(owner.preparing==this||windupToken!=0&&owner.channelToken==windupToken){if(owner.preparing==this)owner.preparing=null;owner.channelToken++;owner.channelUntil=0;
                data.setBuff("g1_channel",0);SkillChargeSystem.clear(player);}
        }
        private Cast(ServerPlayer p,PlayerData data,AbilitySkill skill,Owner owner){this.player=p;this.data=data;this.skill=skill;this.owner=owner;
            rank=Math.clamp(Math.max(1,data.skillLevel(skill.getId())),1,skill.maxRank());strength=(float)p.getAttributeValue(ModAttributes.SPELL_POWER);castToken=owner.castToken;}
        public long now(){return player.level().getGameTime();}
        public int castId(){return castToken;}
        public Vec3 forward(double distance){Vec3 flat=player.getLookAngle().multiply(1,0,1).normalize();
            return (flat.lengthSqr()<.01?Vec3.directionFromRotation(0,player.getYRot()):flat).scale(distance);}
        public Memory memory(){return owner.memory.computeIfAbsent(skill.getId(),id->new Memory());}
        public Memory memory(String key){return owner.memory.computeIfAbsent(skill.getId()+"/"+key,id->new Memory());}
        public float value(float base,float growth,float scale){return base+growth*(rank-1)+scale*strength;}
        public boolean behind(LivingEntity t){Vec3 to=player.position().subtract(t.position()).multiply(1,0,1).normalize();Vec3 look=t.getLookAngle().multiply(1,0,1).normalize();return to.dot(look)<-.5;}
        public boolean hero(LivingEntity t){return t instanceof ServerPlayer||JiarenManager.isJiaren(t.getUUID());}
        public boolean enemy(LivingEntity t){return SkillTargets.enemy(player,t)&&player.hasLineOfSight(t);}
        public List<LivingEntity> enemies(Vec3 center,double radius){return player.level().getEntitiesOfClass(LivingEntity.class,
            new AABB(center,center).inflate(radius,2,radius),t->enemy(t)&&t.position().distanceToSqr(center)<=radius*radius);}
        public List<ServerPlayer> allies(Vec3 center,double radius){return player.level().players().stream().filter(t->t.isAlive()&&!t.isSpectator()
            &&SkillTargets.ally(player,t)&&player.hasLineOfSight(t)&&t.position().distanceToSqr(center)<=radius*radius).toList();}
        public LivingEntity target(double range,boolean friendly){
            Vec3 from=player.getEyePosition(),to=from.add(player.getLookAngle().scale(range));
            var block=player.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));if(block.getType()!=HitResult.Type.MISS)to=block.getLocation();
            final Vec3 end=to;
            return player.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(1.2),t->t!=player&&t.isAlive()&&!t.isSpectator()&&!(t instanceof DefenseTowerEntity)
                &&(friendly?SkillTargets.ally(player,t)&&t instanceof ServerPlayer:enemy(t)&&VisionSystem.canSee(player,t))&&!t.isInvisibleTo(player))
                .stream().filter(t->t.getBoundingBox().inflate(.65).clip(from,end).isPresent())
                .min(Comparator.comparingDouble(t->t.distanceToSqr(player))).orElse(null);
        }
        public Vec3 point(double range){
            return AbilityTargeting.ground(player,range,skill.requiresStandingPoint());
        }
        public boolean safe(Vec3 at){return AbilityTargeting.canStand(player,at);}
        public void damage(LivingEntity target,float amount,DamageCalc.Kind kind){damage(target,amount,kind,true);}
        public void damage(LivingEntity target,float amount,DamageCalc.Kind kind,boolean direct){
            if(amount<=0||!target.isAlive()||!SkillTargets.enemy(player,target))return;
            Source old=SOURCE.get();SOURCE.set(new Source(skill.getId(),direct,castToken));
            try{if(!direct||kind==DamageCalc.Kind.TRUE)DamageCalc.dealUncriticalSkillDamage(player,target,amount,kind,skill.slot()<0?4:skill.slot());
                else DamageCalc.dealSkillDamage(player,target,amount,kind,skill.slot());}finally{if(old==null)SOURCE.remove();else SOURCE.set(old);}
        }
        public void area(Vec3 at,double radius,float amount,DamageCalc.Kind kind){for(var t:enemies(at,radius))damage(t,amount,kind);AbilityVisuals.burst(this,at,radius);AbilityVisuals.seal(this,at,radius,false);}
        public void pulse(Vec3 at,double radius,float amount,DamageCalc.Kind kind){
            for(var t:enemies(at,radius))damage(t,amount,kind,false);AbilityVisuals.seal(this,at,radius,false);}
        public void shield(LivingEntity target,float amount,int ticks){if(!SkillTargets.ally(player,target))return;
            float before=ModShieldSystem.value(target);
            ModShieldSystem.grantMaxSource(target,skill.getId()+"/"+player.getUUID(),amount,ticks);
            float gained=Math.max(0,ModShieldSystem.value(target)-before);
            if(gained>0){AbilityVisuals.shield(this,target);
                if(!skill.role().equals("yingxiao"))AbilityVisuals.maintainShield(this,target,ticks);
                supported(target);}
            }
        public void heal(LivingEntity target,float amount){
            if(!target.isAlive()||target.level()!=player.level()||!SkillTargets.ally(player,target)||amount<=0)return;
            float before=target.getHealth();CombatTelemetry.heal(target,Math.min(amount,Math.max(0,target.getMaxHealth()-before)),skill.role()+"_skill");
            float gained=Math.max(0,target.getHealth()-before);
            if(gained>0){if(target instanceof ServerPlayer ally)PacketDistributor.sendToPlayer(ally,new NetworkSetup.HealFloatPayload(Math.max(1,Math.round(gained))));supported(target);}
        }
        private void supported(LivingEntity target){if(!skill.isPassive()){var passive=passive(player);if(passive!=null)passive.onSupport(context(player,passive),target);}}
        /** Honest cone, shared by blade and fan skills; never hits buildings or through walls. */
        public void cone(double range,double degrees,float amount,DamageCalc.Kind kind){
            Vec3 facing=forward(1);double cos=Math.cos(Math.toRadians(degrees*.5));
            for(var target:enemies(player.position(),range)){
                Vec3 to=target.position().subtract(player.position()).multiply(1,0,1);
                if(to.lengthSqr()<.01||to.normalize().dot(facing)>=cos)damage(target,amount,kind);
            }
            AbilityVisuals.arc(this,player.position(),range,degrees);
        }
        public boolean ownShield(LivingEntity target){return ModShieldSystem.hasSource(target,skill.getId()+"/"+player.getUUID());}
        public void buff(String key,int ticks){data.setBuff(key,ticks);}
        public void haste(float percent,int ticks){slow(player,-percent,ticks);}
        public void slow(LivingEntity t,float percent,int ticks){
            double tenacity=percent>0&&t instanceof ServerPlayer?Math.clamp(t.getAttributeValue(ModAttributes.TENACITY),0,60)/100:0;
            int duration=Math.max(1,(int)Math.ceil(ticks*(1-tenacity)));
            var attr=t.getAttribute(Attributes.MOVEMENT_SPEED);if(attr==null)return;
            var id=Identifier.fromNamespaceAndPath("myriad","ability_slow_"+skill.getId()+"_"+player.getUUID());
            attr.removeModifier(id);attr.addTransientModifier(new AttributeModifier(id,-percent,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            DEBUFFS.removeIf(d->d.target==t&&d.id.equals(id));DEBUFFS.add(new Debuff(t,id,t.level().getGameTime()+duration));
        }
        public void root(LivingEntity t,int ticks,boolean stun){
            t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,ticks,6,false,false));
            if(t instanceof ServerPlayer hero){double ten=Math.clamp(t.getAttributeValue(ModAttributes.TENACITY),0,60)/100;
                PlayerDataProvider.get(hero).setBuff(stun?"g1_stun":"g1_root",Math.max(1,(int)Math.ceil(ticks*(1-ten))));}
        }
        public void ring(Vec3 at,double radius,boolean personal){AbilityVisuals.seal(this,at,radius,personal);}
        public int color(){return switch(skill.role()){case "yingxiao"->0x808080;case "tianxing"->0x80C7FF;case "fenjue"->0xFF853D;case "xuanchen"->0xE7D6A0;default->0x9CF0C4;};}
        public void trail(Vec3 from,Vec3 to){AbilityVisuals.beam(this,from,to,.09);}
        /** Interruptible local-action windup. Real-time later/repeat remain unchanged. */
        public void windup(int ticks,float walkFactor,Runnable action){
            if(ticks<=0){action.run();return;}
            phase=Phase.WINDUP;owner.winding=this;windupProgress=0;
            final int expected=++owner.channelToken;windupToken=expected;
            owner.channelUntil=now()+ticks+1;owner.channelOrigin=walkFactor>0?null:player.position();
            owner.channelImpulse=KnockbackBuffer.impulseToken(player);
            buff("g1_channel",ticks+1);SkillChargeSystem.start(player,skill.getId(),ticks,walkFactor);
            if(skill.role().equals("yike"))com.myriad.character.skills.yike.YikeStance.refreshMovement(player);
            SkillChargeSystem.extend(player,ticks+1);
            var progress=new ActionTime.Progress(now());
            owner.tasks.add(new Task(){
                public boolean tick(long time){
                    if(owner.channelToken!=expected||phase==Phase.CANCELLED){cancel();return false;}
                    double rate=ActionTime.multiplier(player.getUUID(),time);
                    boolean finished=progress.advance(time,rate,ticks);windupProgress=(float)(progress.completed()/ticks);
                    if(finished){
                        if(owner.winding==Cast.this)owner.winding=null;
                        owner.channelUntil=0;buff("g1_channel",0);SkillChargeSystem.clear(player);
                        if(skill.role().equals("yike"))com.myriad.character.skills.yike.YikeStance.refreshMovement(player);
                        phase=committed?Phase.COMMITTED:Phase.REQUESTED;
                        action.run();if(phase==Phase.COMMITTED)phase=Phase.COMPLETED;return false;
                    }
                    int remaining=Math.max(2,(int)Math.ceil((ticks-progress.completed())/rate)+1);
                    owner.channelUntil=time+remaining;buff("g1_channel",remaining);
                    SkillChargeSystem.extend(player,remaining);return true;
                }
                public void cancel(){cancelCast();}
            });
        }
        public void later(int ticks,boolean channel,Runnable action){
            later(ticks,channel,0,action);
        }
        /** Unfinished actor attacks/volley releases consume local work; effect timers use later(). */
        public void actionLater(int ticks,Runnable action){
            actionWhile(ticks,progress->{if(progress+1E-9<ticks)return true;action.run();return false;});
        }
        /** Owner-bound local sequence with a per-tick interruption/geometry check. */
        public void actionWhile(int ticks,DoublePredicate action){
            if(ticks<0)throw new IllegalArgumentException("Negative action duration");
            var progress=new ActionTime.Progress(now());
            owner.tasks.add(time->{boolean finished=progress.advance(time,ActionTime.multiplier(player.getUUID(),time),ticks);
                return action.test(finished?ticks:progress.completed())&&!finished;});
        }
        /** Mobile windups retain the same CC/cast-token ownership as stationary windups. */
        public void later(int ticks,boolean channel,float walkFactor,Runnable action){
            long due=now()+ticks;int token=owner.channelToken;
            if(channel){token=++owner.channelToken;owner.channelUntil=due;owner.channelOrigin=walkFactor>0?null:player.position();owner.channelImpulse=KnockbackBuffer.impulseToken(player);buff("g1_channel",ticks);
                SkillChargeSystem.start(player,skill.getId(),ticks,walkFactor);
            if(skill.role().equals("yike"))com.myriad.character.skills.yike.YikeStance.refreshMovement(player);}
            final int expected=token;owner.tasks.add(time->{if(time<due)return true;
                if(!channel||owner.channelToken==expected){if(channel){owner.channelUntil=0;buff("g1_channel",0);SkillChargeSystem.clear(player);
                        if(skill.role().equals("yike"))com.myriad.character.skills.yike.YikeStance.refreshMovement(player);}action.run();}return false;});
        }
        /** Identity belongs to one moving visual, not to a whole skill or a whole three-shot volley. */
        public int newVisualInstance(){return VISUAL_SEQUENCE.updateAndGet(previous->previous==Integer.MAX_VALUE?1:previous+1);}
        public void repeat(int duration,int interval,IntConsumer action){
            long start=now();owner.tasks.add(time->{long elapsed=time-start;if(elapsed<=0)return true;if(elapsed>duration)return false;
                if(elapsed%interval==0)action.accept((int)(elapsed/interval));return elapsed<duration;});
        }
        public void tickFor(int duration,Consumer<Integer> action){long start=now();owner.tasks.add(time->{long age=time-start;if(age>duration)return false;action.accept((int)age);return age<duration;});}
        public void whileActive(int duration,Predicate<Integer> action){whileActive(duration,action,()->{});}
        /** Independent flights can release their identities when ordinary lifecycle cleanup cancels a task. */
        public void whileActive(int duration,Predicate<Integer> action,Runnable cancelled){long start=now();owner.tasks.add(new Task(){
            public boolean tick(long time){long age=time-start;return age<=duration&&action.test((int)age)&&age<duration;}
            public void cancel(){cancelled.run();}
        });}
        public void projectile(double range,double speed,double radius,boolean pierce,boolean returning,BiConsumer<LivingEntity,Boolean> impact){
            projectileAlong(player.getLookAngle(),range,speed,radius,pierce,returning,impact);
        }
        /** Independent flight identity: actor rates never implicitly slow a projectile. */
        public UUID actionProjectile(double range,double speed,double radius,boolean pierce,boolean returning,BiConsumer<LivingEntity,Boolean> impact){
            return actionProjectileAlong(player.getLookAngle(),range,speed,radius,pierce,returning,impact);
        }
        public UUID actionProjectileAlong(Vec3 aim,double range,double speed,double radius,boolean pierce,boolean returning,BiConsumer<LivingEntity,Boolean> impact){
            UUID id=UUID.randomUUID();projectileAlong(aim,range,speed,radius,pierce,returning,impact,id);return id;
        }
        public void projectileAlong(Vec3 aim,double range,double speed,double radius,boolean pierce,boolean returning,BiConsumer<LivingEntity,Boolean> impact){
            projectileAlong(aim,range,speed,radius,pierce,returning,impact,null);
        }
        private void projectileAlong(Vec3 aim,double range,double speed,double radius,boolean pierce,boolean returning,BiConsumer<LivingEntity,Boolean> impact,UUID actionId){
            Vec3 initial=aim.lengthSqr()<1E-8?forward(1):aim.normalize();
            final class Flight implements Task {
                Vec3 pos=player.getEyePosition().add(initial.scale(.4)),direction=initial;double traveled;boolean back;
                final Set<UUID> hit=new HashSet<>();final int visual=newVisualInstance();int age;boolean ended;
                long lastTick=now();double flightAge;
                public void cancel(){if(actionId!=null)ActionTime.clearTarget(actionId);}
                final int safetyTicks=2*(returning?160:Math.min(160,Math.max(1,(int)Math.ceil(range/speed))));
                boolean finish(boolean dissipate){if(!ended){ended=true;cancel();AbilityVisuals.finishFlight(Cast.this,visual);if(dissipate)AbilityVisuals.dissipate(Cast.this,pos);}return false;}
                public boolean tick(long now){
                    if(actionId!=null&&now<=lastTick)return true;lastTick=now;
                    double rate=actionId==null?1:projectileRate(player,actionId,pos,now);flightAge+=rate;
                    if(++age>(actionId==null?160:safetyTicks)||flightAge>160)return finish(true);
                    double step=speed*rate;
                    if(back){Vec3 toward=player.getEyePosition().subtract(pos);if(toward.length()<step)return finish(true);direction=toward.normalize();}
                    Vec3 end=pos.add(direction.scale(back?step:Math.min(step,range-traveled)));
                    Vec3 wallHit=com.myriad.combat.WindWallSystem.firstEnemyHit(player,pos,end);
                    if(wallHit!=null){AbilityVisuals.flight(Cast.this,pos,wallHit,age,back,visual);pos=wallHit;return finish(true);}
                    var clip=player.level().clip(new ClipContext(pos,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));boolean blocked=clip.getType()!=HitResult.Type.MISS;
                    if(blocked)end=clip.getLocation();final Vec3 dest=end;
                    var targets=player.level().getEntitiesOfClass(LivingEntity.class,new AABB(pos,end).inflate(radius),t->SkillTargets.enemy(player,t)&&!hit.contains(t.getUUID())
                        &&(actionId==null?t.getBoundingBox().inflate(radius).clip(pos,dest).isPresent()
                            :AbilityTargeting.unobstructedHit(player,t.getBoundingBox(),pos,dest,radius).isPresent())).stream().sorted(Comparator.comparingDouble(t->t.position().distanceToSqr(pos))).toList();
                    for(var target:targets){hit.add(target.getUUID());
                        if(!pierce){
                            Vec3 contact=target.getBoundingBox().inflate(radius).clip(pos,dest).orElse(dest);
                            AbilityVisuals.flight(Cast.this,pos,contact,age,back,visual);
                        }
                        impact.accept(target,back);AbilityVisuals.burst(Cast.this,target.position().add(0,.5,0),.55);if(!pierce)return finish(false);}
                    AbilityVisuals.flight(Cast.this,pos,end,age,back,visual);traveled+=pos.distanceTo(end);pos=end;if(blocked)return finish(true);
                    if(!back&&traveled>=range){if(!returning)return finish(true);back=true;hit.clear();}return true;}
            }owner.tasks.add(new Flight());
        }
        public void actionDash(Vec3 delta,int ticks,double radius,BiConsumer<LivingEntity,Boolean> impact,Runnable end,boolean turn,boolean stopAtHero){
            dash(delta,ticks,radius,impact,end,turn,stopAtHero,true);
        }
        public void dash(Vec3 delta,int ticks,double radius,BiConsumer<LivingEntity,Boolean> impact,Runnable end,boolean turn,boolean stopAtHero){
            dash(delta,ticks,radius,impact,end,turn,stopAtHero,false);
        }
        private void dash(Vec3 delta,int ticks,double radius,BiConsumer<LivingEntity,Boolean> impact,Runnable end,boolean turn,boolean stopAtHero,boolean local){
            if(data.getBuff("g1_root")>0||data.getBuff("g1_stun")>0)return;
            if(delta.lengthSqr()<.0025)return;
            final int token=++owner.motionToken;
            final Vec3 destination=player.position().add(delta);final long start=now();final long impulse=KnockbackBuffer.impulseToken(player);
            owner.motionUntil=now()+ticks+4;
            final class Dash implements Task {
                Vec3 last=player.position(),direction=delta.normalize();final Set<UUID> hits=new HashSet<>();double moved,age;long lastTick=start;
                public boolean tick(long now){if(token!=owner.motionToken)return false;if(now==start||local&&now<=lastTick)return true;lastTick=now;
                    if(local&&KnockbackBuffer.impulseToken(player)!=impulse){
                        owner.motionUntil=0;Vec3 forced=player.getDeltaMovement();
                        PacketDistributor.sendToPlayer(player,new NetworkSetup.DashPayload((float)forced.x,(float)forced.y,(float)forced.z,-1));
                        return false;
                    }
                    double rate=local?ActionTime.multiplier(player.getUUID(),now):1;
                    if(local)owner.motionUntil=now+Math.max(2,(int)Math.ceil((ticks+4-age)/rate));
                    Vec3 pos=player.position();moved+=Math.max(0,pos.subtract(last).dot(direction));
                    for(var target:DashHitbox.targets(player,last,pos,radius,hits)){boolean first=hits.isEmpty();hits.add(target.getUUID());impact.accept(target,first);
                        if(stopAtHero&&hero(target)){finish();return false;}}
                    AbilityVisuals.motionTrail(Cast.this,last,pos);last=pos;
                    age+=rate;
                    if(age>=ticks+4||moved>=delta.length()-.04||!turn&&pos.distanceToSqr(destination)<.0025
                            ||data.getBuff("g1_root")>0||data.getBuff("g1_stun")>0){finish();return false;}
                    if(turn)direction=direction.lerp(forward(1),.18).normalize();
                    double step=Math.min(delta.length()/ticks*rate,Math.max(0,delta.length()-moved));Vec3 velocity=direction.scale(step);
                    if(!AbilityTargeting.clearSweep(player,velocity)){finish();return false;}
                    sendMotion(velocity,turn?pos.add(direction.scale(delta.length()-moved)):destination);return true;}
                void finish(){owner.motionUntil=0;Vec3 v=skill.requiresStandingPoint()?Vec3.ZERO:player.getDeltaMovement().scale(.12);
                    player.setDeltaMovement(v);player.hurtMarked=true;player.resetFallDistance();
                    PacketDistributor.sendToPlayer(player,new NetworkSetup.DashPayload((float)v.x,(float)v.y,(float)v.z,-1));end.run();}
            }
            var dash=new Dash();Vec3 v=delta.scale((local?ActionTime.multiplier(player.getUUID(),now()):1)/ticks);
            if(!AbilityTargeting.clearSweep(player,v)){dash.finish();return;}
            sendMotion(v,destination);owner.tasks.add(dash);
        }
        private void sendMotion(Vec3 velocity,Vec3 destination){player.setDeltaMovement(velocity);player.hurtMarked=true;player.resetFallDistance();
            PacketDistributor.sendToPlayer(player,new com.myriad.network.AbilityMotionPayload((float)velocity.x,(float)velocity.y,(float)velocity.z,
                    destination.x,destination.y,destination.z,3));}
        /** Arc motion is calculated against the actual position each tick; never teleports. */
        public void leap(Vec3 destination,int ticks,double height,Runnable landed){
            leap(destination,ticks,height,false,landed);
        }
        /** Exact preflight arc, with landing effects only after successful arrival (no impact on an aborted leap). */
        public void leap(Vec3 destination,int ticks,double height,boolean precise,Runnable landed){
            leap(destination,ticks,height,precise,false,landed);
        }
        public void actionLeap(Vec3 destination,int ticks,double height,Runnable landed){
            leap(destination,ticks,height,false,true,landed);
        }
        public void actionLeap(Vec3 destination,int ticks,double height,boolean precise,Runnable landed){
            leap(destination,ticks,height,precise,true,landed);
        }
        private void leap(Vec3 destination,int ticks,double height,boolean precise,boolean local,Runnable landed){
            if(data.getBuff("g1_root")>0||data.getBuff("g1_stun")>0||!AbilityTargeting.clearArc(player,destination,ticks,height))return;
            final int token=++owner.motionToken;owner.motionUntil=now()+ticks+8;
            Vec3 origin=player.position();long start=now();final double[] age={0};
            double initialRate=local?ActionTime.multiplier(player.getUUID(),start):1;
            final double[] point={initialRate};final long impulse=KnockbackBuffer.impulseToken(player);final long[] lastTick={start};
            Runnable finish=()->{owner.motionUntil=0;player.setDeltaMovement(Vec3.ZERO);player.resetFallDistance();
                PacketDistributor.sendToPlayer(player,new NetworkSetup.DashPayload(0,0,0,-1));
                if(precise&&player.position().distanceToSqr(destination)>.16)return;
                if(safe(player.position()))landed.run();else whileActive(12,a->{
                    if(token!=owner.motionToken)return false;if(safe(player.position())){landed.run();return false;}return true;});};
            Task motion=time->{if(token!=owner.motionToken)return false;if(time<=lastTick[0])return true;lastTick[0]=time;
                if(local&&KnockbackBuffer.impulseToken(player)!=impulse){owner.motionUntil=0;Vec3 forced=player.getDeltaMovement();
                    PacketDistributor.sendToPlayer(player,new NetworkSetup.DashPayload((float)forced.x,(float)forced.y,(float)forced.z,-1));return false;}
                double rate=local?ActionTime.multiplier(player.getUUID(),time):1;age[0]+=rate;
                if(local)owner.motionUntil=time+Math.max(2,(int)Math.ceil((ticks+8-age[0])/rate));
                if(age[0]>ticks+8||data.getBuff("g1_root")>0||data.getBuff("g1_stun")>0){
                    if(precise){owner.motionUntil=0;player.setDeltaMovement(Vec3.ZERO);player.resetFallDistance();
                        PacketDistributor.sendToPlayer(player,new NetworkSetup.DashPayload(0,0,0,-1));return false;}
                    finish.run();return false;}
                if(age[0]>=ticks&&player.position().distanceToSqr(destination)<.01){finish.run();return false;}
                point[0]+=rate;double t=Math.min(1,point[0]/ticks);
                Vec3 desired=origin.lerp(destination,t).add(0,4*height*t*(1-t),0);
                Vec3 velocity=desired.subtract(player.position());double cap=origin.distanceTo(destination)/ticks+1;
                if(precise&&velocity.length()>cap+4*height/ticks){
                    // A displaced/lagged body must not catch up with an arbitrarily large corrective impulse.
                    owner.motionUntil=0;player.setDeltaMovement(Vec3.ZERO);player.resetFallDistance();
                    PacketDistributor.sendToPlayer(player,new NetworkSetup.DashPayload(0,0,0,-1));return false;
                }
                if(!precise&&velocity.length()>cap)velocity=velocity.normalize().scale(cap);
                if(!AbilityTargeting.clearSweep(player,velocity)){finish.run();return false;}
                AbilityVisuals.motionTrail(Cast.this,player.position(),desired);sendMotion(velocity,desired);return true;};
            double t=initialRate/ticks;Vec3 desired=origin.lerp(destination,t).add(0,4*height*t*(1-t),0);
            if(!AbilityTargeting.clearSweep(player,desired.subtract(origin))){finish.run();return;}
            sendMotion(desired.subtract(origin),desired);owner.tasks.add(motion);
        }
        public void portal(Vec3 from,Vec3 to,int duration){
            Memory memory=memory("portal");int token=++memory.count;memory.targets.clear();whileActive(duration,age->{if(token!=memory.count)return false;
                if(age%6==0){AbilityVisuals.portalAnchor(this,from);AbilityVisuals.portalAnchor(this,to);}
                for(var ally:allies(from,2)){if(ally.isShiftKeyDown()&&ally.position().distanceToSqr(from)<=2.25&&memory.targets.getOrDefault(ally.getUUID(),0L)<=now()
                        &&PlayerDataProvider.get(ally).getBuff("g1_root")==0&&PlayerDataProvider.get(ally).getBuff("g1_stun")==0&&AbilityTargeting.canStand(ally,to)){
                    ally.teleportTo(to.x,to.y,to.z);memory.targets.put(ally.getUUID(),now()+40);}}
                for(var ally:allies(to,2)){if(ally.isShiftKeyDown()&&ally.position().distanceToSqr(to)<=2.25&&memory.targets.getOrDefault(ally.getUUID(),0L)<=now()
                        &&PlayerDataProvider.get(ally).getBuff("g1_root")==0&&PlayerDataProvider.get(ally).getBuff("g1_stun")==0&&AbilityTargeting.canStand(ally,from)){
                    ally.teleportTo(from.x,from.y,from.z);memory.targets.put(ally.getUUID(),now()+40);}}
                return true;
            });
        }
    }
}
