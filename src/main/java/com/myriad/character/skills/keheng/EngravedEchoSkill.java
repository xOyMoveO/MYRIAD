package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
import com.myriad.combat.DamageCalc;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
public final class EngravedEchoSkill extends KehengSkill {
    private static final WeakHashMap<ServerPlayer,Map<UUID,Mark>> STATES=new WeakHashMap<>();
    private static final class Mark {int stacks;long until;final Set<Integer> seen=new HashSet<>();}
    public EngravedEchoSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){}
    @Override public void onHit(AbilityRuntime.Hit hit){
        if(hit.basic()||!hit.direct()||hit.skillId().isEmpty()||AbilityRuntime.sourceCast()==0)return;
        var c=AbilityRuntime.context(hit.player(),this);if(!c.hero(hit.target()))return;
        var m=STATES.computeIfAbsent(hit.player(),p->new HashMap<>()).computeIfAbsent(hit.target().getUUID(),id->new Mark());
        if(!m.seen.add(AbilityRuntime.sourceCast()))return;
        if(c.now()>=m.until){m.stacks=0;m.until=c.now()+80;}
        c.memory().target=hit.target().getUUID();m.stacks++;KehengVisuals.mark(c,hit.target(),m.stacks);
        if(m.stacks==3){m.stacks=0;m.until=0;c.damage(hit.target(),c.value(50,12,.25f),DamageCalc.Kind.MAGIC,false);KehengAudio.play(c.player,"echo");}
        c.buff("g1_keheng_marks",m.stacks);c.buff("g1_keheng_mark_window",(int)Math.max(0,m.until-c.now()));
    }
    @Override public void tickPassive(AbilityRuntime.Cast c){
        var marks=STATES.get(c.player);if(marks==null)return;
        for(var entry:marks.entrySet())if(entry.getValue().stacks>0&&entry.getValue().until>c.now()&&c.now()%3==0
                &&c.player.level().getEntity(entry.getKey()) instanceof net.minecraft.world.entity.LivingEntity target)KehengVisuals.mark(c,target,entry.getValue().stacks);
        var m=marks.get(c.memory().target);c.buff("g1_keheng_marks",m==null||m.until<=c.now()?0:m.stacks);
        c.buff("g1_keheng_mark_window",m==null?0:(int)Math.max(0,m.until-c.now()));
    }
    public static void clear(ServerPlayer p){STATES.remove(p);}
}
