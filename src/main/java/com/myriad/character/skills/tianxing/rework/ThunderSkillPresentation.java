package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.network.ThunderSkillViewPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.concurrent.atomic.AtomicInteger;

/** Role-owned first-person cue scheduling. Gameplay still belongs to each separate skill. */
public final class ThunderSkillPresentation {
    private static final AtomicInteger SEQUENCE=new AtomicInteger();
    private ThunderSkillPresentation(){}
    public static int play(Cast c,int action,int ticks){
        int token=SEQUENCE.updateAndGet(n->n==Integer.MAX_VALUE?1:n+1);play(c,action,ticks,token);return token;
    }
    public static void play(Cast c,int action,int ticks,int token){
        PacketDistributor.sendToPlayer(c.player,new ThunderSkillViewPayload(action,ticks,c.now(),token));
    }
    /** Same interruptible charge/lock as existing skills; the completion cue is never predicted. */
    public static void charged(Cast c,int ticks,int ready,int fire,Runnable release){
        ThunderAudio.play(c,ThunderAudio.Cue.CHARGE);
        int token=play(c,ready,ticks);boolean[] resolved={false};
        c.later(ticks,true,()->{resolved[0]=true;play(c,fire,10,token);release.run();});
        c.whileActive(ticks,age->{
            if(resolved[0])return false;
            if(age>0&&!AbilityRuntime.channeling(c.player)){play(c,ThunderSkillViewPayload.CANCEL,0,token);return false;}
            return true;
        });
    }
}
