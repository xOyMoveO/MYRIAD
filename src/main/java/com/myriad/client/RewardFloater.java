package com.myriad.client;
import com.myriad.network.RewardFloatPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
public final class RewardFloater {
    private record Entry(RewardFloatPayload reward,long born,net.minecraft.client.multiplayer.ClientLevel level){}
    private static final java.util.List<Entry> ENTRIES=new java.util.ArrayList<>();
    public static void add(RewardFloatPayload p){var level=Minecraft.getInstance().level;if(level!=null){ENTRIES.add(new Entry(p,level.getGameTime(),level));if(ENTRIES.size()>32)ENTRIES.removeFirst();}}
    public static void render(GuiGraphicsExtractor gui,Font font){
        var mc=Minecraft.getInstance(); if(mc.level==null)return;
        long now=mc.level.getGameTime(); ENTRIES.removeIf(e->e.level!=mc.level||now-e.born>=20);
        for(var e:ENTRIES){var r=e.reward;var point=new Vec3(r.x(),r.y()+(now-e.born)*.025,r.z());
            if(point.subtract(mc.gameRenderer.mainCamera().position()).dot(new Vec3(mc.gameRenderer.mainCamera().forwardVector()))<=0)continue;
            var p=mc.gameRenderer.projectPointToScreen(point);
            int x=(int)((p.x+1)*.5*mc.getWindow().getGuiScaledWidth()),y=(int)((1-p.y)*.5*mc.getWindow().getGuiScaledHeight());
            String gold=Component.translatable("hud.myriad.reward_gold",r.gold()).getString();
            String xp=Component.translatable("hud.myriad.reward_xp",r.xp()).getString();
            if(r.gold()>0)gui.text(font,gold,x-font.width(gold)/2,y,0xFFFFD34F);
            if(r.xp()>0)gui.text(font,xp,x-font.width(xp)/2,y+11,0xFF91E6FF);
        }
    }
}
