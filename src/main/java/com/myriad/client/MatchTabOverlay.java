package com.myriad.client;

import com.myriad.network.MatchTabPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.Comparator;
import java.util.List;

public final class MatchTabOverlay {
    private static MatchTabPayload snapshot;
    private static net.minecraft.client.multiplayer.ClientLevel snapshotLevel;
    public static List<MatchTabPayload.Entry> players(){return snapshot!=null && snapshotLevel==Minecraft.getInstance().level?snapshot.players():List.of();}
    public static void update(MatchTabPayload packet) { snapshot = packet; snapshotLevel = Minecraft.getInstance().level; }
    public static boolean active() {
        var mc = Minecraft.getInstance();
        // 对局开始前保留原版 Tab：只显示当前在线玩家，不显示阵营、角色、经济和战绩。
        // 本方法同时被 PlayerTabOverlayMixin 用来决定是否隐藏原版列表。
        var phase = ClientDataHolder.phase();
        boolean matchTab = phase == com.myriad.game.GameManager.Phase.PLAYING
                || phase == com.myriad.game.GameManager.Phase.ENDED;
        return ClientDataHolder.modEnabled() && matchTab && mc.player != null
                && mc.gui.screen() == null && mc.options.keyPlayerList.isDown();
    }
    public static void render(GuiGraphicsExtractor gui, Font font) {
        if (!active()) return;
        var mc = Minecraft.getInstance();
        var data = snapshotLevel == mc.level ? snapshot : null;
        List<MatchTabPayload.Entry> entries = data == null ? List.of() : data.players();
        var left = entries.stream().filter(e -> e.faction() == 1).sorted(Comparator.comparing(MatchTabPayload.Entry::name)).toList();
        var right = entries.stream().filter(e -> e.faction() == 2).sorted(Comparator.comparing(MatchTabPayload.Entry::name)).toList();
        var waiting = entries.stream().filter(e -> e.faction() == 0).toList();
        int rows = Math.max(1, Math.max(left.size(), right.size()));
        int h = 66 + rows * 25 + waiting.size() * 12;
        float scale = Math.min(1F, Math.min((mc.getWindow().getGuiScaledWidth()-16)/480F, (mc.getWindow().getGuiScaledHeight()-24)/(float)h));
        gui.pose().pushMatrix(); gui.pose().scale(scale);
        int x = (int)(mc.getWindow().getGuiScaledWidth()/scale-480)/2, y = 12;
        XianxiaUiSkin.tabPanel(gui,x,y,480,h);
        gui.fill(x+239,y+8,x+240,y+35+rows*25,0xFF82805E);
        title(gui,font,Component.translatable("faction.myriad.xuanxiao").getString(),x+120,y+10,0xFFFF7676);
        title(gui,font,Component.translatable("faction.myriad.youming").getString(),x+360,y+10,0xFF70C9FF);
        int leftGold = left.stream().mapToInt(MatchTabPayload.Entry::gold).sum();
        int rightGold = right.stream().mapToInt(MatchTabPayload.Entry::gold).sum();
        title(gui,font,Component.translatable("tab.myriad.team_gold", leftGold).getString(),x+120,y+21,0xFFFFD570);
        title(gui,font,Component.translatable("tab.myriad.team_gold", rightGold).getString(),x+360,y+21,0xFFFFD570);
        column(gui,font,left,x+10,y+32); column(gui,font,right,x+250,y+32);
        int bottom=y+35+rows*25;
        for(var e:waiting) { title(gui,font,e.name(),x+240,bottom,0xFFAAB4C2); bottom+=12; }
        String time = data == null || data.elapsedTicks()<0 ? Component.translatable("tab.myriad.waiting").getString()
                : Component.translatable("tab.myriad.time", formatTime(data.elapsedTicks())).getString();
        gui.fill(x+28,bottom-3,x+452,bottom-2,0xFF82805E);
        title(gui,font,time,x+240,bottom+7,0xFFFFD570);
        gui.pose().popMatrix();
    }
    public static String formatTime(long ticks) {
        long seconds=Math.max(0,ticks)/20;
        return String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
    }
    private static void column(GuiGraphicsExtractor g,Font f,List<MatchTabPayload.Entry> es,int x,int y) {
        for(var e:es) {
            g.text(f,f.plainSubstrByWidth(e.name(),155),x,y,0xFFF4F4F4);
            g.text(f,e.kills()+" / "+e.deaths(),x+145,y,0xFFE2C587);
            g.text(f,Component.translatable("tab.myriad.gold_short", e.gold()),x+184,y,0xFFFFD570);
            String character = e.character().isEmpty() ? "—" : Component.translatable("character.myriad."+e.character()).getString();
            g.text(f,f.plainSubstrByWidth(character+" · Lv."+e.level(),216),x,y+11,0xFF9DB1C8); y+=25;
        }
    }
    private static void title(GuiGraphicsExtractor g,Font f,String s,int x,int y,int c) { g.text(f,s,x-f.width(s)/2,y,c); }
}
