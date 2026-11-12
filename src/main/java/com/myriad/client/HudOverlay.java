package com.myriad.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.GuiLayer;

import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.game.GameRules;
import com.myriad.network.NetworkSetup;
import com.myriad.network.NetworkSetup.PlayerDataPayload;

/**
 * 游戏内 HUD：阵营 / 等级 / 经验 / 金币 / 法力 / 携带灵源 / 对局计分 / 技能栏。
 */
public class HudOverlay implements GuiLayer {
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
    /** 技能栏 / buff 列表的放大倍数 */
    private static final float UI_SCALE = 1.5f;
    /** 界面适配基准宽度（guiScale=2、854 宽窗口），guiScale 越大文本整体收缩，保证完整显示 */
    private static final float FIT_REF_WIDTH = 427.0f;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HIGHLIGHT_COLOR = 0xFF55FFFF;
    private static final int CD_COLOR = 0xFFFFAA00;
    private static final int READY_COLOR = 0xFF55FF55;

    /** 适配玩家界面尺寸的缩放因子：guiScale 越大（屏幕逻辑宽越小）文本越小，保证完整显示 */
    private static float fitScale() {
        float w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return Math.min(1.0f, w / FIT_REF_WIDTH);
    }

    @Override
    public void render(GuiGraphicsExtractor gui, DeltaTracker delta) {
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
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.INFO, () -> {
            float s = fitScale();
            int line = (int) (LINE_HEIGHT * s);
            gui.pose().pushMatrix();
            gui.pose().scale(s);
            int x = 6;
            int y = 6;
            gui.text(font, Component.literal("阵营: " + ClientDataHolder.factionName())
                            .withStyle(net.minecraft.ChatFormatting.BOLD), x, y, HIGHLIGHT_COLOR);
            y += line;
            gui.text(font, "等级: " + data.level() + "  经验: " + data.xp() + "/" + data.xpToNext(), x, y, TEXT_COLOR);
            y += line;
            gui.text(font, Component.translatable("hud.myriad.destroy_crystal"), x, y, TEXT_COLOR);
            y += line;
            gui.text(font, Component.translatable("hud.myriad.gold", data.gold()), x, y, 0xFFFFD34F);
            gui.pose().popMatrix();
        });
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.SKILLS, () -> renderSkillBar(gui, font, data));
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.ATTACKS, () -> {
            renderBasicAttackBar(gui, font, data);
            renderMagicBulletBar(gui, font, data);
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
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.HERO_RESOURCE,
                () -> renderZheChuanPalindromeBonus(gui, font, data));
        HudLayoutConfig.render(gui, HudLayoutConfig.Element.DPS, () -> DpsTracker.render(gui, font));
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

    /** 右下角：折川当局回文斩永久增伤，图标在上、累计数值在下。 */
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
    }

    private void renderRecall(GuiGraphicsExtractor gui, Font font) {
        int ticks = ClientDataHolder.buffTicks("recall");
        if (ticks <= 0) return;
        int x=Minecraft.getInstance().getWindow().getGuiScaledWidth()/2;
        int y=Minecraft.getInstance().getWindow().getGuiScaledHeight()/2+40;
        String text=Component.translatable("hud.myriad.recall",String.format(java.util.Locale.ROOT,"%.1f",ticks/20.0)).getString();
        gui.fill(x-80,y,x+80,y+24,0xD0101722);
        gui.text(font,text,x-font.width(text)/2,y+3,0xFFA6F5EC);
        gui.fill(x-76,y+17,x+76,y+20,0xFF293C48);
        int filled=Math.max(0,Math.min(152,152*(160-ticks)/160));
        gui.fill(x-76,y+17,x-76+filled,y+20,0xFF66DCCE);
    }

    private void renderZheChuanPalindromeBonus(GuiGraphicsExtractor gui, Font font, PlayerDataPayload data) {
        if (!"zhechuan".equals(data.characterId())) return;
        Skill[] passives = CharacterType.ZHECHUAN.passives();
        if (passives.length == 0) return;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int size = 24;
        int x = sw - size - 12;
        int y = sh - size - 25;
        gui.fill(x - 2, y - 2, x + size + 2, y + size + 14, 0xB20A1018);
        SkillIcon.drawBuff(gui, font, x, y, size, passives[0]);
        int bonus = Math.max(0, ClientDataHolder.syncedCounter("zhechuan_palindrome_bonus"));
        String value = "+" + bonus;
        gui.text(font, value, x + (size - font.width(value)) / 2, y + size + 3,
                bonus > 0 ? 0xFFFFD76A : 0xFF93A4AE);
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
        String counts = Component.translatable("faction.myriad.xuanxiao").getString() + " "
                + status.xuanxiaoPlayers() + "  VS  " + status.youmingPlayers() + " "
                + Component.translatable("faction.myriad.youming").getString();
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
            if ("chixiao_passive".equals(entry.getKey()) || "lige_passive".equals(entry.getKey())
                    || "guanlan_passive".equals(entry.getKey()) || "xuanjia_passive".equals(entry.getKey()) || "sulan_passive_ready".equals(entry.getKey())
                    || entry.getKey().startsWith("feiyu_") || entry.getKey().startsWith("luoheng_")
                    || entry.getKey().startsWith("shenli_") || entry.getKey().startsWith("zhechuan_") || entry.getKey().startsWith("mingzhu_")
                    || entry.getKey().startsWith("skill_charge_") || entry.getKey().startsWith("g1_attribute_")
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
            "wanyue_empowered", "wanyue_passive",
            "taxianzuiyue_shield", "taxianzuiyue");

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

    /** 物品栏右侧：自定义普攻的攻速回复条，满条即代表下一击可用。 */
    private void renderBasicAttackBar(GuiGraphicsExtractor gui, Font font, PlayerDataPayload data) {
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int width = 48;
        int height = 5;
        int x = sw / 2 + 94;
        int y = sh - 25;
        int max = Math.max(1, data.basicAttackCooldownMax());
        int cooldown = ClientDataHolder.basicAttackCooldownNow();
        boolean vanillaMelee=com.myriad.combat.VanillaMeleeAttack.usesVanilla(CharacterType.byId(data.characterId()));
        var attackPlayer=Minecraft.getInstance().player;
        if(vanillaMelee&&attackPlayer!=null)max=Math.max(1,(int)Math.ceil(attackPlayer.getCurrentItemAttackStrengthDelay()));
        float ready = 1.0F - Math.min(1.0F, Math.max(0.0F, cooldown / (float) max));
        boolean fullReady = cooldown <= 0;
        int fill = Math.round(width * ready);
        int color = fullReady ? 0xFF62E88E : 0xFF57C9F5;
        gui.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF111827);
        gui.fill(x, y, x + width, y + height, 0xAA07111F);
        if (fill > 0) gui.fill(x, y, x + fill, y + height, color);
        String label = fullReady ? "普攻" : String.format("%.1fs", cooldown / 20.0F);
        gui.text(font, label, x + (width - font.width(label)) / 2, y - 10,
                fullReady ? 0xFF9FFFD0 : 0xFFB9ECFF);
        if ("mingzhu".equals(data.characterId())) {
            int energy = Math.clamp(ClientDataHolder.syncedCounter("mingzhu_energy_eighths"), 0, 800);
            int iy = y - 28;
            gui.fill(x-1, iy-1, x+73, iy+6, 0xEE191923);
            gui.fill(x, iy, x+72, iy+5, 0xFF493723);
            gui.fill(x, iy, x+Math.round(72*energy/800F), iy+5, energy>=240?0xFFFFD277:0xFFAD8957);
            String energyText=Component.translatable("hud.myriad.mingzhu_energy", String.format("%.3f", energy/8F)).getString();
            gui.text(font,energyText,x,iy-11,0xFFFFE6B2);
            String hint=ClientDataHolder.buffTicks("mingzhu_channel")>0
                    ? Component.translatable("hud.myriad.mingzhu_channel",String.format("%.2f",ClientDataHolder.buffTicks("mingzhu_beam_time")/20F)).getString()
                    : energy<240?Component.translatable("message.myriad.mingzhu_need_energy").getString():"";
            if(!hint.isEmpty())gui.text(font,hint,x,iy-22,0xFFE7CC91);
            if(ClientDataHolder.buffTicks("mingzhu_channel")>0){
                int radiance=Math.clamp(ClientDataHolder.syncedCounter("mingzhu_radiance"),0,10);
                gui.text(font,Component.translatable("hud.myriad.mingzhu_radiance",radiance).getString(),x+80,iy-11,0xFFFFEFC9);
                for(int i=0;i<5;i++)gui.fill(x+80+i*10,iy,x+87+i*10,iy+5,i*2<radiance?0xFFFFE3A0:0xFF55472F);
            }
        } else if ("shuo".equals(data.characterId())) {
            int gauge=Math.clamp(ClientDataHolder.syncedCounter("g1_shuo_gauge"),0,100);
            var passive=com.myriad.character.rework.FirstGroupSkills.selected("shuo",0,data.skillLevels());
            boolean edge=passive!=null&&passive.index()==0&&data.skillLevels().getOrDefault(passive.getId(),0)>0;
            String stance=ClientDataHolder.buffTicks("g1_shuo_sheathing")>0?"回鞘中":ClientDataHolder.isChanneling()?"蓄势":ClientDataHolder.syncedCounter("g1_shuo_drawn")>0?"出鞘·连击式 · 右键回鞘":edge?"收鞘·拔刀式 · 藏锋 "+gauge+"%":"收鞘·拔刀式";
            if(passive!=null&&passive.index()==1&&data.skillLevels().getOrDefault(passive.getId(),0)>0&&ClientDataHolder.syncedCounter("g1_shuo_ready")>0)stance+=" · 见切就绪";
            gui.text(font,stance,x-font.width(stance)/2+width/2,y-26,0xFFD8EEFF);
            if(edge){gui.fill(x,y-14,x+width,y-10,0xCC172333);
                gui.fill(x,y-14,x+Math.round(width*gauge/100F),y-10,0xFFE7F7FF);}
        } else if (com.myriad.character.rework.FirstGroupSkills.SECOND_GROUP.contains(data.characterId())) {
            var passive=com.myriad.character.rework.FirstGroupSkills.selected(data.characterId(),0,data.skillLevels());
            if(passive!=null&&ClientDataHolder.buffTicks(passive.getId()+"_ready")>0){
                SkillIcon.drawBuff(gui,font,x+width/2-7,y-29,14,passive);
                gui.text(font,"就绪",x+width/2-10,y-12,0xFFFFE7A0);
            }
        } else if ("chixiao".equals(data.characterId())) {
            int stacks = Math.max(0, Math.min(3, ClientDataHolder.syncedCounter("chixiao_passive")));
            int internalCd = ClientDataHolder.buffTicks("chixiao_passive_cd");
            int alpha = stacks == 0 ? (internalCd > 0 ? 125 : 45) : 80 + stacks * 55;
            int iconColor = (alpha << 24) | (stacks >= 3 ? 0xFFE75A24 : 0xFF9C3A2F);
            int ix = x + width / 2 - 6; int iy = y - 23;
            gui.fill(ix - 1, iy - 1, ix + 13, iy + 13, 0xCC1A1010);
            gui.fill(ix, iy, ix + 12, iy + 12, iconColor);
            gui.text(font, "战", ix + 2, iy + 2, stacks >= 3 ? 0xFFFFFF88 : 0xFFFFFFFF);
            if (stacks >= 3) { gui.fill(ix - 2, iy - 2, ix + 14, iy - 1, 0xFFFFC84A); gui.fill(ix - 2, iy + 13, ix + 14, iy + 14, 0xFFFFC84A); }
            if (internalCd > 0) gui.text(font, String.valueOf((internalCd + 19) / 20), ix + 3, iy + 3, 0xFFFFD060);
        } else if ("lige".equals(data.characterId())) {
            int stacks = Math.max(0, Math.min(3, ClientDataHolder.syncedCounter("lige_passive")));
            Skill[] passives = CharacterType.LIGE.passives();
            if (passives.length > 0) {
                int ix = x + 11;
                int iy = y - 31;
                SkillIcon.drawBuff(gui, font, ix, iy, 14, passives[0]);
                if (stacks == 0) gui.fill(ix, iy, ix + 14, iy + 14, 0xA8000000);
                String stackText = "×" + stacks;
                gui.text(font, stackText, ix + 18, iy + 3,
                        stacks >= 3 ? 0xFFFFF09A : (stacks > 0 ? 0xFF8EF5F2 : 0xFF74808C));
                if (stacks >= 3) {
                    gui.fill(ix - 2, iy - 2, ix + 16, iy - 1, 0xFFFFE56E);
                    gui.fill(ix - 2, iy + 15, ix + 16, iy + 16, 0xFFFFE56E);
                }
            }
        } else if ("feiyu".equals(data.characterId())) {
            int stage = Math.max(0, Math.min(6, ClientDataHolder.syncedCounter("feiyu_stage")));
            int remain = ClientDataHolder.buffTicks("feiyu_score_time");
            String[] names = {"未入谱", "羽", "翎", "翔", "霄", "鸿", "凰"};
            int ix = x - 9, iy = y - 34;
            int[] colors = {0xFF54E1D0,0xFF559CFF,0xFF9B65FF,0xFFF0445E,0xFFFFC95C,0xFFFFF1B0};
            for (int i = 0; i < 6; i++) {
                int fx = ix + i * 11;
                gui.fill(fx, iy + Math.abs(3 - i) / 2, fx + 8, iy + 10, i < stage ? colors[i] : 0xFF303640);
            }
            String stageText = names[stage] + (stage > 0 ? String.format("  %.1fs", remain / 20F) : "");
            gui.text(font, stageText, ix + 30 - font.width(stageText) / 2, iy - 10,
                    stage >= 6 ? 0xFFFFE58A : 0xFFEAFBFF);
            int recovery = ClientDataHolder.buffTicks("feiyu_recovery");
            if (recovery > 0) {
                String recoveryText = String.format("收势 %.2fs", recovery / 20F);
                gui.text(font, recoveryText, ix + 30 - font.width(recoveryText) / 2, iy + 13, 0xFFFFE09A);
            }
            int beat = ClientDataHolder.syncedCounter("feiyu_r_beat");
            int window = ClientDataHolder.buffTicks("feiyu_r_window");
            if (beat > 0 || window > 0) {
                int chapter = ClientDataHolder.syncedCounter("feiyu_r_chapter");
                int perfect = ClientDataHolder.syncedCounter("feiyu_r_perfect");
                int direction = ClientDataHolder.syncedCounter("feiyu_r_direction") - 1;
                int used = ClientDataHolder.syncedCounter("feiyu_r_directions");
                String[] arrows = {"→", "↘", "↓", "↙", "←", "↖", "↑", "↗"};
                int beatX = ix + 3;
                for (int i = 0; i < 5; i++) {
                    int bx = beatX + i * 12;
                    gui.fill(bx, iy - 31, bx + 9, iy - 27, i < beat ? 0xFFFFC75A : 0xFF38414D);
                }
                String timing = String.format("下一拍 %.2fs", window / 20F);
                gui.text(font, timing, ix + 30 - font.width(timing) / 2, iy - 43,
                        window <= 12 && window > 0 ? 0xFFFFFFFF : 0xFFFFC75A);
                String rText = "羽章 " + chapter + "  完美 " + perfect + "/3"
                        + (direction >= 0 && direction < 8 ? "  上拍 " + arrows[direction] : "");
                gui.text(font, rText, ix + 30 - font.width(rText) / 2, iy - 22, 0xFFFFC75A);
                StringBuilder dirs = new StringBuilder();
                for (int i = 0; i < 8; i++) dirs.append((used & (1 << i)) != 0 ? "·" : arrows[i]);
                gui.text(font, dirs.toString(), ix + 30 - font.width(dirs.toString()) / 2, iy - 12, 0xFF9BEDE7);
            }
        } else if ("shenli".equals(data.characterId())) {
            int ix = x - 5, iy = y - 37;
            int[] colors = {0xFFD85A36, 0xFF67EDF2, 0xFFF5C451};
            int ammo = Math.max(0, Math.min(6, ClientDataHolder.syncedCounter("shenli_ammo")));
            for (int i = 0; i < 6; i++) {
                int bx = ix + i * 11;
                int colorId = ClientDataHolder.syncedCounter("shenli_ammo_" + (i + 1));
                gui.fill(bx - 1, iy - 1, bx + 9, iy + 10, 0xDD15191F);
                gui.fill(bx, iy, bx + 8, iy + 9,
                        i < ammo && colorId >= 1 && colorId <= 3 ? colors[colorId - 1] : 0xFF353B43);
            }
            int forced = ClientDataHolder.buffTicks("shenli_forced_reload");
            int windup = ClientDataHolder.buffTicks("shenli_basic_windup");
            int mind = ClientDataHolder.buffTicks("shenli_mind");
            int combo = Math.max(0, Math.min(4, ClientDataHolder.syncedCounter("shenli_combo")));
            String gunMode = Component.translatable(ClientDataHolder.syncedCounter("shenli_aiming") > 0
                    ? "hud.myriad.shenli_aim" : "hud.myriad.shenli_hip").getString();
            String state = windup > 0 ? String.format("射击后摇 %.2fs", windup / 20F)
                    : forced > 0 ? String.format("装填 %.2fs", forced / 20F)
                    : mind > 0 ? String.format("机心 %.1fs", mind / 20F) : gunMode + " " + combo + "/4";
            gui.text(font, state, ix + 27 - font.width(state) / 2, iy - 11,
                    windup > 0 ? 0xFFFFFFFF : forced > 0 ? 0xFFFFB36A : mind > 0 ? 0xFFFFE58A : 0xFFC8D3DC);
            int wTime = ClientDataHolder.buffTicks("shenli_w_time");
            if (wTime > 0) {
                int pointer = Math.max(1, Math.min(6, ClientDataHolder.syncedCounter("shenli_w_pointer")));
                int chosen = ClientDataHolder.syncedCounter("shenli_w_color");
                boolean precise = ClientDataHolder.syncedCounter("shenli_w_precise") > 0;
                int px = ix + (pointer - 1) * 11;
                gui.fill(px - 2, iy + 11, px + 10, iy + 13, precise ? 0xFFFFFFFF : 0xFFFF9B5A);
                String wText = String.format("定膛 %.2fs", wTime / 20F);
                gui.text(font, wText, ix + 27 - font.width(wText) / 2, iy + 16,
                        chosen >= 1 && chosen <= 3 ? colors[chosen - 1] : 0xFFFFFFFF);
            }
            int r = ClientDataHolder.buffTicks("shenli_r");
            if (r > 0) {
                int marks = ClientDataHolder.syncedCounter("shenli_fire_marks");
                String stamp = ((marks & 1) != 0 ? "赤" : "·") + ((marks & 2) != 0 ? "青" : "·")
                        + ((marks & 4) != 0 ? "金" : "·")
                        + (ClientDataHolder.syncedCounter("shenli_tristar") > 0 ? " 三曜待发" : "");
                gui.text(font, stamp, ix + 27 - font.width(stamp) / 2, iy - 21, 0xFFFFD66E);
            }
        } else if ("luoheng".equals(data.characterId())) {
            int ix=x-8,iy=y-42;
            String[] symbols={"◇","□","✦"};
            for(int i=0;i<3;i++){
                int remain=ClientDataHolder.buffTicks("luoheng_node_"+(i+1));int bx=ix+i*24;
                gui.fill(bx-1,iy-1,bx+19,iy+19,0xCC101923);
                gui.fill(bx,iy,bx+18,iy+18,remain>0?0xCC135A68:0xAA303640);
                gui.text(font,symbols[i],bx+5,iy+1,remain>0?0xFFE9FFFF:0xFF74808C);
                if(remain>0){String sec=String.valueOf((remain+19)/20);gui.text(font,sec,bx+10-font.width(sec)/2,iy+10,0xFFFFDF8A);}
            }
            int start=ClientDataHolder.syncedCounter("luoheng_w_start");
            int rTicks=ClientDataHolder.buffTicks("sanyuantianluo");
            if(start>0){String text="贯线起点 "+symbols[start-1];gui.text(font,text,ix+28-font.width(text)/2,iy-11,0xFFF1FDFF);}
            if(rTicks>0){
                int area=ClientDataHolder.syncedCounter("luoheng_triangle_area");
                String text=String.format("天罗 %.1fs  面积 %d",rTicks/20F,area);
                gui.text(font,text,ix+28-font.width(text)/2,iy-21,0xFFFFC768);
                String edges=String.format("%.1f / %.1f / %.1f",
                        ClientDataHolder.syncedCounter("luoheng_edge_1")/10F,
                        ClientDataHolder.syncedCounter("luoheng_edge_2")/10F,
                        ClientDataHolder.syncedCounter("luoheng_edge_3")/10F);
                gui.text(font,edges,ix+28-font.width(edges)/2,iy+21,
                        ClientDataHolder.buffTicks("luoheng_rebuild")>0?0xFFFF765C:0xFF8DEAF2);
            }
        } else if ("zhechuan".equals(data.characterId())) {
            int ix = x - 9, iy = y - 39;
            boolean corridor = ClientDataHolder.buffTicks("zhubiaowujinhuilang") > 0;
            int cap = corridor ? 5 : 3;
            for (int i = 0; i < 5; i++) {
                int bx = ix + i * 14;
                int remain = ClientDataHolder.buffTicks("zhechuan_marker_" + (i + 1));
                int frame = i < cap ? 0xDD172630 : 0x66303840;
                int markerFill = remain > 0 ? (corridor ? 0xFFD2A84A : 0xFF227986) : 0xFF303942;
                gui.fill(bx - 1, iy - 1, bx + 11, iy + 12, frame);
                gui.fill(bx, iy, bx + 10, iy + 11, markerFill);
                gui.text(font, String.valueOf(i + 1), bx + 3, iy + 1,
                        remain > 0 ? 0xFFF2FEFF : 0xFF79858E);
            }
            int retreat = ClientDataHolder.buffTicks("zhechuan_retreat");
            if (retreat > 0) {
                String retreatText = String.format("退标 %.1fs", retreat / 20F);
                gui.text(font, retreatText, ix + 28 - font.width(retreatText) / 2, iy + 14, 0xFFEAFDFF);
            }
            int returns = Math.max(0, Math.min(3, ClientDataHolder.syncedCounter("zhechuan_return_stacks")));
            if (corridor) {
                String rText = "归锋 " + returns + "/3";
                gui.text(font, rText, ix + 28 - font.width(rText) / 2, iy - 11,
                        returns >= 3 ? 0xFFFFE080 : 0xFF9EF4F1);
            }
        } else if ("sulan".equals(data.characterId())) {
            boolean windReady = ClientDataHolder.syncedCounter("sulan_passive_ready") > 0;
            Skill[] passives = {com.myriad.character.rework.FirstGroupSkills.selected("sulan",0,data.skillLevels())};
            if (passives.length > 0) {
                int ix = x + 11, iy = y - 31;
                if (windReady) gui.fill(ix - 2, iy - 2, ix + 16, iy + 16, 0xFF84FFE0);
                SkillIcon.drawBuff(gui, font, ix, iy, 14, passives[0]);
                if (!windReady) gui.fill(ix, iy, ix + 14, iy + 14, 0xA8000000);
                gui.text(font, Component.translatable(windReady ? "hud.myriad.wind_ready" : "hud.myriad.wind_charging"),
                        ix + 18, iy + 3, windReady ? 0xFF84FFE0 : 0xFF74808C);
            }
        } else if ("guanlan".equals(data.characterId()) || "xuanjia".equals(data.characterId())) {
            boolean guanlan = "guanlan".equals(data.characterId());
            int cap = guanlan ? 4 : 3;
            String buffId = guanlan ? "guanlan_passive" : "xuanjia_passive";
            int stacks = Math.max(0, Math.min(cap, ClientDataHolder.syncedCounter(buffId)));
            CharacterType role = guanlan ? CharacterType.GUANLAN : CharacterType.XUANJIA;
            Skill[] passives = role.passives();
            if (passives.length > 0) {
                int ix = x + 11;
                int iy = y - 31;
                SkillIcon.drawBuff(gui, font, ix, iy, 14, passives[0]);
                if (stacks == 0) gui.fill(ix, iy, ix + 14, iy + 14, 0xA8000000);
                String stackText = "×" + stacks;
                int activeColor = guanlan ? 0xFF8AD9FF : 0xFFD9B06C;
                gui.text(font, stackText, ix + 18, iy + 3,
                        stacks >= cap ? 0xFFFFF09A : (stacks > 0 ? activeColor : 0xFF74808C));
                if (stacks >= cap) {
                    gui.fill(ix - 2, iy - 2, ix + 16, iy - 1, 0xFFFFE56E);
                    gui.fill(ix - 2, iy + 15, ix + 16, iy + 16, 0xFFFFE56E);
                }
            }
        }
    }

    /** 法师专属：位于普攻条下方的蓝色法术弹攻速回复条。 */
    private void renderMagicBulletBar(GuiGraphicsExtractor gui, Font font, PlayerDataPayload data) {
        String character = data.characterId();
        if (!"xuanchen".equals(character) && !"hanze".equals(character)
                && !"qingluo".equals(character) && !"yunsheng".equals(character)
                && !"guanlan".equals(character) && !"luoheng".equals(character) && !"mingzhu".equals(character)) return;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int width = 48, height = 5;
        int x = sw / 2 + 94;
        int y = sh - 17;
        int max = Math.max(1, ClientDataHolder.magicBulletCooldownMax());
        int cooldown = ClientDataHolder.magicBulletCooldownNow();
        float ready = 1.0F - Math.min(1.0F, Math.max(0.0F, cooldown / (float) max));
        boolean fullReady = cooldown <= 0;
        int fill = Math.round(width * ready);
        gui.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF101827);
        gui.fill(x, y, x + width, y + height, 0xCC06162B);
        if (fill > 0) gui.fill(x, y, x + fill, y + height,
                fullReady ? 0xFF58AFFF : 0xFF267DD8);
        String label = fullReady ? "法术弹" : String.format("%.1fs", cooldown / 20.0F);
        gui.text(font, label, x - font.width(label) - 4, y - 2,
                fullReady ? 0xFF8DCCFF : 0xFF69AEEA);
    }

    /** 屏幕下方居中技能栏：Q / W / E / R / F 技能图标槽（横向排列，放大 1.5 倍），冷却倒计时 / 技能等级 */
    private void renderSkillBar(GuiGraphicsExtractor gui, Font font, PlayerDataPayload data) {
        CharacterType type = CharacterType.byId(data.characterId());
        if (type == null) {
            return;
        }
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        java.util.List<Skill> skills = new java.util.ArrayList<>();
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        String[] keyLabels = { "①", "②", "③", "④", "⑤" };
        for (int slot = 0; slot < CharacterType.SKILL_SLOTS; slot++) {
            Skill skill = com.myriad.character.rework.FirstGroupSkills.resolve(type, slot, data.skillLevels());
            if (skill != null) {
                skills.add(skill);
                slots.add(slot);
            }
        }
        if (skills.isEmpty()) {
            return;
        }
        float total = UI_SCALE * fitScale();
        int slotSize = 28;
        int gap = 4;
        gui.pose().pushMatrix();
        gui.pose().scale(total);
        int x = (int) (6 / total);
        int y = (int) ((screenHeight / 2 - slotSize / 2) / total);
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            int slot = slots.get(i);
            int cx = x + i * (slotSize + gap);
            int cdTicks = ClientDataHolder.cooldownTicks(slot);
            int lv = data.skillLevels().getOrDefault(skill.getId(), 0);
            boolean ultimate = skill.isUltimate();
            SkillIcon.draw(gui, font, cx, y, slotSize, skill, ultimate, cdTicks <= 0, keyLabels[slot]);
            if (cdTicks > 0) {
                gui.fill(cx, y, cx + slotSize, y + slotSize, 0x66000000);
                String cd = String.valueOf((cdTicks + 19) / 20);
                gui.text(font, cd, cx + (slotSize - font.width(cd)) / 2, y + slotSize - 9, CD_COLOR);
            }
            int charges = SkillChargeDisplay.chargeCount(skill, data.buffs());
            if (charges >= 0) {
                String count = Integer.toString(charges);
                int badgeWidth = Math.max(10, font.width(count) + 6);
                int badgeX = cx + (slotSize - badgeWidth) / 2;
                int badgeY = y + slotSize + 1;
                gui.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + font.lineHeight + 2, 0xCC101722);
                String chargeKey = "skill_charge_" + skill.getId();
                int remaining = data.buffs().getOrDefault(chargeKey + "_remaining", 0);
                int totalTicks = data.buffs().getOrDefault(chargeKey + "_total", 0);
                if (remaining > 0 && totalTicks > 0 && charges < skill.getMaxCharges()) {
                    double progress = 1.0 - Math.min(1.0, remaining / (double) totalTicks);
                    int ringCx = cx + slotSize / 2;
                    int ringCy = badgeY + (font.lineHeight + 2) / 2;
                    for (int part = 0; part < 24; part++) {
                        double angle = -Math.PI / 2 + Math.PI * 2 * part / 24.0;
                        int px = ringCx + (int)Math.round(Math.cos(angle) * (badgeWidth / 2.0 + 2));
                        int py = ringCy + (int)Math.round(Math.sin(angle) * (font.lineHeight / 2.0 + 3));
                        int color = part / 24.0 <= progress ? 0xFF65DDF2 : 0xFF334653;
                        gui.fill(px, py, px + 1, py + 1, color);
                    }
                }
                gui.text(font, count, badgeX + (badgeWidth - font.width(count)) / 2, badgeY + 1,
                        charges > 0 ? 0xFF72E8FF : 0xFF8A929C);
            }
            int secondCastTicks = SecondCastDisplay.remainingTicks(skill.getId());
            if (secondCastTicks > 0) {
                String seconds = String.format(java.util.Locale.ROOT, "%.1fs", secondCastTicks / 20.0F);
                int timeX = cx + (slotSize - font.width(seconds)) / 2;
                int timeY = y - font.lineHeight * 2 - 5;
                gui.fill(timeX - 2, timeY - 1, timeX + font.width(seconds) + 2,
                        timeY + font.lineHeight + 1, 0xD0181018);
                gui.text(font, seconds, timeX, timeY, secondCastTicks <= 10 ? 0xFFFF746A : 0xFFFFD46A);
            }
            // 状态标签放在图标上方完整一行，避免与图标边框和内容重叠。
            int labelY = y - font.lineHeight - 2;
            if (lv <= 0) {
                String lvLabel = "未学习";
                gui.text(font, lvLabel, cx + slotSize - font.width(lvLabel) - 1, labelY, 0xFF9E9E9E);
            } else {
                String lvLabel = "Lv" + lv;
                gui.text(font, lvLabel, cx + slotSize - font.width(lvLabel) - 1, labelY, 0xFF55FFFF);
            }
        }
        gui.pose().popMatrix();
    }
}
