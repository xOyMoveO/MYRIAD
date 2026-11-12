package com.myriad.client;

import com.myriad.combat.CombatImpactProfile;
import com.myriad.registry.ModSounds;
import net.minecraft.client.Minecraft;

/** Personal contact feedback follows the Players slider, never a global world sound. */
public final class CombatImpactAudio {
    private static java.lang.ref.WeakReference<Object> world=new java.lang.ref.WeakReference<>(null);
    private static long lastHit=Long.MIN_VALUE,lastHurt=Long.MIN_VALUE;
    private static boolean lastCritical;
    private CombatImpactAudio(){}
    private static boolean ready(){
        var mc=Minecraft.getInstance();
        if(world.get()!=mc.level){world=new java.lang.ref.WeakReference<>(mc.level);lastHit=lastHurt=Long.MIN_VALUE;lastCritical=false;}
        return ClientDataHolder.modEnabled()&&mc.player!=null&&mc.level!=null;
    }
    public static void receive(int kind){
        if(!ready()||!CombatImpactProfile.isPayload(kind))return;
        var mc=Minecraft.getInstance();long now=mc.level.getGameTime();
        boolean critical=((kind-CombatImpactProfile.PAYLOAD_BASE)&1)!=0;
        // Also cap bursts of queued network messages arriving in the same client tick.
        if(!CombatImpactProfile.allow(now,lastHit,lastCritical,critical,false))return;
        lastHit=now;lastCritical=critical;
        var profile=CombatImpactProfile.fromPayload(kind);
        float pitch=.97f+mc.player.getRandom().nextFloat()*.06f;
        mc.player.playSound(ModSounds.impact(profile),critical?.62f:profile==CombatImpactProfile.SHIELD?.44f:.48f,pitch);
    }
    public static void hurt(float ratio){
        if(!ready())return;
        var mc=Minecraft.getInstance();long now=mc.level.getGameTime();
        if(lastHurt!=Long.MIN_VALUE&&now>=lastHurt&&now-lastHurt<4)return;
        lastHurt=now;
        mc.player.playSound(ModSounds.impact(ratio>=.10f?CombatImpactProfile.CRITICAL:CombatImpactProfile.BLUNT),
                ratio>=.10f?.40f:.30f,.97f+mc.player.getRandom().nextFloat()*.06f);
    }
}
