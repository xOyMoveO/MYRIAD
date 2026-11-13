package com.myriad.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import com.myriad.character.CharacterType;
import com.myriad.client.ClientDataHolder;
import com.myriad.game.Faction;
import com.myriad.network.NetworkSetup;

/**
 * 开局角色选择界面：
 * 双方共享全部角色池；中央使用自适应网格显示未选角色，被选角色缩小移到屏幕两侧。
 * 仅当前轮到者可选择尚未被占用的角色；选择期间不可退出。
 */
public class PickScreen extends Screen {
    private static final int AVATAR_SIZE = 48;
    private static final int PANEL_TOP = 66;
    private static final int TEXT_COLOR = 0xFFE5EBF4;
    private static final int TITLE_COLOR = 0xFFFFDCA0;
    private static final int HIGHLIGHT_COLOR = 0xFFE9C587;
    private static final int TAKEN_COLOR = 0xFF9E9E9E;
    private static final int BG_COLOR = 0xF50A121E;
    private static final int PANEL_BORDER = 0xFFB0B0B0;
    private static final int COLOR_XUANXIAO = 0xFFEC7975;
    private static final int COLOR_YOUMING = 0xFF6BC8E7;
    /** 收尾/入边动画：被选角色缩小移到屏幕两侧竖排（玄霄宗左/幽冥宗右） */
    private static final int TARGET_SIZE = 30;
    private static final int TARGET_GAP = 12;
    private static final float ANIM_TICKS = 24f;

    /** 正在播放“移向屏幕边缘”动画的角色与起始游戏刻 */
    private String animPickedId = null;
    private long animStartTick = -1;
    private final java.util.Set<String> prevPicked = new java.util.HashSet<>();

    private record AvatarSlot(int x, int y, int size) {}
    private int rosterWidth(){return Math.min(154,Math.max(68,width/4));}

    public PickScreen() {
        super(Component.translatable("screen.myriad.pick.title"));
        com.myriad.Myriad.LOGGER.info("[pick-screen] opened");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // 选人阶段不可退出；结束后允许关闭
        if (!ClientDataHolder.pickActive()) {
            super.onClose();
        }
    }

    /** 已播放提示音的倒计时秒数（用于每秒触发一次音效） */
    private int lastCountdownSecond = -1;

    /** 选人结束（收到 state=1 或状态清空）后自动关闭界面；倒计时期间每秒播放提示音，结束时播放开始音 */
    @Override
    public void tick() {
        NetworkSetup.PickStatePayload state = ClientDataHolder.pickState();
        if (state == null || state.state() != 0) {
            com.myriad.Myriad.LOGGER.info("[pick-screen] tick closing: state={}",
                    state == null ? "null" : state.state());
            if (lastCountdownSecond > 0) {
                playCountdownGo();
            }
            lastCountdownSecond = -1;
            super.onClose();
            return;
        }
        if (state.countdownTick() >= 0) {
            long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
            int cd = (int) Math.max(0, (state.countdownTick() - now) / 20);
            if (cd != lastCountdownSecond) {
                if (cd > 0) {
                    playCountdownTick(cd);
                } else {
                    playCountdownGo();
                }
                lastCountdownSecond = cd;
            }
        } else {
            lastCountdownSecond = -1;
        }
    }

