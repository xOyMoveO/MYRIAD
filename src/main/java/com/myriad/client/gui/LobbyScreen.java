package com.myriad.client.gui;

import com.myriad.client.HudLayoutConfig;
import com.myriad.client.LobbyClient;
import com.myriad.network.LobbyNetworking;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Lobby and queue share navigation so closing a screen never withdraws a player. */
public final class LobbyScreen extends Screen {
    private final boolean afterRemake;
    private String shownOffer = "";
    private final List<Button> actions = new ArrayList<>();
    private boolean queued, training, showProposal;
    private Button main, propose, train;
    private int scroll, maximum;
    private String previewArena = "";
    private Identifier previewTexture;
    private Component keyConflict;
    private int left, top, panelWidth, bodyHeight, actionWidth;
    private boolean wide;

    public LobbyScreen() { this(false); }
    public LobbyScreen(boolean afterRemake) {
        super(label(afterRemake ? "remake.result" : "title"));
        this.afterRemake = afterRemake;
    }
    private static Component label(String key, Object... args) { return Component.translatable("lobby.myriad."+key, args); }
    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        keyConflict = LobbyClient.conflict();
        var s = LobbyClient.snapshot();
        queued = s != null && s.position() > 0;
        training = s != null && s.training();
        showProposal = queued && s.capacity() == 6;
        wide = width >= 310;
        panelWidth = Math.max(120, Math.min(660, width - 20));
        left = (width-panelWidth)/2;
        int panelHeight = Math.min(235, height - 12);
        top = (height-panelHeight)/2;
        bodyHeight = Math.max(24,panelHeight - 63);
        actionWidth = wide ? Math.min(198,panelWidth*36/100) : panelWidth-24;
        int footer = top + panelHeight - 26;
        int motionW = Math.min(137, (panelWidth-28)/2);
        actions.clear();
        String joinLabel = afterRemake ? "results.requeue" : "join";
        main = action(queued ? "leave" : joinLabel, () -> LobbyClient.send(queued ? "leave" : "join",""));
        shownOffer = s == null ? "" : s.remakeOfferId();
        if (!shownOffer.isEmpty()) {
            String offer = shownOffer;
            action("remake.action", () -> LobbyClient.send("remake", offer));
        }
        propose = showProposal ? action("propose", () -> LobbyClient.send("propose","")) : null;
        train = action(training ? "training_controls" : "train", () -> {
            if (training) Minecraft.getInstance().setScreenAndShow(new TrainScreen(this));
            else LobbyClient.send("train", "");
        });
        action("guide", () -> Minecraft.getInstance().setScreenAndShow(new CharacterSelectScreen(this,true)));
        addRenderableWidget(LobbyTheme.button(left+12,footer,motionW,19,motionLabel(),b -> {
            HudLayoutConfig.toggleMenuAnimations();
            b.setMessage(motionLabel());
        }));
        int doneWidth = Math.min(74,(panelWidth-32)/2);
        addRenderableWidget(LobbyTheme.button(left+panelWidth-12-doneWidth,footer,doneWidth,19,afterRemake ? label("results.lobby") : Component.translatable("gui.done"),b -> onClose()));
        update();
    }

    private Component motionLabel() {
        return label("motion", Component.translatable(LobbyClient.reducedMotion() ? "options.off" : "options.on"));
    }

    private Button action(String text, Runnable press) {
        var b = addRenderableWidget(LobbyTheme.button(left+12,0,actionWidth,22,label(text),ignored -> press.run()));
        actions.add(b);
        return b;
    }

    public void update() {
        if (main == null) return;
        var s = LobbyClient.snapshot();
        String newArena = s == null ? "" : s.position() > 0 ? s.queuedArena() : s.arena();
        if (!previewArena.equals(newArena)) {
            previewArena = newArena;
            var resource = Identifier.fromNamespaceAndPath("myriad","textures/gui/lobby/map_"+newArena+".png");
            previewTexture = Minecraft.getInstance().getResourceManager().getResource(resource).isPresent() ? resource : null;
        }
        if (((s != null && s.position() > 0) != queued) || ((s != null && s.training()) != training)
                || !shownOffer.equals(s == null ? "" : s.remakeOfferId())
                || (queued && (s.capacity()==6) != showProposal)) {
            boolean enteredTraining = s != null && s.training() && !training;
            if (enteredTraining) {
                Minecraft.getInstance().setScreenAndShow(new TrainScreen(this));
            } else {
                scroll = 0;
                rebuildWidgets();
            }
            return;
        }
        main.active = s != null && (queued || !s.participant() && !"disabled".equals(s.reason()));
        train.active = s != null && !s.participant() && !"disabled".equals(s.reason());
        if (propose != null) propose.active = s.canPropose();
        // With a narrow viewport the summary follows the actions. Both scroll as one.
        int textWidth = wide ? panelWidth-actionWidth-48 : panelWidth-42;
        int summaryH = mapHeight()+9;
        for (var row : rows(s)) summaryH += 3 + font.split(row, Math.max(12,textWidth)).size()*11;
        int actionsH = actions.size()*27;
        int total = wide ? Math.max(actionsH,summaryH) : actionsH+summaryH+8;
        maximum = Math.max(0,total-bodyHeight);
        scroll = Math.clamp(scroll,0,maximum);
        int bodyY = top+32;
        for (int i=0;i<actions.size();i++) {
            var button = actions.get(i);
            int y = bodyY+i*27-scroll;
            button.setPosition(left+12,y);
            button.visible = y >= bodyY && y+22 <= bodyY+bodyHeight;
        }
    }

    private int mapHeight() { return Math.clamp(bodyHeight/3,38,66); }

    private void scrollBy(int pixels) {
        scroll = Math.clamp(scroll+pixels,0,maximum);
        update();
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (dy == 0) return super.mouseScrolled(x,y,dx,dy);
        scrollBy(-(int)Math.round(dy*27));
        return true;
    }

    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int step = Math.max(1,bodyHeight/27)*27;
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) { scrollBy(-step); return true; }
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN) { scrollBy(step); return true; }
        return super.keyPressed(event);
    }

    private List<Component> rows(LobbyNetworking.Snapshot s) {
        List<Component> result = new ArrayList<>();
        if (s == null) {
            result.add(label("sync"));
        } else {
            var remake = LobbyClient.remakeNotice(s);
            if (remake != null) result.add(remake);
            result.add(label("count", s.count(), s.capacity()));
            if (queued) result.add(label("position", s.position(), s.waitedSeconds()/60, String.format(java.util.Locale.ROOT,"%02d",s.waitedSeconds()%60)));
            if (s.voteSeconds() >= 0) result.add(label("proposal",s.count()/2,s.count()/2,s.voteSeconds()));
            if (!s.reason().isEmpty()) result.add(label("reason."+s.reason()));
            if (queued && !showProposal && s.voteSeconds() < 0) result.add(label("full_only"));
            if (queued && showProposal && s.voteSeconds() < 0 && s.reason().isEmpty()) result.add(label("early_rule"));
        }
        if (keyConflict != null) result.add(keyConflict);
        return result;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        LobbyTheme.background(g,false,width,height);
        if (wide) {
            LobbyTheme.panel(g,false,left,top,actionWidth+22,Math.min(bodyHeight+32,38+actions.size()*27));
            LobbyTheme.panel(g,false,left+actionWidth+21,top+24,panelWidth-actionWidth-21,bodyHeight+12);
        } else {
            LobbyTheme.panel(g,false,left,top,panelWidth,bodyHeight+63);
        }
        g.text(font,queued ? label("queue") : title,left+13,top+12,LobbyTheme.GOLD);
        int bodyY = top+32;
        g.enableScissor(left+8,bodyY,left+panelWidth-8,bodyY+bodyHeight);
        int summaryX = wide ? left+actionWidth+25 : left+12;
        int summaryY = bodyY-scroll+(wide ? 0 : actions.size()*27+8);
        int summaryWidth = wide ? panelWidth-actionWidth-38 : actionWidth;
        var s = LobbyClient.snapshot();
        String arena = s == null ? "" : queued ? s.queuedArena() : s.arena();
        drawMap(g,arena,summaryX,summaryY,summaryWidth,mapHeight());
        int y = summaryY+mapHeight()+6;
        for (var row : rows(s)) {
            for (var line : font.split(row,Math.max(12,summaryWidth-10))) {
                g.text(font,line,summaryX+3,y,LobbyTheme.TEXT);
                y += 11;
            }
            y += 3;
        }
        g.disableScissor();
        if (maximum>0) {
            int trackX=left+panelWidth-8, thumb=Math.max(10,bodyHeight*bodyHeight/(bodyHeight+maximum));
            g.fill(trackX,bodyY,trackX+2,bodyY+bodyHeight,0x80415858);
            int thumbY=bodyY+(bodyHeight-thumb)*scroll/maximum;
            g.fill(trackX,thumbY,trackX+2,thumbY+thumb,LobbyTheme.JADE);
        }
        super.extractRenderState(g,mx,my,partial);
    }

    private void drawMap(GuiGraphicsExtractor g,String arena,int x,int y,int w,int h) {
        g.fill(x,y,x+w,y+h,0xFF13282C);
        if (!arena.isEmpty()) {
            if (previewTexture != null) {
                g.blit(previewTexture,x,y,x+w,y+h,0,1,0,1);
            } else {
                int a=x+12,b=x+w-13,near=y+h-13,far=y+8;
                for (int i=a;i<=b;i++) {
                    int laneY=near+(far-near)*(i-a)/Math.max(1,b-a);
                    g.fill(i,laneY,i+1,laneY+2,0xFF789F92);
                }
                if ("5v5".equals(arena)) {
                    g.fill(a,far,b,far+1,0xFF64796F); g.fill(a,far,a+1,near,0xFF64796F);
                    g.fill(a,near,b,near+1,0xFF64796F); g.fill(b,far,b+1,near,0xFF64796F);
                }
                g.fill(a-2,near-2,a+3,near+3,0xFF85C8E1);
                g.fill(b-2,far-2,b+3,far+3,0xFFE18E74);
            }
            Component name=Component.translatable("arena.myriad."+arena);
            g.fill(x,y+h-13,x+w,y+h,0xD1102024);
            g.text(font,font.plainSubstrByWidth(name.getString(),w-8),x+4,y+h-11,LobbyTheme.GOLD);
        }
    }
}
