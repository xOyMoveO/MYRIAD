package com.myriad.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.network.NetworkSetup;
import com.myriad.network.NetworkSetup.PlayerDataPayload;

/** 对局信息、个人战斗面板和世界提示。 */
public class HudOverlay implements HudElement {
    public static final HudOverlay INSTANCE = new HudOverlay();

    /** 攻击目标生命显示：entityId + 显示截止时间（毫秒） */
    private static int targetEntityId = -1;
    private static long targetExpireTime = 0;
    private static final long TARGET_DISPLAY_MS = 5000;
    private static long hurtFeedbackUntil;
    private static float hurtFeedbackStrength;
    /** 假人（训练目标）mod 血量（原版血量上限 1024，假人走 mod 跟踪） */
    private static int targetModHpEntity = -1;
    private static float targetModHp = -1;
    private static float targetModMaxHp = -1;
    private static float targetPhysicalDefense = 0;
    private static float targetMagicDefense = 0;
    private static NetworkSetup.VeinStatusPayload veinStatus =
            new NetworkSetup.VeinStatusPayload(false, 0, false, 0, 0, 0, 1);

    public static void setVeinStatus(NetworkSetup.VeinStatusPayload payload) {
        veinStatus = payload;
    }

    /** 攻击后设置显示目标（服务端包回调，主线程） */
    public static void setTarget(int entityId, float physicalDefense, float magicDefense) {
        targetEntityId = entityId;
        targetExpireTime = System.currentTimeMillis() + TARGET_DISPLAY_MS;
        targetPhysicalDefense = Math.max(0, physicalDefense);
        targetMagicDefense = Math.max(0, magicDefense);
        if (entityId != targetModHpEntity) {
            targetModHpEntity = -1;
            targetModHp = -1;
            targetModMaxHp = -1;
        }
    }

    /** 假人 mod 血量同步（服务端包回调，主线程） */
    public static void setJiarenHp(int entityId, float hp, float maxHp) {
        targetModHpEntity = entityId;
        targetModHp = hp;
        targetModMaxHp = maxHp;
    }

    public static void showHurtFeedback(float damage, float maxHealth) {
        if(!ClientDataHolder.modEnabled()||!Float.isFinite(damage)||damage<=0)return;
        float ratio = damage / Math.max(1.0F, maxHealth);
        hurtFeedbackStrength = Math.max(hurtFeedbackStrength,
                net.minecraft.util.Mth.clamp(.18F + ratio * 2.2F, .18F, .62F));
        hurtFeedbackUntil = System.currentTimeMillis() + 320L;
        CombatImpactAudio.hurt(ratio);
    }

    private static final int LINE_HEIGHT = 12;
    /** 界面适配基准宽度（guiScale=2、854 宽窗口），guiScale 越大文本整体收缩，保证完整显示 */
    private static final float FIT_REF_WIDTH = 427.0f;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HIGHLIGHT_COLOR = 0xFF55FFFF;