    /** 倒计时每秒一声短促提示音；最后 3 秒音调升高制造紧迫感 */
    private void playCountdownTick(int cd) {
        float pitch = cd <= 3 ? 1.6f : 1.0f;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), pitch, 0.8f));
    }

    /** 倒计时结束：开局提示音 */
    private void playCountdownGo() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 1.2f));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        return super.keyPressed(event);
    }

    private static List<CharacterType> charsOf(Faction faction) {
        List<CharacterType> result = new ArrayList<>();
        for (CharacterType t : CharacterType.playableValues()) {
            result.add(t);
        }
        return result;
    }

    /** 全角色共享池的响应式网格位置，保证头像与名称始终处于屏幕可见范围。 */
    private AvatarSlot avatarSlot(CharacterType target) {
        CharacterType[] all = CharacterType.playableValues();
        int count = all.length;
        int sideMargin = rosterWidth()+14;
        int usableW = Math.max(80, this.width - sideMargin * 2);
        int usableH = Math.max(60, this.height - PANEL_TOP - 14);
        int cols = Math.min(count, Math.max(1, usableW / 104));
        while (cols < count) {
            int rows = (count + cols - 1) / cols;
            if (usableH / rows >= 38) break;
            cols++;
        }
        int rows = (count + cols - 1) / cols;
        int cellW = Math.max(1, usableW / cols);
        int cellH = Math.max(1, usableH / rows);
        int size = Math.max(12, Math.min(AVATAR_SIZE, Math.min(cellW - 8, cellH - 14)));
        int gridW = cellW * cols;
        int gridH = cellH * rows;
        int startX = (this.width - gridW) / 2;
        int startY = PANEL_TOP + Math.max(0, (usableH - gridH) / 2);
        int index = Math.max(0, java.util.Arrays.asList(all).indexOf(target));
        int col = index % cols;
        int row = index / cols;
        int x = startX + col * cellW + (cellW - size) / 2;
        int y = startY + row * cellH;
        return new AvatarSlot(x, y, size);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gui, mouseX, mouseY, partialTick);
        NetworkSetup.PickStatePayload state = ClientDataHolder.pickState();
        if (state == null || state.state() != 0) {
            return;
        }
        com.myriad.client.XianxiaUiSkin.pickBackground(gui, width, height);
        gui.fill(width/2-110,8,width/2+110,47,0xB0091820);
        gui.fill(12,51,width-12,52,0xFF82805E);
        gui.fill(rosterWidth()+12,58,width-rosterWidth()-12,height-8,0xB00C2028);
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;

        gui.text(font, this.title, this.width / 2 - font.width(this.title) / 2, 14, TITLE_COLOR);

        long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        int remain = (int) Math.max(0, (state.deadlineTick() - now) / 20);
        String myName = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString();
        boolean myTurn = state.currentName() != null && state.currentName().equals(myName);
        String turnText;
        if (state.countdownTick() >= 0) {
            // 全部选择完成：开局倒计时（醒目大数字在面板之后绘制，避免被卡片遮住）
            int cd = (int) Math.max(0, (state.countdownTick() - now) / 20);
            turnText = Component.translatable("screen.myriad.pick.countdown", cd).getString();
        } else if (myTurn) {
            turnText = Component.translatable("screen.myriad.pick.your_turn", remain).getString();
        } else if (state.currentName() == null || state.currentName().isEmpty()) {
            turnText = Component.translatable("screen.myriad.pick.waiting").getString();
        } else {
            String fac = state.currentFaction() == null ? "" : factionLabel(state.currentFaction());
            turnText = Component.translatable("screen.myriad.pick.turn",
                    state.currentName(), fac, remain).getString();
        }
        gui.text(font, Component.literal(turnText).withStyle(net.minecraft.ChatFormatting.BOLD),
                this.width / 2 - font.width(turnText) / 2, 34, HIGHLIGHT_COLOR);

        // 检测新选中：立即播放“移向屏幕边缘”动画
        java.util.Set<String> pickedNow = new java.util.HashSet<>(state.taken().keySet());
        if (!pickedNow.equals(this.prevPicked)) {
            for (String id : pickedNow) {
                if (!this.prevPicked.contains(id)) {
                    this.animPickedId = id;
                    this.animStartTick = now;
                    break;
                }
            }
            this.prevPicked.clear();
            this.prevPicked.addAll(pickedNow);
        }
        if (state.countdownTick() >= 0) {
            drawRosters(gui,state);
        } else {
            // 只显示当前选择阵营的角色；已选角色移入屏幕两侧
            drawCurrentPanel(gui, font, mouseX, mouseY, state, myTurn);
            drawRosters(gui,state);
        }
        if (state.countdownTick() >= 0) {
            int cd = (int) Math.max(0, (state.countdownTick() - now) / 20);
            drawBigCountdown(gui, font, cd);
        }
        gui.text(font, Component.translatable("hero.guide.pick_hint"), width/2-90, height-12, 0xFFBDE7CD);
    }

    /** All participants stay in their actual faction column, including before choosing. */
    private void drawRosters(GuiGraphicsExtractor gui,NetworkSetup.PickStatePayload state){
        var font=Minecraft.getInstance().font;
        for(int team=1;team<=2;team++){
            final int faction=team;
            var players=com.myriad.client.MatchTabOverlay.players().stream().filter(p->p.faction()==faction).toList();
            int w=rosterWidth(),x=team==1?8:width-8-w,accent=team==1?COLOR_XUANXIAO:COLOR_YOUMING;
            gui.fill(x,58,x+w,height-8,0xCF0C2028);
            gui.fill(x,58,x+w,60,accent);
            gui.fill(x,60,x+1,height-8,0xFF82805E);
            gui.fill(x+w-1,60,x+w,height-8,0xFF82805E);
            gui.fill(x,height-9,x+w,height-8,0xFF82805E);
            String title=factionLabel(team==1?"xuanxiao":"youming");
            gui.text(font,font.plainSubstrByWidth(title,w-12),x+6,66,accent);
            int rowHeight=Math.min(48,Math.max(12,(height-98)/Math.max(1,players.size())));
            float scale=Math.min(1,rowHeight/40F);
            gui.enableScissor(x,81,x+w,height-9);
            for(int i=0;i<players.size();i++){
                var player=players.get(i);int y=84+i*rowHeight;
                boolean current=player.name().equals(state.currentName());
                gui.fill(x+3,y-2,x+w-3,y+rowHeight-4,current?0xFF263C53:0xFF182638);
                gui.fill(x+3,y-2,x+5,y+rowHeight-4,current?0xFFFFD77A:accent);
                gui.pose().pushMatrix();gui.pose().translate(x+8,y);gui.pose().scale(scale);
                int contentWidth=(int)((w-16)/scale),face=26;
                var skin=skinOf(player.name(),player.id().toString());
                if(skin==null)skin=net.minecraft.client.resources.DefaultPlayerSkin.get(player.id());
                PlayerFaceExtractor.extractRenderState(gui,skin,0,2,face);
                gui.text(font,font.plainSubstrByWidth(player.name(),contentWidth-face-5),face+5,2,0xFFF3F5F8);
                String chosen=state.takenUuids().entrySet().stream().filter(e->e.getValue().equals(player.id().toString())).map(java.util.Map.Entry::getKey).findFirst().orElse("");
                String status=chosen.isEmpty()?Component.translatable(current?"screen.myriad.pick.selecting":"screen.myriad.pick.unselected").getString()
                        :Component.translatable(CharacterType.byId(chosen).nameKey()).getString();
                gui.text(font,font.plainSubstrByWidth(status,contentWidth-face-5),face+5,16,current?0xFFFFD77A:accent);
                gui.pose().popMatrix();
            }
            gui.disableScissor();
        }
    }

    /** 倒计时阶段：全部角色已选，头像保持在屏幕两侧边缘。 */
    private void drawCountdownLayout(GuiGraphicsExtractor gui, net.minecraft.client.gui.Font font,
            NetworkSetup.PickStatePayload state) {
        for (CharacterType t : CharacterType.playableValues()) {
            String picker = state.taken().get(t.getId());
            if (picker != null) {
                drawEdgeAvatar(gui, font, t, picker, state.takenUuids().get(t.getId()));
            }
        }
    }

    /** 开局倒计时醒目大数字：脉冲放大 + 文字投影（无背景框） */
    private void drawBigCountdown(GuiGraphicsExtractor gui, net.minecraft.client.gui.Font font, int cd) {
        String s = String.valueOf(Math.max(1, cd));
        float pulse = 1f + 0.10f * (float) Math.sin(System.currentTimeMillis() / 150.0);
        float scale = 4.5f * pulse;
        int cx = this.width / 2;
        int cy = 96;
        int tw = font.width(s);
        gui.pose().pushMatrix();
        gui.pose().translate(cx, cy);
        gui.pose().scale(scale);
        gui.text(font, Component.literal(s).withStyle(net.minecraft.ChatFormatting.BOLD),
                -tw / 2 + 2, -7, 0x88000000);
        gui.text(font, Component.literal(s).withStyle(net.minecraft.ChatFormatting.BOLD),
                -tw / 2, -9, 0xFFFFAA33);
        gui.pose().popMatrix();
    }

    private static float easeInOut(float t) {
        return t * t * (3f - 2f * t);
    }

    private static int accentOf(Faction faction) {
        return faction == Faction.XUANXIAO ? COLOR_XUANXIAO : COLOR_YOUMING;
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24);
    }

    /** 将颜色向白色混合 amount（0~1），用于阵营色浅色底 */
    private static int lighten(int argb, float amount) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 当前阵营面板：居中显示该阵营未选角色（已选角色在屏幕边缘），边框用阵营色 */
    private void drawCurrentPanel(GuiGraphicsExtractor gui, net.minecraft.client.gui.Font font,
            int mouseX, int mouseY, NetworkSetup.PickStatePayload state, boolean myTurn) {
        Faction faction = factionOf(state.currentFaction());
        if (faction == null) {
            return;
        }
        List<CharacterType> chars = charsOf(faction);
        int accent = accentOf(faction);
        String label = factionLabel(faction.getName());
        gui.text(font, Component.literal(label).withStyle(net.minecraft.ChatFormatting.BOLD),
                this.width / 2 - font.width(label) / 2, PANEL_TOP - 16, accent);
        for (CharacterType t : chars) {
            boolean taken=state.taken().containsKey(t.getId());
            AvatarSlot slot = avatarSlot(t);
            boolean hover = mouseX >= slot.x() && mouseX <= slot.x() + slot.size()
                    && mouseY >= slot.y() && mouseY <= slot.y() + slot.size();
            boolean clickable = myTurn && faction.getName().equals(state.myFaction());
            drawAvatarAt(gui, font, t, null, null, slot.x(), slot.y(), slot.size(), 1f,
                    hover, clickable && !taken, taken, accent);
            String name = font.plainSubstrByWidth(Component.translatable(t.nameKey()).getString(),slot.size()+8);
            gui.text(font, Component.literal(name).withStyle(net.minecraft.ChatFormatting.BOLD),
                    slot.x() + slot.size() / 2 - font.width(name) / 2,
                    slot.y() + slot.size() + 2, TEXT_COLOR);
        }
    }

    /** 已选角色：播放“移向屏幕边缘”动画并绘制在对应宗门一侧。 */
    private void drawPickedEdges(GuiGraphicsExtractor gui, net.minecraft.client.gui.Font font,
            NetworkSetup.PickStatePayload state) {
        long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        for (CharacterType t : CharacterType.playableValues()) {
            String picker = state.taken().get(t.getId());
            if (picker == null) {
                continue;
            }
            String pickerUuid = state.takenUuids().get(t.getId());
            if (t.getId().equals(this.animPickedId)) {
                float tAnim = (float) (now - this.animStartTick) / ANIM_TICKS;
                if (tAnim >= 1f) {
                    this.animPickedId = null;
                    drawEdgeAvatar(gui, font, t, picker, pickerUuid);
                } else {
                    tAnim = easeInOut(Math.max(0f, tAnim));
                    AvatarSlot origin = avatarSlot(t);
                    int ox = origin.x();
                    int oy = origin.y();
                    int[] ep = edgePos(t);
                    int cx = (int) Math.round(ox + (ep[0] - ox) * tAnim);
                    int cy = (int) Math.round(oy + (ep[1] - oy) * tAnim);
                    int size = Math.round(origin.size() + (TARGET_SIZE - origin.size()) * tAnim);
                    drawAvatarAt(gui, font, t, picker, pickerUuid, cx, cy, size, 1f,
                            false, false, true, accentOf(t.getFaction()));
                }
            } else {
                drawEdgeAvatar(gui, font, t, picker, pickerUuid);
            }
        }
    }

    /** 角色在其宗门一侧屏幕边缘的目标位置（玄霄宗左，幽冥宗右）。 */
    private int[] edgePos(CharacterType t) {
        List<CharacterType> panel = charsOf(t.getFaction());
        int idx = panel.indexOf(t);
        int tx = t.getFaction() == Faction.XUANXIAO ? 8 : this.width - 8 - TARGET_SIZE;
        int maxGap = panel.size() <= 1 ? TARGET_GAP
                : Math.max(2, (this.height - 16 - panel.size() * TARGET_SIZE) / (panel.size() - 1));
        int gap = Math.min(TARGET_GAP, maxGap);
        int totalH = panel.size() * TARGET_SIZE + Math.max(0, panel.size() - 1) * gap;
        int ty = Math.max(8, (this.height - totalH) / 2) + idx * (TARGET_SIZE + gap);
        return new int[] { tx, ty };
    }

    /** 屏幕边缘的已选头像与名字。 */
    private void drawEdgeAvatar(GuiGraphicsExtractor gui, net.minecraft.client.gui.Font font,
            CharacterType t, String picker, String pickerUuid) {
        int[] ep = edgePos(t);
        drawAvatarAt(gui, font, t, picker, pickerUuid, ep[0], ep[1], TARGET_SIZE, 1f,
                false, false, true, accentOf(t.getFaction()));
        String name = Component.translatable(t.nameKey()).getString();
        int nameY = ep[1] + TARGET_SIZE / 2 - 4;
        if (t.getFaction() == Faction.XUANXIAO) {
            gui.text(font, Component.literal(name).withStyle(net.minecraft.ChatFormatting.BOLD),
                    ep[0] + TARGET_SIZE + 6, nameY, TEXT_COLOR);
        } else {
            gui.text(font, Component.literal(name).withStyle(net.minecraft.ChatFormatting.BOLD),
                    ep[0] - 6 - font.width(name), nameY, TEXT_COLOR);
        }
    }

    /** 玩家皮肤缓存：uuid -> PlayerSkin（PlayerInfo 查不到时异步加载） */
    private static final java.util.Map<String, net.minecraft.world.entity.player.PlayerSkin> SKIN_CACHE =
            new java.util.HashMap<>();
    private static final java.util.Set<String> SKIN_REQUESTED = new java.util.HashSet<>();

    /** 解析选择者皮肤：本地玩家直接用自身皮肤；否则优先按 UUID 查 PlayerInfo，失败则异步加载并缓存 */
    private static net.minecraft.world.entity.player.PlayerSkin skinOf(String pickerName, String pickerUuid) {
        Minecraft mc = Minecraft.getInstance();
        if (pickerUuid != null && !pickerUuid.isEmpty() && mc.player != null
                && pickerUuid.equals(mc.player.getUUID().toString())) {
            return mc.player.getSkin();
        }
        net.minecraft.client.multiplayer.ClientPacketListener conn = mc.getConnection();
        if (pickerUuid != null && !pickerUuid.isEmpty()) {
            try {
                java.util.UUID uid = java.util.UUID.fromString(pickerUuid);
                if (conn != null) {
                    net.minecraft.client.multiplayer.PlayerInfo info = conn.getPlayerInfo(uid);
                    if (info != null && info.getSkin() != null) {
                        return info.getSkin();
                    }
                }
                synchronized (SKIN_CACHE) {
                    net.minecraft.world.entity.player.PlayerSkin cached = SKIN_CACHE.get(pickerUuid);
                    if (cached != null) {
                        return cached;
                    }
                    if (!SKIN_REQUESTED.contains(pickerUuid)) {
                        SKIN_REQUESTED.add(pickerUuid);
                        try {
                            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uid,
                                    pickerName == null ? "" : pickerName);
                            Minecraft.getInstance().getSkinManager().get(profile).thenAccept(opt -> {
                                opt.ifPresent(skin -> {
                                    synchronized (SKIN_CACHE) {
                                        SKIN_CACHE.put(pickerUuid, skin);
                                    }
                                });
                            });
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (pickerName != null && !pickerName.isEmpty() && conn != null) {
            net.minecraft.client.multiplayer.PlayerInfo info = conn.getPlayerInfo(pickerName);
            if (info != null && info.getSkin() != null) {
                return info.getSkin();
            }
        }
        return null;
    }

    /** 角色肖像为主，已选角色右下角保留选择者皮肤角标。 */
    private void drawAvatarAt(GuiGraphicsExtractor gui, net.minecraft.client.gui.Font font,
            CharacterType t, String picker, String pickerUuid, int x, int y, int size, float alpha,
            boolean hover, boolean clickable, boolean taken, int accent) {
        int a = (int) (255f * Math.max(0f, Math.min(1f, alpha)));
        // 边框只在可点击悬停时显示（反馈用），平时不画
        if (hover && clickable && !taken) {
            int border = withAlpha(0xFFFFC400, a);
            gui.fill(x - 1, y - 1, x + size + 1, y + 1, border);
            gui.fill(x - 1, y + size, x + size + 1, y + size + 1, border);
            gui.fill(x - 1, y - 1, x, y + size + 1, border);
            gui.fill(x + size, y - 1, x + size + 1, y + size + 1, border);
        }
        gui.fill(x-2,y-2,x+size+2,y+size+2,withAlpha(hover&&clickable?0xFFE9C587:0xFF394B60,a));
        gui.fill(x, y, x + size, y + size, withAlpha(0xFF21344A, a));
        net.minecraft.world.entity.player.PlayerSkin skin = taken ? skinOf(picker, pickerUuid) : null;
        boolean drewPortrait = com.myriad.client.CharacterPortrait.draw(gui,t,x,y,size,alpha);
        boolean drewFace = drewPortrait;
        if (skin != null && !drewPortrait) {
            try {
                PlayerFaceExtractor.extractRenderState(gui, skin, x, y, size);
                drewFace = true;
            } catch (Exception ignored) {
                drewFace = false;
            }
        }
        if (!drewFace) {
            String glyph = Component.translatable(t.nameKey()).getString();
            glyph = glyph.isEmpty() ? "?" : glyph.substring(0, 1);
            int glyphColor = taken ? withAlpha(0xFF707070, a) : withAlpha(accent, a);
            gui.text(font, Component.literal(glyph).withStyle(net.minecraft.ChatFormatting.BOLD),
                    x + size / 2 - font.width(glyph) / 2,
                    y + size / 2 - 4, glyphColor);
        }
        if (taken && !drewFace) {
            // 无皮肤时的已选遮罩（正常头像不压暗，保证皮肤清晰可见）
            gui.fill(x, y, x + size, y + size, withAlpha(0x40000000, a));
        }
        if(taken && drewPortrait){
            gui.fill(x,y,x+size,y+size,withAlpha(0x30000000,a));
            if(skin!=null&&alpha>.9F){int badge=Math.max(8,size/3);gui.fill(x+size-badge-1,y+size-badge-1,x+size+1,y+size+1,0xFFDFCE9E);PlayerFaceExtractor.extractRenderState(gui,skin,x+size-badge,y+size-badge,badge);}
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.buttonInfo().button() == 1) {
            var state = ClientDataHolder.pickState();
            String id = state == null ? null : hitAnyCharacter(event.x(), event.y(), state);
            var type = id == null ? null : CharacterType.byId(id);
            if(type!=null){Minecraft.getInstance().setScreenAndShow(new CharacterGuideScreen(this,type));return true;}
        }
        if (event.buttonInfo().button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        NetworkSetup.PickStatePayload state = ClientDataHolder.pickState();
        Minecraft mc = Minecraft.getInstance();
        boolean myTurn = state != null && state.currentName() != null
                && mc.player != null && state.currentName().equals(mc.player.getName().getString());
        String hit = state == null ? null : hitCharacter(event.x(), event.y(), state);
        com.myriad.Myriad.LOGGER.info(
                "[pick-click] btn={} x={} y={} st={} cur={} myF={} myTurn={} hit={}",
                event.buttonInfo().button(), event.x(), event.y(),
                state == null ? "null" : state.state(),
                state == null || state.currentName() == null ? "null" : state.currentName(),
                state == null ? "null" : state.myFaction(), myTurn,
                hit == null ? "null" : (hit.isEmpty() ? "empty" : hit));
        if (state == null || state.state() != 0 || mc.player == null) {
            return super.mouseClicked(event, doubleClick);
        }
        if (!myTurn || state.myFaction() == null) {
            return super.mouseClicked(event, doubleClick);
        }
        if (hit != null && !hit.isEmpty() && !state.taken().containsKey(hit)) {
            ClientPacketDistributor.sendToServer(new NetworkSetup.ChooseCharacterPayload(hit));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** 命中检测：仅当前阵营居中面板上未选中的角色可点选（已选的在屏幕边缘，不可点） */
    private String hitCharacter(double mouseX, double mouseY, NetworkSetup.PickStatePayload state) {
        Faction faction = factionOf(state.currentFaction());
        if (faction == null) {
            return null;
        }
        List<CharacterType> chars = charsOf(faction);
        for (CharacterType t : chars) {
            AvatarSlot slot = avatarSlot(t);
            if (mouseX >= slot.x() && mouseX <= slot.x() + slot.size()
                    && mouseY >= slot.y() && mouseY <= slot.y() + slot.size()) {
                return t.getId();
            }
        }
        return null;
    }

    /** Right-click can inspect both available and already-picked avatars, including edge slots. */
    private String hitAnyCharacter(double mouseX, double mouseY, NetworkSetup.PickStatePayload state) {
        String center = hitCharacter(mouseX, mouseY, state);
        if (center != null) return center;
        for (CharacterType t : CharacterType.playableValues()) {
            if (!state.taken().containsKey(t.getId())) continue;
            int[] edge = edgePos(t);
            if (mouseX >= edge[0] && mouseX <= edge[0] + TARGET_SIZE
                    && mouseY >= edge[1] && mouseY <= edge[1] + TARGET_SIZE) return t.getId();
        }
        return null;
    }

    private static Faction factionOf(String name) {
        if (name == null) {
            return null;
        }
        for (Faction f : Faction.values()) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    private static String factionLabel(String faction) {
        return Component.translatable("faction.myriad." + faction).getString();
    }
}
