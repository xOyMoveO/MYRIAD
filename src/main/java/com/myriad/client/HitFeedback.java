package com.myriad.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
public final class HitFeedback {
    private static long until;
    private static long started;
    private static boolean critical;
    public static void hit(boolean crit) {
        long now = System.nanoTime();
        if (!crit && critical && now < until) return;
        critical=crit; started=now; until=now+(crit ? 260_000_000L : 180_000_000L);
    }
    public static void draw(GuiGraphicsExtractor gui) {
        if (!ClientDataHolder.modEnabled() || System.nanoTime()>=until || Minecraft.getInstance().gui.screen()!=null) return;
        var window=Minecraft.getInstance().getWindow();int x=window.getGuiScaledWidth()/2,y=window.getGuiScaledHeight()/2;
        double progress=Math.min(1, (System.nanoTime()-started)/(double)(until-started));
        int alpha=(int)(255*(1-progress*progress));
        int color=(alpha<<24)|(critical?0xFFCA66:0xD8FFFF);
        for(int i=0;i<(critical?6:4);i++){
            int offset=5+i+(int)((1-progress)*(critical?4:2));
            gui.fill(x-offset,y-offset,x-offset+2,y-offset+2,color);
            gui.fill(x+offset,y-offset,x+offset+2,y-offset+2,color);
            gui.fill(x-offset,y+offset,x-offset+2,y+offset+2,color);
            gui.fill(x+offset,y+offset,x+offset+2,y+offset+2,color);
        }
    }
}
