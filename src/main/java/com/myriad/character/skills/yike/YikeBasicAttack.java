package com.myriad.character.skills.yike;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;

import com.myriad.combat.InputFailure;

import com.myriad.character.ability.*;
import com.myriad.combat.*;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.*;
import net.minecraft.server.level.ServerPlayer;
import com.myriad.game.DefenseTowerEntity;
import net.minecraft.world.phys.*;
import java.util.*;

/** Free physical attack: a left-button flight, separate from every paid spell. */
public final class YikeBasicAttack {
    public static final double RANGE = 10;
    private static final double FLIGHT_SPEED = 1.3 * HeroProjectileTuning.SPEED;
    private static final int MAX_FLIGHT_TICKS = (int) Math.ceil(RANGE / (FLIGHT_SPEED * .30)) + 2;
    private static final double HIT_RADIUS = .12 * HeroProjectileTuning.ENTITY_PADDING;
    private static final class Recovery {ActionTime.Progress progress;int total;long tick;}
    private static final Map<UUID,Recovery> RECOVERY=new HashMap<>();
    private YikeBasicAttack(){}
    public static void tick(ServerPlayer p){var s=RECOVERY.get(p.getUUID());if(s==null)return;
        if(s.progress.advance(p.level().getGameTime(),ActionTime.multiplier(p.getUUID(),p.level().getGameTime()),s.total))RECOVERY.remove(p.getUUID());}
    public static int remaining(ServerPlayer p){var s=RECOVERY.get(p.getUUID());return s==null?0:(int)Math.ceil(s.total-s.progress.completed());}
    public static int recoveryTicks(double attackSpeed) { return (int) Math.ceil(20 / Math.max(.1, attackSpeed)); }
    public static int total(ServerPlayer p){var s=RECOVERY.get(p.getUUID());return s==null?recoveryTicks(p.getAttributeValue(ModAttributes.ATTACK_SPEED)):s.total;}
    public static void tryFire(ServerPlayer p){
        var data=PlayerDataProvider.get(p);tick(p);
        if(!AbilityRuntime.valid(p)||!"yike".equals(data.getCharacterId())||!p.getMainHandItem().is(ModItems.HONG_LIE.get())
                ||!BasicAttackSystem.canAttackNow(p,data))return;
        if(data.getBuff("g1_stun")>0){InputFailure.STUN.send(p);return;}
        if(AbilityRuntime.channeling(p)||SkillChargeSystem.active(p)||PrismaticBeam.active(p)){InputFailure.CHANNEL.send(p);return;}
        if(YikeStance.active(p)){InputFailure.LINK.send(p);return;}
        if(remaining(p)>0){InputFailure.RECOVERING.send(p);return;}
        var s=new Recovery();s.total=total(p);s.progress=new ActionTime.Progress(p.level().getGameTime());RECOVERY.put(p.getUUID(),s);
        var c=AbilityRuntime.context(p,YikeRework.q(p));Vec3 direction=p.getLookAngle().normalize();
        Vec3 origin=p.getEyePosition();
        Vec3[] pos={origin.add(direction.scale(.4))};double[] distance={0};long[] last={c.now()};UUID flight=UUID.randomUUID();int visual=c.newVisualInstance();
        float damage=(float)p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        WeaponPosePayload.play(p, Move.CANNON_BASIC, 5);
        YikeVfx.event(c,"basic_muzzle",pos[0],pos[0].add(direction),.2f,3);YikeAudio.play(p,"basic",pos[0],.35f,1.2f);PlayerDataProvider.syncToClient(p);
        c.whileActive(MAX_FLIGHT_TICKS,age->{
            if(c.now()<=last[0])return true;last[0]=c.now();
            Vec3 from = pos[0];
            if (distance[0] == 0 && ProjectileBasicAttack.clip(p, origin, from).blocked()) {
                ActionTime.clearTarget(flight);
                YikeVfx.finish(c, visual);
                return false;
            }
            double remaining = RANGE - distance[0];
            double step = Math.min(remaining, FLIGHT_SPEED * AbilityRuntime.projectileRate(p, flight, from, c.now()));
            var segment = ProjectileBasicAttack.clip(p, from, from.add(direction.scale(step)));
            Vec3 to = segment.end();
            var candidates = segment.candidates(HIT_RADIUS, t -> !(t instanceof DefenseTowerEntity));
            var baselineQuery = new AABB(from, to).inflate(HIT_RADIUS);
            var contact = candidates.stream().filter(t -> baselineQuery.intersects(t.getBoundingBox()))
                    .map(t -> segment.contact(t.getBoundingBox(),HIT_RADIUS,remaining)
                            .map(at -> new ProjectileBasicAttack.Hit(t,at)))
                    .flatMap(Optional::stream)
                    .min(Comparator.comparingDouble(hit -> hit.point().distanceToSqr(from))).orElse(null);
            var target = contact == null ? null : contact.target();
            if (target == null) {
                var assisted = ProjectileBasicAttack.firstAssistedHit(segment, candidates, HIT_RADIUS, remaining);
                if (assisted != null) target = assisted.target();
            }
            if(target!=null){DamageCalc.dealBasicDamage(p,target,damage,DamageCalc.Kind.PHYSICAL);YikeVfx.event(c,"basic_hit",target.getBoundingBox().getCenter(),target.getBoundingBox().getCenter(),.25f,4);ActionTime.clearTarget(flight);YikeVfx.finish(c,visual);return false;}
            YikeVfx.basicFlight(c,from,to,visual);distance[0]+=from.distanceTo(to);pos[0]=to;
            if(segment.blocked()||distance[0]>=RANGE-1E-5){ActionTime.clearTarget(flight);YikeVfx.finish(c,visual);return false;}return true;
        },()->{ActionTime.clearTarget(flight);YikeVfx.finish(c,visual);});
    }
    public static void clear(ServerPlayer p){RECOVERY.remove(p.getUUID());}
}
