package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.network.FlameSkillViewPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.concurrent.atomic.AtomicInteger;
import static com.myriad.network.FlameSkillViewPayload.*;

/** Per-role cosmetics only. Each independent skill still owns its gameplay and timing. */
public final class FlameSkillPresentation {
    private static final AtomicInteger SEQUENCE=new AtomicInteger();
    private FlameSkillPresentation(){}
    public static int play(Cast c,int action,int ticks){int token=SEQUENCE.updateAndGet(n->n==Integer.MAX_VALUE?1:n+1);play(c,action,ticks,token);return token;}
    public static void play(Cast c,int action,int ticks,int token){PacketDistributor.sendToPlayer(c.player,new FlameSkillViewPayload(action,ticks,c.now(),token));}
    public static void mobileBurst(Cast c,Runnable release){
        int token=play(c,BURST_READY,13);boolean[] resolved={false};
        c.later(13,true,.25f,()->{resolved[0]=true;play(c,BURST_FIRE,10,token);release.run();});
        c.whileActive(14,age->{if(resolved[0])return false;
            if(age>0&&!AbilityRuntime.channeling(c.player)){play(c,CANCEL,0,token);return false;}return true;});
    }
}
