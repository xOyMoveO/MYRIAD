package com.myriad.character.skills.yike;

import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** One paid cast can mark each hero only once, even after the third mark clears the stack. */
public final class RainbowCalibration extends AbilitySkill {
    private static final class Mark {int count;long until;final Set<Integer> casts=new HashSet<>();}
    private static final Map<UUID,Map<UUID,Mark>> MARKS=new HashMap<>();
    public RainbowCalibration(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){}
    @Override public void onHit(AbilityRuntime.Hit hit){
        if(hit.basic()||!hit.direct()||!hit.skillId().startsWith("yike_")||AbilityRuntime.sourceCast()<=0)return;
        var paid=FirstGroupSkills.byId(hit.skillId());if(paid==null||paid.getManaCost()<=0)return;
        var c=hit.passiveContext();if(c==null||!c.hero(hit.target()))return;
        var targets=MARKS.computeIfAbsent(c.player.getUUID(),u->new HashMap<>());
        var m=targets.computeIfAbsent(hit.target().getUUID(),u->new Mark());
        if(!m.casts.add(AbilityRuntime.sourceCast()))return;
        if(c.now()>=m.until)m.count=0;m.until=c.now()+120;
        if(++m.count>=3){m.count=0;
            c.damage(hit.target(),c.value(40,10,.20f),DamageCalc.Kind.MAGIC,false);
            YikeVfx.target(c,"mark_burst",hit.target(),.7f,8);YikeAudio.play(c.player,"mark",hit.target().position(),.45f,1.25f);
        }else YikeVfx.target(c,"mark_"+m.count,hit.target(),.7f,12);
    }
    @Override public void tickPassive(AbilityRuntime.Cast c){
        var targets=MARKS.get(c.player.getUUID());if(targets==null)return;
        // Keep cast identities until ordinary lifecycle cleanup, so expired/cleared stacks do not re-mark a long cast.
        for(var e:targets.entrySet())if(e.getValue().count>0){
            if(c.now()>=e.getValue().until)e.getValue().count=0;
            else if(c.now()%6==0&&c.player.level().getEntity(e.getKey()) instanceof net.minecraft.world.entity.LivingEntity target)
                YikeVfx.target(c,"mark_"+e.getValue().count,target,.7f,8);
        }
    }
    public static int count(ServerPlayer p,UUID target){var m=MARKS.getOrDefault(p.getUUID(),Map.of()).get(target);return m==null||p.level().getGameTime()>=m.until?0:m.count;}
    public static void clear(ServerPlayer p){MARKS.remove(p.getUUID());}
}
