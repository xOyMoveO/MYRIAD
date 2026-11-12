package com.myriad.character.skills.shuo;
import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.*;
import com.myriad.game.DefenseTowerEntity;
import com.myriad.network.ShuoViewPayload;
import com.myriad.player.*;
import com.myriad.registry.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.myriad.network.ShuoViewPayload.*;

/** Shuo's weapon state only. Each concrete ability retains its own combat formula. */
public final class ShuoCombat {
    private static final WeakHashMap<ServerPlayer,State> STATES=new WeakHashMap<>();
    private static final AtomicInteger SEQUENCE=new AtomicInteger();
    private static final class State {final ShuoBasicCombo combo=new ShuoBasicCombo();boolean drawn,charging;long sheathUntil,busyUntil,windupUntil,guardUntil,riposteUntil,lastTick=-1,bonusTick=-1;int gauge,chargeSkill=-1;float basicBonus;Object level;}
    private ShuoCombat(){}
    private static State state(ServerPlayer p){var s=STATES.computeIfAbsent(p,k->new State());if(s.level!=p.level()){s=new State();s.level=p.level();STATES.put(p,s);}return s;}
    public static boolean isShuo(ServerPlayer p){return "shuo".equals(PlayerDataProvider.get(p).getCharacterId());}
    public static boolean blocked(ServerPlayer p){if(!isShuo(p))return false;var s=state(p);long now=p.level().getGameTime();return s.charging||s.sheathUntil>now||s.busyUntil>now||AbilityRuntime.moving(p);}
    public static boolean drawn(ServerPlayer p){return state(p).drawn;}
    public static int gauge(ServerPlayer p){return state(p).gauge;}
    public static void cue(ServerPlayer p,int action,int ticks){int token=SEQUENCE.updateAndGet(v->v==Integer.MAX_VALUE?1:v+1);PacketDistributor.sendToPlayer(p,new ShuoViewPayload(action,ticks,p.level().getGameTime(),token));}
    public static void clear(ServerPlayer p){
        ShuoWeaponState.sync(p,false);
        if(STATES.remove(p)!=null&&p.connection!=null)cue(p,CANCEL,0);
        var d=PlayerDataProvider.get(p);for(String key:List.of("g1_shuo_drawn","g1_shuo_gauge","g1_shuo_ready","g1_shuo_sheathing","shuo_qiaofan_recast","shuo_canyue_recast"))d.setBuff(key,0);
    }
    public static void tick(ServerPlayer p){if(!isShuo(p))return;
        var s=state(p);long now=p.level().getGameTime();
        if(s.charging&&!AbilityRuntime.channeling(p)){s.charging=false;s.windupUntil=0;s.busyUntil=0;ShuoVisuals.cancel(p,s.chargeSkill);s.chargeSkill=-1;cue(p,CANCEL,0);}
        if(s.sheathUntil>0&&now>=s.sheathUntil){s.sheathUntil=0;s.drawn=false;s.gauge=0;ShuoAudio.play(p,"sheathe");}
        if(PlayerDataProvider.get(p).getBuff("g1_stun")>0){if(s.guardUntil>now)ShuoVisuals.cancel(p,3);s.guardUntil=0;s.riposteUntil=0;}
        if(s.guardUntil>now)ShuoVisuals.guard(p);
        var d=PlayerDataProvider.get(p);d.setBuff("g1_shuo_drawn",s.drawn?2:0);d.setBuff("g1_shuo_gauge",s.gauge);
        ShuoWeaponState.sync(p,s.drawn);
        d.setBuff("g1_shuo_sheathing",(int)Math.max(0,s.sheathUntil-now));d.setBuff("g1_shuo_ready",s.riposteUntil>now?2:0);
    }
    public static void chargeEdge(ServerPlayer p){var s=state(p);long now=p.level().getGameTime();if(s.lastTick==now)return;s.lastTick=now;
        if(!s.drawn&&!blocked(p)){int old=s.gauge;s.gauge=Math.min(100,s.gauge+2);if(old<100&&s.gauge==100)ShuoVisuals.body(p,0,ShuoSkillVfx.READY,12);}
        PlayerDataProvider.get(p).setBuff("g1_shuo_gauge",s.gauge);
    }
    public static void windup(AbilityRuntime.Cast c,int ticks,Runnable fire){
        var s=state(c.player);s.charging=true;s.windupUntil=c.now()+ticks;
        s.chargeSkill=c.skill.index();ShuoVisuals.charge(c,ticks);
        cue(c.player,CHARGE,ticks);ShuoAudio.play(c.player,"charge");
        c.later(ticks,true,()->{s.charging=false;s.windupUntil=0;s.chargeSkill=-1;ShuoVisuals.cancel(c.player,c.skill.index());fire.run();});
    }
    public static float draw(ServerPlayer p,int action,int ticks){
        var s=state(p);float bonus=0;int passiveIndex=-1;var d=PlayerDataProvider.get(p);long now=p.level().getGameTime();
        if(!s.drawn){var passive=FirstGroupSkills.selected("shuo",0,d.getSkillLevels());int r=passive==null?0:d.skillLevel(passive.getId());
            if(r>0){passiveIndex=passive.index();float strength=(float)p.getAttributeValue(ModAttributes.SPELL_POWER);
                if(passive.index()==0)bonus=(40+12*(r-1)+.4f*strength)*s.gauge/100;
                else if(s.riposteUntil>now)bonus=55+15*(r-1)+.5f*strength;
            }}
        s.drawn=true;s.charging=false;s.sheathUntil=0;s.gauge=0;s.riposteUntil=0;
        ShuoWeaponState.sync(p,true);
        s.guardUntil=0;d.setBuff("g1_shuo_drawn",2);
        if(!basicAction(action))s.combo.reset();
        cue(p,action,ticks);ShuoAudio.play(p,"draw");if(bonus>0)ShuoVisuals.body(p,passiveIndex,ShuoSkillVfx.EMPOWER,8);return bonus;
    }
    public static void basic(ServerPlayer p){if(!isShuo(p))return;var s=state(p);boolean wasDrawn=s.drawn;
        int action=s.combo.select(p.level().getGameTime(),wasDrawn);s.basicBonus=draw(p,action,9);s.bonusTick=p.level().getGameTime();
        ShuoVisuals.basic(p,wasDrawn,action);
    }
    public static void basicExtra(AbilityRuntime.Hit hit,AbilitySkill passive){if(!hit.basic()||!isShuo(hit.player()))return;
        var s=state(hit.player());if(s.bonusTick!=hit.player().level().getGameTime()||s.basicBonus<=0)return;
        float amount=s.basicBonus;s.basicBonus=0;
        float before=hit.target().getHealth()+ModShieldSystem.value(hit.target());
        AbilityRuntime.context(hit.player(),passive).damage(hit.target(),amount,DamageCalc.Kind.PHYSICAL,false);
        if(hit.target().getHealth()+ModShieldSystem.value(hit.target())<before)ShuoVisuals.contact(hit.player(),passive.index(),hit.target(),.7f);
    }
    public static boolean requestSheathe(ServerPlayer p){
        if(!AbilityRuntime.valid(p)||!isShuo(p)||blocked(p)||AbilityRuntime.channeling(p)||AbilityRuntime.moving(p)||PlayerDataProvider.get(p).getBuff("g1_stun")>0||!state(p).drawn)return false;
        sheathe(p);return true;
    }
    public static void sheathe(ServerPlayer p){if(!AbilityRuntime.valid(p)||!isShuo(p))return;var s=state(p);s.charging=false;s.sheathUntil=p.level().getGameTime()+8;s.busyUntil=0;
        ShuoVisuals.body(p,2,ShuoSkillVfx.SHEATHING,8);
        s.combo.reset();
        cue(p,SHEATHE,8);PlayerDataProvider.get(p).setBuff("g1_shuo_sheathing",8);}
    public static void motion(ServerPlayer p,int ticks){state(p).busyUntil=p.level().getGameTime()+ticks;cue(p,RETREAT,ticks);ShuoAudio.play(p,"draw");}
    public static void guard(AbilityRuntime.Cast c){state(c.player).guardUntil=c.now()+7;c.memory().until=0;c.buff("shuo_qiaofan_recast",0);ShuoVisuals.guard(c.player);cue(c.player,GUARD,7);}
    /** Short exposed-blade cut, not a new draw: no gauge/passive bonus is created. */
    public static void cut(AbilityRuntime.Cast c,int action,int ticks){state(c.player).combo.reset();cue(c.player,action,ticks);}
    /** Follow-ups latch even a brief control interruption; they never fire after a cancelled sequence. */
    public static void follow(AbilityRuntime.Cast c,int ticks,Runnable fire){
        var s=state(c.player);s.busyUntil=Math.max(s.busyUntil,c.now()+ticks);
        c.whileActive(ticks,age->{
            if(c.data.getBuff("g1_stun")>0||c.data.getBuff("g1_root")>0||com.myriad.combat.SilenceSystem.isSilenced(c.player)){
                s.busyUntil=0;cue(c.player,CANCEL,0);return false;
            }
            if(age<ticks)return true;s.busyUntil=0;fire.run();return false;
        });
    }
    public static boolean parry(ServerPlayer p,DamageSource source,DamageCalc.Kind kind){
        if(!isShuo(p)||!AbilityRuntime.valid(p)||kind==DamageCalc.Kind.TRUE)return false;
        var s=state(p);long now=p.level().getGameTime();
        if(s.guardUntil<=now||PlayerDataProvider.get(p).getBuff("g1_stun")>0||!(source.getEntity() instanceof LivingEntity enemy)||enemy instanceof DefenseTowerEntity||!SkillTargets.enemy(p,enemy))return false;
        Vec3 toward=enemy.position().subtract(p.position()).multiply(1,0,1).normalize();
        Vec3 facing=p.getLookAngle().multiply(1,0,1).normalize();if(toward.dot(facing)<Math.cos(Math.toRadians(65)))return false;
        s.guardUntil=0;s.riposteUntil=now+80;
        ShuoVisuals.cancel(p,3);ShuoVisuals.body(p,3,ShuoSkillVfx.PARRY,8);
        var skill=FirstGroupSkills.byId("shuo_qiaofan");AbilityRuntime.context(p,skill).memory().until=now+40;
        PlayerDataProvider.get(p).setBuff("shuo_qiaofan_recast",40);ShuoAudio.play(p,"parry");cue(p,COUNTER,6);return true;
    }
    public static void cone(AbilityRuntime.Cast c,double range,double degrees,float amount,int stun){
        cone(c,range,degrees,amount,stun,0,0);
    }
    /** A visual hit requires effective health/shield loss; invulnerability is not an impact. */
    public static boolean physicalHit(AbilityRuntime.Cast c,LivingEntity target,float amount){
        float before=target.getHealth()+ModShieldSystem.value(target);
        c.damage(target,amount,DamageCalc.Kind.PHYSICAL);
        return target.getHealth()+ModShieldSystem.value(target)<before;
    }
    public static void cone(AbilityRuntime.Cast c,double range,double degrees,float amount,int stun,float slow,int slowTicks){
        coneAt(c,c.player.position(),c.forward(1),range,degrees,amount,stun,slow,slowTicks);
    }
    /** Fixed spatial cuts do not rotate or follow the caster after being committed. */
    public static Set<UUID> coneAt(AbilityRuntime.Cast c,Vec3 origin,Vec3 facing,double range,double degrees,float amount,int stun,float slow,int slowTicks){
        var hits=new HashSet<UUID>();
        for(var target:c.player.level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(origin,origin).inflate(range,1.5,range),t->!(t instanceof DefenseTowerEntity)&&c.enemy(t))){
            if(!BasicAttackSystem.inMeleeArc(origin,facing,target.getBoundingBox(),range,Math.toRadians(degrees*.5)))continue;
            if(!clearCut(c,origin,target)||!physicalHit(c,target,amount))continue;
            hits.add(target.getUUID());if(stun>0)c.root(target,stun,true);
            ShuoVisuals.hit(c,target);if(slow>0)c.slow(target,slow,slowTicks);
        }
        ShuoAudio.play(c.player,"slash");return hits;
    }
    public static boolean clearCut(AbilityRuntime.Cast c,Vec3 origin,LivingEntity target){
        var from=origin.add(0,.9,0);var to=target.getBoundingBox().getCenter();
        return c.player.level().clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,c.player)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    /** Target geometry and the visible strip use the same finite length and half-width. */
    public static Set<UUID> line(AbilityRuntime.Cast c,Vec3 origin,Vec3 facing,double length,double width,float amount){
        var hits=new HashSet<UUID>();var a=origin.add(0,.9,0);var b=a.add(facing.normalize().scale(length));
        var axis=facing.normalize();var side=new Vec3(axis.z,0,-axis.x);
        for(var t:c.player.level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(a,b).inflate(width,1,width),
                t->!(t instanceof DefenseTowerEntity)&&c.enemy(t))){
            var bounds=t.getBoundingBox().inflate(width,.5,width);
            var box=t.getBoundingBox();var relative=box.getCenter().subtract(a);
            double along=relative.dot(axis),across=relative.dot(side);
            double extAlong=(Math.abs(axis.x)*box.getXsize()+Math.abs(axis.z)*box.getZsize())*.5;
            double extAcross=(Math.abs(side.x)*box.getXsize()+Math.abs(side.z)*box.getZsize())*.5;
            if(along+extAlong<0||along-extAlong>length||Math.abs(across)>width+extAcross)continue;
            if(!(bounds.contains(a)||bounds.clip(a,b).isPresent())||!clearCut(c,origin,t)||!physicalHit(c,t,amount))continue;
            hits.add(t.getUUID());ShuoVisuals.hit(c,t);
        }
        ShuoAudio.play(c.player,"slash");return hits;
    }
}