    /** 适配玩家界面尺寸的缩放因子：guiScale 越大（屏幕逻辑宽越小）文本越小，保证完整显示 */
    private static float fitScale() {
        float w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return Math.min(1.0f, w / FIT_REF_WIDTH);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker delta) {
        if (!ClientDataHolder.modEnabled() || !ClientDataHolder.inGame()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (nowMs < hurtFeedbackUntil) {
            float remaining = (hurtFeedbackUntil - nowMs) / 320.0F;
            int alpha = Math.clamp(Math.round(255.0F * hurtFeedbackStrength * remaining), 0, 180);
            int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            gui.fill(0, 0, width, height, alpha << 24 | 0x00A80000);
        } else {
            hurtFeedbackStrength = 0.0F;
        }
        PlayerDataPayload data = ClientDataHolder.get();
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.FEEDBACK,
                () -> HitFeedback.draw(gui));
        if (data == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        TeamSignalClient.render(gui,font);
        var combatLayout = CombatHudOverlay.draw(gui, font);
        ControlStatusOverlay.render(gui, font, combatLayout != null);
        YikeHud.drawMuzzle(gui, font);
        var preparation=ClientDataHolder.pickState();
        int seconds = LobbyClient.preparationSeconds();
        if (seconds < 0 && preparation != null && preparation.state() == 1 && preparation.countdownTick() >= 0
                && Minecraft.getInstance().level != null) {
            seconds = (int)Math.max(0,(preparation.countdownTick()-Minecraft.getInstance().level.getGameTime()+19)/20);
        }
        var queue = LobbyClient.snapshot();
        Component notice = null;
        if (seconds > 0) {
            notice = Component.translatable("rivet".equals(data.characterId())
                    ? "hud.myriad.rivet.preparation" : "hud.myriad.match.preparation", seconds);
        } else if (queue != null && queue.position() > 0 && queue.voteSeconds() >= 0) {
            notice = Component.translatable("lobby.myriad.proposal_hud", queue.count()/2, queue.count()/2,
                    queue.voteSeconds(), com.myriad.MyriadClient.KEY_LOBBY.getTranslatedKeyMessage());
        }
        if (notice != null) {
            int px = (Minecraft.getInstance().getWindow().getGuiScaledWidth() - font.width(notice)) / 2;
            gui.fill(px-5, 18, px+font.width(notice)+5, 32, 0xDD111C24);
            gui.text(font, notice, px, 20, 0xFFFFD79A);
        }
        if (data.combatActive() || data.combatPreparing()) HudLayoutConfig.render(gui, HudLayoutConfig.Element.INFO, () -> {
            float s = fitScale();
            int line = (int) (LINE_HEIGHT * s);
            gui.pose().pushMatrix();
            gui.pose().scale(s);
            int x = 6;
            int y = 6;
            Component faction = Component.literal("阵营: " + ClientDataHolder.factionName())
                    .withStyle(net.minecraft.ChatFormatting.BOLD);
            Component objective = Component.translatable("hud.myriad.destroy_crystal");
            boolean occluded = combatLayout != null && Minecraft.getInstance().gui.screen() instanceof ChatScreen
                    && (overlapsInfoRow(gui, combatLayout.bounds(), x, y, font.width(faction), font.lineHeight)
                    || overlapsInfoRow(gui, combatLayout.bounds(), x, y + line, font.width(objective), font.lineHeight));
            if (!occluded) {
                gui.text(font, faction, x, y, HIGHLIGHT_COLOR);
                gui.text(font, objective, x, y + line, TEXT_COLOR);
            }
            gui.pose().popMatrix();
        });
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.MINIMAP, () -> MinimapOverlay.render(gui, font));
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.BUFFS, () -> renderBuffs(gui, font));
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.PROMPTS, () -> {
            renderPlacementHint(gui, font);
            renderHeroRespawn(gui, font);
            renderRecall(gui, font);
            SkillChargeClient.render(gui,font,delta.getGameTimeDeltaPartialTick(false));
        });
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.TARGET, () -> renderTargetHealth(gui, font));
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.OBJECTIVE, () -> renderVeinStatus(gui, font));
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.ANNOUNCEMENTS, () -> {
            KillAnnouncementOverlay.render(gui, font);
            TowerAlertOverlay.render(gui, font);
        });
        DpsTracker.render(gui, font, combatLayout);
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.COMBAT_STATS,
                () -> CombatStatsPanel.render(gui, font));
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.FLOATERS, () -> {
            HealFloater.render(gui, font);
            DamageFloater.render(gui, font, delta.getGameTimeDeltaPartialTick(false));
            RewardFloater.render(gui, font);
        });
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.WORLD_BARS, () -> {
            float partialTick = delta.getGameTimeDeltaPartialTick(false);
            EnemyHealthBars.render(gui, font, partialTick);
            MinionHealthBars.render(gui, font, partialTick);
        });
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.TAB, () -> MatchTabOverlay.render(gui, font));
    }

    private static boolean overlapsInfoRow(GuiGraphicsExtractor gui, CombatHudLayout.Bounds panel,
                                           int x, int y, int width, int height) {
        var pose = gui.pose(); // The same scale and position already applied to both INFO text rows.
        float left = pose.m20() + x * pose.m00();
        float top = pose.m21() + y * pose.m11();
        float right = left + (width + 1) * pose.m00();
        float bottom = top + (height + 1) * pose.m11();
        return left < panel.right() && right > panel.x() && top < panel.bottom() && bottom > panel.y();
    }

    /** 复活倒计时与观战目标提示。 */
    private void renderHeroRespawn(GuiGraphicsExtractor gui, Font font) {
        int ticks = ClientDataHolder.buffTicks("hero_respawn");
        if (ticks <= 0) return;
        String text = Component.translatable("hud.myriad.hero_respawn", (ticks + 19) / 20).getString();
        int x = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2;
        int y = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2 - 28;
        gui.fill(x - font.width(text) / 2 - 8, y - 5, x + font.width(text) / 2 + 8, y + 15, 0xD0101722);
        gui.text(font, text, x - font.width(text) / 2, y, 0xFFFFD782);
        String target = RespawnSpectatorView.targetName();
        String hint = target.isBlank()
                ? Component.translatable("hud.myriad.spectating_none").getString()
                : Component.translatable("hud.myriad.spectating", target).getString();
        gui.fill(x - font.width(hint) / 2 - 8, y + 16, x + font.width(hint) / 2 + 8, y + 32, 0xB8101722);
        gui.text(font, hint, x - font.width(hint) / 2, y + 19, 0xFFB9DFFF);
        if (DeathRecapStore.latest() != null) {
            var recap = Component.translatable("recap.myriad.hint", com.myriad.MyriadClient.KEY_DEATH_RECAP.getTranslatedKeyMessage());
            gui.text(font, recap, x - font.width(recap) / 2, y + 35, 0xFFFFD48B);
        }
    }

    private void renderRecall(GuiGraphicsExtractor gui, Font font) {
        int ticks = ClientDataHolder.buffTicks("recall");
        if (ticks <= 0) return;
        String text=Component.translatable("hud.myriad.recall",String.format(java.util.Locale.ROOT,"%.1f",ticks/20.0)).getString();
        float progress=1.0F-ticks/(float)com.myriad.game.RecallSystem.DURATION;
        CastProgressBar.draw(gui,font,text,progress);
    }

    /** 位于灵脉范围内时显示双方人数与双向占领进度。 */
    private void renderVeinStatus(GuiGraphicsExtractor gui, Font font) {
        NetworkSetup.VeinStatusPayload status = veinStatus;
        if (status == null || !status.visible()) return;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int cx = sw / 2;
        // 攻击目标血条存在时自动下移，避免与目标物防/法防文字重叠。
        int y = targetEntityId >= 0 ? 43 : 10;
        Component title = status.large() ? Component.translatable("hud.myriad.vein_large")
                : Component.translatable("hud.myriad.vein_" + switch (status.index()) {
                    case 1 -> "moon";
                    case 2 -> "fire";
                    case 3 -> "wood";
                    default -> "unknown";
                });
        gui.text(font, title, cx - font.width(title) / 2, y, 0xFFFFE27A);
        String counts = Component.translatable("faction.myriad.guardian").getString() + " "
                + status.guardianPlayers() + "  VS  " + status.destructionPlayers() + " "
                + Component.translatable("faction.myriad.destruction").getString();
        gui.text(font, counts, cx - font.width(counts) / 2, y + 11, 0xFFFFFFFF);
        int w = 180;
        int h = 7;
        int x = cx - w / 2;
        int barY = y + 23;
        int middle = x + w / 2;
        gui.fill(x - 1, barY - 1, x + w + 1, barY + h + 1, 0xDD080B12);
        gui.fill(x, barY, x + w, barY + h, 0xAA30343D);
        float ratio = Math.min(1.0F, Math.abs(status.progress()) / (float) Math.max(1, status.captureTicks()));
        int length = Math.round((w / 2.0F) * ratio);
        if (status.progress() > 0) gui.fill(middle, barY, middle + length, barY + h, 0xFFFF5555);
        else if (status.progress() < 0) gui.fill(middle - length, barY, middle, barY + h, 0xFF55AAFF);
        gui.fill(middle, barY - 1, middle + 1, barY + h + 1, 0xFFFFFFFF);
        String progressText = Math.round(ratio * 100.0F) + "%";
        gui.text(font, progressText, cx - font.width(progressText) / 2, barY + h + 3, 0xFFFFFFFF);
    }

    /** 按住 Tab 时在玩家列表上方显示对局剩余时间 + 进度条 */

    /** 屏幕正上方：攻击目标的名称 + 生命值 + 血条（实时读取客户端实体，目标死亡/超时消失） */
    private void renderTargetHealth(GuiGraphicsExtractor gui, Font font) {
        if (targetEntityId < 0) {
            return;
        }
        if (System.currentTimeMillis() > targetExpireTime) {
            targetEntityId = -1;
            targetModHpEntity = -1;
            targetModHp = -1;
            targetModMaxHp = -1;
            targetPhysicalDefense = 0;
            targetMagicDefense = 0;
            return;
        }
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            targetEntityId = -1;
            targetModHpEntity = -1;
            targetModHp = -1;
            targetModMaxHp = -1;
            targetPhysicalDefense = 0;
            targetMagicDefense = 0;
            return;
        }
        net.minecraft.world.entity.Entity entity = level.getEntity(targetEntityId);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || living.isDeadOrDying()) {
            targetEntityId = -1;
            targetModHpEntity = -1;
            targetModHp = -1;
            targetModMaxHp = -1;
            targetPhysicalDefense = 0;
            targetMagicDefense = 0;
            return;
        }
        float health = living.getHealth();
        float max = living.getMaxHealth();
        // 假人：使用 mod 同步的血量显示
        if (targetModHpEntity == entity.getId() && targetModHp >= 0) {
            health = targetModHp;
            max = targetModMaxHp > 0 ? targetModMaxHp : max;
        }
        double targetDistance = living.position().distanceTo(Minecraft.getInstance().gameRenderer.mainCamera().position());
        if (Minecraft.getInstance().player == null || !com.myriad.network.EnemyHealthPayload.inRange(
                Minecraft.getInstance().player.distanceToSqr(living))) return;
        boolean compactTarget = targetDistance > 12;
        int cx = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2;
        int y = 10;
        String text = compactTarget ? living.getName().getString() : living.getName().getString()
                + " 生命: " + String.format("%.0f", health) + "/" + String.format("%.0f", max);
        gui.text(font, text, cx - font.width(text) / 2, y, 0xFFFFFFFF);
        int w = (int) Math.round(140 * Math.clamp(12.0 / Math.max(12.0, targetDistance), .45, 1.0));
        int h = compactTarget ? 3 : 6;
        int bx = cx - w / 2;
        int by = y + 10;
        gui.fill(bx - 1, by - 1, bx + w + 1, by + h + 1, 0xFF000000);
        gui.fill(bx, by, bx + w, by + h, 0x88000000);
        float ratio = max <= 0 ? 0 : health / max;
        int fillW = (int) (w * Math.min(1.0f, Math.max(0.0f, ratio)));
        int color = 0xFF55FF55;
        if (ratio < 0.5f) {
            color = 0xFFFFFF55;
        }
        if (ratio < 0.25f) {
            color = 0xFFFF5555;
        }
        if (fillW > 0) {
            gui.fill(bx, by, bx + fillW, by + h, color);
        }
        if (compactTarget) return;
        String physical = "物防: " + Math.round(targetPhysicalDefense);
        String magic = "法防: " + Math.round(targetMagicDefense);
        int gap = 14;
        int defenseWidth = font.width(physical) + gap + font.width(magic);
        int defenseX = cx - defenseWidth / 2;
        gui.text(font, physical, defenseX, by + h + 3, 0xFFE6BF7A);
        gui.text(font, magic, defenseX + font.width(physical) + gap, by + h + 3, 0xFF78C9FF);
    }

    /** 屏幕中央：墨尘布阵提示（左键释放 / 右键取消） */
    private void renderPlacementHint(GuiGraphicsExtractor gui, Font font) {
        if (ClientDataHolder.placementType() == 0) {
            return;
        }
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        String text = Component.translatable("hud.myriad.placement_hint").getString();
        gui.text(font, Component.literal(text).withStyle(net.minecraft.ChatFormatting.BOLD)
                        .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF66FFEE)),
                sw / 2 - font.width(text) / 2, sh / 2 + 26, 0xFF66FFEE);
    }

    /** 屏幕右上角：技能附加效果以对应技能图标 + 剩余时间显示；非技能增益保留文字显示。 */
    private void renderBuffs(GuiGraphicsExtractor gui, Font font) {
        java.util.Map<String, Integer> buffs = ClientDataHolder.buffs();
        if (buffs.isEmpty()) {
            return;
        }
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int x = sw - 6;
        int y = 6;
        if (MinimapClientState.configured() && MinimapOverlay.isHeld()) {
            int mapX = MinimapConfig.x(sw, MinimapOverlay.SIZE);
            int mapY = MinimapConfig.y(sh, MinimapOverlay.SIZE);
            if (mapX + MinimapOverlay.SIZE >= sw - 150 && mapY < 80) {
                y = mapY + MinimapOverlay.SIZE + 6;
            }
        }
        CharacterType type = CharacterType.byId(ClientDataHolder.get().characterId());
        for (java.util.Map.Entry<String, Integer> entry : buffs.entrySet()) {
            if ("chixiao_passive".equals(entry.getKey()) || "sulan_passive_ready".equals(entry.getKey())
                    || entry.getKey().startsWith("skill_charge_") || entry.getKey().startsWith("g1_attribute_")
                    || entry.getKey().startsWith("g1_root/") || entry.getKey().startsWith("g1_rivet_anchor_")
                    || entry.getKey().startsWith("g1_shuo_") || entry.getKey().startsWith("rune_") || entry.getKey().equals("g1_channel")
                    || entry.getKey().equals("hero_respawn") || entry.getKey().equals("recall") || entry.getKey().endsWith("_recast")
                    || entry.getKey().startsWith("g2_")&&entry.getKey().endsWith("_ready")) continue;
            String displayId = BUFF_ICON_SKILLS.getOrDefault(entry.getKey(), entry.getKey());
            String text = Component.translatable("skill.myriad." + displayId).getString()
                    + " " + String.format("%.1fs", entry.getValue() / 20.0);
            Skill skill = buffSkill(type, entry.getKey());
            if (SkillIcon.hasMonsterBuffIcon(entry.getKey())) {
                int iconSize = 18;
                int iconX = x - font.width(text) - iconSize - 4;
                SkillIcon.drawMonsterBuff(gui, iconX, y, iconSize, entry.getKey());
                gui.text(font, text, iconX + iconSize + 4, y + 5, 0xFFBFEFFF);
                y += 22;
            } else if (skill != null) {
                int iconSize = 18;
                int iconX = x - font.width(text) - iconSize - 4;
                SkillIcon.drawBuff(gui, font, iconX, y, iconSize, skill);
                gui.text(font, text, iconX + iconSize + 4, y + 5, 0xFFBFEFFF);
                y += 22;
            } else {
                gui.text(font, text, x - font.width(text), y + 5, 0xFF7FB8FF);
                y += 16;
            }
        }
    }

    private static final java.util.Map<String, String> BUFF_ICON_SKILLS = java.util.Map.of(
            "yufengqianli_buff", "yufengqianli",
            "fentianhuohai_ad", "fentianhuohai",
            "g1_rivet_rhythm", "rivet_breach_rhythm");

    private Skill buffSkill(CharacterType type, String buffId) {
        var ability = com.myriad.character.rework.FirstGroupSkills.byId(buffId);
        if (ability != null) return ability;
        String skillId = BUFF_ICON_SKILLS.getOrDefault(buffId, buffId);
        if (type != null) {
            for (Skill passive : type.passives()) if (passive != null && passive.getId().equals(skillId)) return passive;
            for (int slot = 0; slot < CharacterType.SKILL_SLOTS; slot++) {
                Skill skill = type.skill(slot);
                if (skill != null && skill.getId().equals(skillId)) return skill;
            }
        }
        // 外部角色施加的增益/减益（如云笙共鸣与沉默）仍复用来源技能图标。
        for (CharacterType candidate : CharacterType.values()) {
            for (Skill passive : candidate.passives()) if (passive != null && passive.getId().equals(skillId)) return passive;
            for (int slot = 0; slot < CharacterType.SKILL_SLOTS; slot++) {
                Skill skill = candidate.skill(slot);
                if (skill != null && skill.getId().equals(skillId)) return skill;
            }
        }
        return null;
    }
}
