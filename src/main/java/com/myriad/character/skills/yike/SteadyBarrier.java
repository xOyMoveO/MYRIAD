package com.myriad.character.skills.yike;

import com.myriad.character.ability.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Rolling horizontal path length, rather than net displacement or stance transitions. */
public final class SteadyBarrier extends AbilitySkill {
    private record Step(long time,double distance){}
    private static final class State {Vec3 position;long last=Long.MIN_VALUE,started,ready;final Deque<Step> path=new ArrayDeque<>();}
    private static final Map<UUID,State> STATES=new HashMap<>();
    public SteadyBarrier(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){}
    private static State sample(ServerPlayer p){
        long now=p.level().getGameTime();var s=STATES.computeIfAbsent(p.getUUID(),u->{var n=new State();n.started=now;n.position=p.position();return n;});
        if(now!=s.last){double distance=p.position().subtract(s.position).multiply(1,0,1).length();s.path.addLast(new Step(now,distance));s.position=p.position();s.last=now;}
        while(!s.path.isEmpty()&&s.path.peekFirst().time<=now-24)s.path.removeFirst();return s;
    }
    public static boolean ready(ServerPlayer p){var s=sample(p);return p.level().getGameTime()-s.started>=24&&p.level().getGameTime()>=s.ready&&s.path.stream().mapToDouble(Step::distance).sum()<=.300001;}
    public static int cooldown(ServerPlayer p){var s=STATES.get(p.getUUID());return s==null?0:(int)Math.max(0,s.ready-p.level().getGameTime());}
    @Override public void tickPassive(AbilityRuntime.Cast c){sample(c.player);}
    @Override public void onCast(AbilityRuntime.Cast cast){
        if(!cast.committed()||cast.skill.isPassive()||!ready(cast.player))return;
        var c=AbilityRuntime.context(cast.player,this);var s=sample(c.player);s.ready=c.now()+240;
        c.shield(c.player,c.value(65,15,.25f),50);YikeVfx.target(c,"barrier",c.player,.7f,50);YikeAudio.play(c.player,"barrier",c.player.position(),.4f,1);
    }
    public static void clear(ServerPlayer p){STATES.remove(p.getUUID());com.myriad.player.ModShieldSystem.removeSource(p,"yike_steady_barrier/"+p.getUUID());}
}
