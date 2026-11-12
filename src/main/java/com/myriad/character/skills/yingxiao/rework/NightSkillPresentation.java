package com.myriad.character.skills.yingxiao.rework;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.network.NightSkillViewPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Called by the individual skill only after actual authoritative release/recast/arrival. */
public final class NightSkillPresentation {
    private NightSkillPresentation(){}
    public static void play(Cast c,int action,int ticks){
        PacketDistributor.sendToPlayer(c.player,new NightSkillViewPayload(action,ticks,c.now(),c.castId()));
    }
    public static void cancel(Cast c){play(c,NightSkillViewPayload.CANCEL,0);}
}
