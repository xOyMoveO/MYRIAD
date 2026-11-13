package com.myriad.character.skills.yike;

import com.myriad.character.ability.*;
import com.myriad.combat.*;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Free physical attack: a left-button flight, separate from every paid spell. */
public final class YikeBasicAttack {
    private static final class Recovery {ActionTime.Progress progress;int total;long tick;}
    private static final Map<UUID,Recovery> RECOVERY=new HashMap<>();
    private YikeBasicAttack(){}
    public static void tick(ServerPlayer p){var s=RECOVERY.get(p.getUUID());if(s==null)return;
        if(s.progress.advance(p.level().getGameTime(),ActionTime.multiplier(p.getUUID(),p.level().getGameTime()),s.total))RECOVERY.remove(p.getUUID());}
    public static int remaining(ServerPlayer p){var s=RECOVERY.get(p.getUUID());return s==null?0:(int)Math.ceil(s.total-s.progress.completed());}
    public static int total(ServerPlayer p){var s=RECOVERY.get(p.getUUID());return s==null?(int)Math.ceil(20/Math.max(.1,p.getAttributeValue(ModAttributes.ATTACK_SPEED))):s.total;}
    public static void tryFire(ServerPlayer p){
        var data=PlayerDataProvider.get(p);tick(p);
        if(!AbilityRuntime.valid(p)||!"yike".equals(data.getCharacterId())||!p.getMainHandItem().is(ModItems.HONG_LIE.get())
                ||YikeStance.active(p)||PrismaticBeam.active(p)||AbilityRuntime.channeling(p)||SkillChargeSystem.active(p)
                ||data.getBuff("g1_stun")>0||!BasicAttackSystem.canAttackNow(p,data)||remaining(p)>0)return;
        var s=new Recovery();s.total=total(p);s.progress=new ActionTime.Progress(p.level().getGameTime());RECOVERY.put(p.getUUID(),s);
        var c=AbilityRuntime.context(p,YikeRework.q(p));Vec3 direction=p.getLookAngle().normalize();
        Vec3[] pos={p.getEyePosition().add(direction.scale(.4))};double[] distance={0};long[] last={c.now()};UUID flight=UUID.randomUUID();int visual=c.newVisualInstance();
        float damage=(float)p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        YikeVfx.event(c,"basic_muzzle",pos[0],pos[0].add(direction),.2f,3);YikeAudio.play(p,"basic",pos[0],.35f,1.2f);PlayerDataProvider.syncToClient(p);
        c.whileActive(17,age->{
            if(c.now()<=last[0])return true;last[0]=c.now();if(age>16){ActionTime.clearTarget(flight);YikeVfx.finish(c,visual);return false;}
            double step=Math.min(10-distance[0],1.3*AbilityRuntime.projectileRate(p,flight,pos[0],c.now()));
            Vec3 from=pos[0],to=YikeGeometry.end(c,from,direction,step);
            var target=p.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(.12),t->SkillTargets.enemy(p,t)
                    &&t.getBoundingBox().inflate(.12).clip(from,to).isPresent()).stream().min(Comparator.comparingDouble(t->t.distanceToSqr(from))).orElse(null);
            if(target!=null){DamageCalc.dealBasicDamage(p,target,damage,DamageCalc.Kind.PHYSICAL);YikeVfx.event(c,"basic_hit",target.getBoundingBox().getCenter(),target.getBoundingBox().getCenter(),.25f,4);ActionTime.clearTarget(flight);YikeVfx.finish(c,visual);return false;}
            YikeVfx.basicFlight(c,from,to,visual);distance[0]+=from.distanceTo(to);pos[0]=to;
            if(from.distanceTo(to)+1E-5<step||distance[0]>=10-1E-5){ActionTime.clearTarget(flight);YikeVfx.finish(c,visual);return false;}return true;
        },()->{ActionTime.clearTarget(flight);YikeVfx.finish(c,visual);});
    }
    public static void clear(ServerPlayer p){RECOVERY.remove(p.getUUID());}
}
