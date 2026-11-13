package com.myriad.character.skills.keheng;

import com.myriad.Myriad;
import com.myriad.character.ability.*;
import com.myriad.combat.*;
import com.myriad.game.*;
import com.myriad.player.*;
import com.myriad.registry.ModAttributes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.*;
import java.util.*;
import static com.myriad.combat.DamageCalc.Kind.MAGIC;

/** Keheng's real-time field lifecycle and source publication. Does not change the world clock.
 * Actor rates and independent projectile sampling deliberately have separate entry points. */
@EventBusSubscriber(modid=Myriad.MODID)
public final class KehengTimeFields {
    private KehengTimeFields(){}
    private static final Identifier MOVE=Identifier.fromNamespaceAndPath("myriad","local_action_movement");
    public static final class Field {
        final AbilityRuntime.Cast cast;final String key;final Vec3 origin,direction;final double radius,actorRate,flightRate;
        final long begins,ends,impulse;final boolean follows,corridor;final float opening,pulse,ending;
        long lastTick=Long.MIN_VALUE;boolean opened,inside,removed;
        Field(AbilityRuntime.Cast c,Vec3 at,Vec3 facing,double r,int warning,int duration,double actor,double flight,float open,float pulse,float end,boolean follow,boolean corridor){
            cast=c;origin=at;direction=facing;radius=r;actorRate=actor;flightRate=flight;begins=c.now()+warning;ends=begins+duration;
            impulse=KnockbackBuffer.impulseToken(c.player);follows=follow;this.corridor=corridor;opening=open;this.pulse=pulse;ending=end;
            key="keheng_field_"+c.castId();
        }
        public Vec3 center(){return follows?cast.player.position():origin;}
        public double length(){
            var from=origin.add(0,1.5,0);var to=from.add(direction.scale(12));
            var hit=cast.player.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,cast.player));
            return hit.getType()==HitResult.Type.MISS?12:Math.max(0,from.distanceTo(hit.getLocation())-.02);
        }
        public boolean contains(Vec3 point){
            var at=center();
            if(corridor){var delta=point.subtract(origin.add(0,1.5,0));var side=new Vec3(direction.z,0,-direction.x);
                double along=delta.dot(direction);if(along<0||along>length()||Math.abs(delta.dot(side))>1.5||Math.abs(delta.y)>1.5)return false;
                return clear(cast.player,origin.add(0,1.5,0),point);
            }
            return point.distanceToSqr(at)<=radius*radius+.00001&&clear(cast.player,at.add(0,.12,0),point.add(0,.12,0));
        }
        boolean active(long now){return !removed&&now>=begins&&now<ends&&(!corridor||AbilityRuntime.channeling(cast.player));}
        boolean interrupted(){var d=cast.data;return d.getBuff("g1_stun")>0||SilenceSystem.isSilenced(cast.player)||KnockbackBuffer.impulseToken(cast.player)!=impulse;}
    }
    private record Needle(AbilityRuntime.Cast cast,LivingEntity target,double rate,long until,String key){}
    private record Published(UUID owner,String key){}
    private static final List<Field> FIELDS=new ArrayList<>();
    private static final List<Needle> NEEDLES=new ArrayList<>();
    private static final Map<UUID,List<Published>> PUBLISHED=new HashMap<>();
    private static final Map<UUID,Long> CLEANSED=new HashMap<>();
    private static final Map<UUID,ServerPlayer> ACTORS=new HashMap<>();

    public static float health(LivingEntity t){return (MonsterManager.isMonster(t)?MonsterManager.modHealth(t):t.getHealth())+ModShieldSystem.value(t);}
    public static boolean clear(ServerPlayer p,Vec3 from,Vec3 to){return p.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;}
    public static void ground(AbilityRuntime.Cast c,Vec3 at,double radius,int warning,int duration,double rate,float opening,float pulse){
        add(new Field(c,at,Vec3.ZERO,radius,warning,duration,rate,rate,opening,pulse,0,false,false));
    }
    public static void follow(AbilityRuntime.Cast c,int duration,double rate,float ending){
        add(new Field(c,c.player.position(),Vec3.ZERO,3,0,duration,rate,rate,0,0,ending,true,false));
        c.buff("g1_keheng_follow",duration);
    }
    public static Field corridor(AbilityRuntime.Cast c,Vec3 origin,Vec3 facing){
        var f=new Field(c,origin,facing.normalize(),1.5,0,Integer.MAX_VALUE,.7,.4,0,0,0,false,true);FIELDS.add(f);return f;
    }
    private static void add(Field field){FIELDS.add(field);tick(field.cast.player.level());refreshActor(field.cast.player);}
    public static boolean following(ServerPlayer p){return FIELDS.stream().anyMatch(f->f.follows&&f.cast.player==p&&!f.removed);}
    public static void cancelFollowing(ServerPlayer p){for(var f:new ArrayList<>(FIELDS))if(f.follows&&f.cast.player==p)remove(f);}
    public static void remove(Field f){if(f.removed)return;f.removed=true;FIELDS.remove(f);KehengVisuals.end(f.cast,f.center(),f.radius);
        if(f.follows)f.cast.buff("g1_keheng_follow",0);
        KehengAudio.play(f.cast.player,"end");refreshPublished();}
    public static void penetrate(Field f,float damage){if(f.removed)return;
        for(var target:f.cast.player.level().getEntitiesOfClass(LivingEntity.class,new AABB(f.origin,f.origin.add(f.direction.scale(f.length()))).inflate(1.6,3,1.6),
                t->SkillTargets.enemy(f.cast.player,t)&&f.contains(t.getBoundingBox().getCenter()))){
            f.cast.damage(target,damage,MAGIC);KehengVisuals.impact(f.cast,target.position().add(0,.9,0));
        }
        KehengVisuals.penetrate(f.cast,f.origin.add(0,1.3,0),f.origin.add(f.direction.scale(f.length())).add(0,1.3,0));KehengAudio.play(f.cast.player,"pierce");
    }
    private static List<LivingEntity> targets(Field f){var at=f.center();return f.cast.player.level().getEntitiesOfClass(LivingEntity.class,
            new AABB(at,at).inflate(f.radius),t->SkillTargets.enemy(f.cast.player,t)&&f.contains(t.position()));}
    private static void damage(Field f,float amount){for(var target:targets(f)){f.cast.damage(target,amount,MAGIC);KehengVisuals.impact(f.cast,target.position().add(0,.9,0));}}
    public static void tick(ServerLevel level){long now=level.getGameTime();
        for(var f:new ArrayList<>(FIELDS)){
            if(f.cast.player.level()!=level)continue;
            if(f.lastTick==now)continue;f.lastTick=now;
            if(!AbilityRuntime.valid(f.cast.player)||!"keheng".equals(PlayerDataProvider.get(f.cast.player).getCharacterId())){remove(f);continue;}
            if(f.follows&&f.interrupted()||f.corridor&&(!AbilityRuntime.channeling(f.cast.player)||f.interrupted())){remove(f);continue;}
            if(now>=f.ends){if(f.follows)damage(f,f.ending);if(f.pulse>0&&now==f.ends)damage(f,f.pulse);remove(f);continue;}
            if(now<f.begins){if((now-f.begins)%3==0)KehengVisuals.warning(f.cast,f.center(),f.radius);continue;}
            if(!f.opened){f.opened=true;if(f.opening>0)damage(f,f.opening);KehengAudio.play(f.cast.player,"field");}
            if(f.pulse>0&&now>f.begins&&(now-f.begins)%10==0){damage(f,f.pulse);KehengVisuals.pulse(f.cast,f.center(),f.radius);KehengAudio.play(f.cast.player,"pulse");}
            if(now%3==0){if(f.corridor)KehengVisuals.corridor(f.cast,f.origin,f.origin.add(f.direction.scale(f.length())));else KehengVisuals.field(f.cast,f.center(),f.radius,f.follows);}
            boolean inside=!f.corridor&&f.contains(f.cast.player.position());if(inside&&!f.inside)ConstantStepSkill.entered(f.cast.player);f.inside=inside;
        }
        NEEDLES.removeIf(n->n.cast.player.level()!=n.target.level()||!n.target.isAlive()||now>=n.until||!AbilityRuntime.valid(n.cast.player));
        CLEANSED.entrySet().removeIf(e->e.getValue()<=now);
    }
    public static void needle(AbilityRuntime.Cast c,LivingEntity t,double rate){
        if(!(t instanceof ServerPlayer p)||!susceptible(p,c.now()))return;
        int duration=(int)Math.ceil(25*(1-Math.clamp(t.getAttributeValue(ModAttributes.TENACITY),0,60)/100));
        NEEDLES.add(new Needle(c,t,rate,c.now()+duration,"keheng_needle_"+c.castId()));refreshActor(p);
    }
    /** A cleanse grants only the specified fixed immunity to Keheng's action slow. */
    public static void cleanse(ServerPlayer p){NEEDLES.removeIf(n->n.target==p);CLEANSED.put(p.getUUID(),p.level().getGameTime()+15);refreshActor(p);}
    private static boolean susceptible(ServerPlayer p,long now){
        // Existing invulnerable control phases are supplied by these actor-owned flags; no new immunity ability is granted.
        var d=PlayerDataProvider.get(p);
        return p.isAlive()&&!p.isSpectator()&&CLEANSED.getOrDefault(p.getUUID(),0L)<=now&&d.getBuff("g1_unstoppable")==0&&d.getBuff("g1_control_immune")==0;
    }
    public static void refreshActor(ServerPlayer p){
        ACTORS.put(p.getUUID(),p);var previous=PUBLISHED.remove(p.getUUID());
        if(previous!=null)for(var source:previous)ActionTime.remove(p.getUUID(),source.owner,source.key);
        long now=p.level().getGameTime();var sources=new ArrayList<Published>();
        if(susceptible(p,now)){
            for(var n:NEEDLES)if(n.target==p&&now<n.until&&n.cast.player.level()==p.level()){
                ActionTime.apply(p.getUUID(),n.cast.player.getUUID(),n.key,n.rate,n.until);sources.add(new Published(n.cast.player.getUUID(),n.key));}
            for(var f:FIELDS)if(f.cast.player.level()==p.level()&&f.active(now)&&SkillTargets.enemy(f.cast.player,p)&&f.contains(f.corridor?p.getBoundingBox().getCenter():p.position())){
                ActionTime.apply(p.getUUID(),f.cast.player.getUUID(),f.key,f.actorRate,now+2);sources.add(new Published(f.cast.player.getUUID(),f.key));}
        }
        if(!sources.isEmpty())PUBLISHED.put(p.getUUID(),sources);
        double rate=ActionTime.multiplier(p.getUUID(),now);updateMovement(p,rate);
        var data=PlayerDataProvider.get(p);int display=rate<1?(int)Math.round(rate*100):0;boolean changed=data.getBuff("g1_keheng_rate")!=display;
        data.setBuff("g1_keheng_rate",display);if(changed&&p.connection!=null)PlayerDataProvider.syncToClient(p);
    }
    /** Take the stronger ordinary slow or local movement limit, while keeping stance/root/charge restrictions. */
    private static void updateMovement(ServerPlayer p,double rate){
        var attr=p.getAttribute(Attributes.MOVEMENT_SPEED);if(attr==null)return;attr.removeModifier(MOVE);
        double ordinary=AbilityRuntime.ordinarySlowRate(p);double factor=Math.min(rate,ordinary)/Math.max(.0001,ordinary);
        if(factor<1)attr.addTransientModifier(new AttributeModifier(MOVE,factor-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static void refreshPublished(){for(var p:new ArrayList<>(ACTORS.values()))if(!p.isRemoved())refreshActor(p);}
    public static double projectileRate(ServerPlayer owner,Vec3 at,long now){double rate=1;
        for(var f:FIELDS)if(f.cast.player.level()==owner.level()&&f.active(now)&&SkillTargets.enemy(f.cast.player,owner)&&f.contains(at))rate=Math.min(rate,f.flightRate);
        return rate;
    }
    public static void clear(ServerPlayer p){
        for(var f:new ArrayList<>(FIELDS))if(f.cast.player==p)remove(f);
        NEEDLES.removeIf(n->n.cast.player==p||n.target==p);CLEANSED.remove(p.getUUID());EngravedEchoSkill.clear(p);
        ModShieldSystem.removeSource(p,"keheng_hengbu/"+p.getUUID());ModShieldSystem.removeSource(p,"keheng_cuopaibu/"+p.getUUID());
        refreshPublished();var previous=PUBLISHED.remove(p.getUUID());if(previous!=null)for(var s:previous)ActionTime.remove(p.getUUID(),s.owner,s.key);
        var attr=p.getAttribute(Attributes.MOVEMENT_SPEED);if(attr!=null)attr.removeModifier(MOVE);ACTORS.remove(p.getUUID());
    }
    @SubscribeEvent public static void clock(ServerTickEvent.Pre e){for(var level:e.getServer().getAllLevels())tick(level);}
    @SubscribeEvent public static void movement(PlayerTickEvent.Pre e){if(e.getEntity() instanceof ServerPlayer p&&ModState.isEnabled())refreshActor(p);}
}
