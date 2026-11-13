package com.myriad.character.skills.yike;

import com.myriad.character.ability.*;

/** Paid on legal confirmation; an interrupted charge never refunds the ultimate. */
public final class DistantBombardment extends AbilitySkill {
    private static final java.util.Map<java.util.UUID,AbilityRuntime.Cast> CHARGES=new java.util.HashMap<>();
    public static void tick(net.minecraft.server.level.ServerPlayer p){
        var c=CHARGES.get(p.getUUID());if(c==null)return;
        if(c.phase()!=AbilityRuntime.Phase.WINDUP||!SkillChargeSystem.active(p)){CHARGES.remove(p.getUUID());return;}
        YikeVfx.event(c,"heavy_warning",c.point.add(0,.06,0),c.point.add(0,.06,0),4,2);
    }
    public static void clear(net.minecraft.server.level.ServerPlayer p){CHARGES.remove(p.getUUID());}
    public DistantBombardment(Definition d){super(d);}
    @Override public boolean needsPoint(){return true;}
    @Override public double pointRange(AbilityRuntime.Cast c){return 26;}
    @Override public double previewRadius(AbilityRuntime.Cast c){return 4;}
    @Override public boolean canRelease(AbilityRuntime.Cast c){return !PrismaticBeam.active(c.player);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void release(AbilityRuntime.Cast c){
        YikeStance.exit(c.player,"ultimate");
        YikeVfx.event(c,"heavy_warning",c.point.add(0,.06,0),c.point.add(0,.06,0),4,2);
        YikeAudio.play(c.player,"heavy_charge",c.player.position(),.65f,.85f);
        CHARGES.put(c.player.getUUID(),c);
        c.windup(12,0,()->{CHARGES.remove(c.player.getUUID());YikeProjectiles.heavy(c,c.point,c.value(250,85,1.15f));});
    }
}
