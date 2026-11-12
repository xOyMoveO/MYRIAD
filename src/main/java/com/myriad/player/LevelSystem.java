package com.myriad.player;


import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import com.myriad.game.Faction;
import com.myriad.game.GameRules;

/**
 * 等级与经验系统（策划案第九节）。
 * <p>
 * 每升1级：生命上限 +2%、攻击力 +3%。
 * 分组重做角色最高 20 级；尚未重做角色沿用现有等级配置。
 */
public final class LevelSystem {
    private LevelSystem() {}

    public static final AttributeModifier.Operation LEVEL_HP_OP = AttributeModifier.Operation.ADD_VALUE;
    public static final AttributeModifier.Operation LEVEL_AD_OP = AttributeModifier.Operation.ADD_VALUE;

    private static final String HP_MODIFIER_ID = "myriad:level_hp_bonus";
    private static final String AD_MODIFIER_ID = "myriad:level_ad_bonus";
    private static final String SP_MODIFIER_ID = "myriad:level_sp_bonus";
    private static final String MP_MODIFIER_ID = "myriad:level_mp_bonus";
    private static final String AS_MODIFIER_ID = "myriad:level_as_bonus";
    private static final String MS_MODIFIER_ID = "myriad:level_ms_bonus";
    private static final String PD_MODIFIER_ID = "myriad:level_pd_bonus";
    private static final String MR_MODIFIER_ID = "myriad:level_mr_bonus";

    public static int maxLevel(Faction faction) {
        return GameRules.MAX_LEVEL;
    }
    public static int maxLevel(PlayerData data) {
        return data != null && com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())
                ? 20 : maxLevel(data == null ? null : data.getFaction());
    }

    /** 升到下一级所需经验 */
    public static int xpToNext(int level) {
        return GameRules.XP_BASE + (level - 1) * GameRules.XP_STEP;
    }



    /**
     * 增加经验，处理升级、属性加成和技能点。
     */
    public static void addXp(ServerPlayer player, PlayerData data, int amount) {
        if (!com.myriad.game.ModState.isEnabled() || amount < 0) {
            return;
        }
        Faction faction = data.getFaction();
        if (faction == null) {
            faction = Faction.XUANXIAO; // 未分宗门时仍使用相同成长规则，实际开局前不产生经验
        }
        data.setXp(data.getXp() + amount);
        int xp = data.getXp();
        int level = data.getLevel();
        int previousLevel = level;
        int maxLevel = maxLevel(data);
        boolean leveled = false;
        while (level < maxLevel && xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            level++;
            leveled = true;
        }
        data.setXp(xp);
        if (leveled) {
            float healthBefore = player.getHealth();
            float manaBefore = data.getMana();
            int pointsBefore = data.getSkillPoints();
            data.setLevel(level);
            applyLevelAttributes(player, level);
            // 技能点：每跨越 1 级 +1；一次经验跨多级时也必须逐级发放。
            data.setSkillPoints(pointsBefore + level - previousLevel);
            PlayerHealthSystem.recalculate(player);
            player.setHealth(Math.min(healthBefore, player.getMaxHealth()));
            data.setMana((float)Math.min(manaBefore, player.getAttributeValue(com.myriad.registry.ModAttributes.MAX_MANA)));
            com.myriad.game.MatchMessages.send(player,
                    com.myriad.game.MatchMessages.Tone.SUCCESS,
                    Component.translatable("message.myriad.level_up", level));
            data.ensureLevelRewards(level);
            LevelUpPresentation.play(player);
        }
        if (leveled || amount > 0) PlayerDataProvider.syncToClient(player);
    }

    /**
     * 按当前等级重算角色成长加成（覆盖式，等级变化时调用）。
     * 成长属性按角色配置（CharacterType.growthXxx），每级数值加法。
     */
    public static void applyLevelAttributes(ServerPlayer player, int level) {
        PlayerData data = PlayerDataProvider.get(player);
        com.myriad.character.CharacterType type = data == null ? null
                : com.myriad.character.CharacterType.byId(data.getCharacterId());
        if (data != null && com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())) {
            String[] ids = {HP_MODIFIER_ID, AD_MODIFIER_ID, SP_MODIFIER_ID, MP_MODIFIER_ID,
                    AS_MODIFIER_ID, MS_MODIFIER_ID, PD_MODIFIER_ID, MR_MODIFIER_ID};
            for (var attribute : player.getAttributes().getSyncableAttributes())
                for (String id : ids) attribute.removeModifier(net.minecraft.resources.Identifier.parse(id));
            com.myriad.character.rework.FirstGroupGrowth.initialize(player, data);
            return;
        }
        double bonus = type == null ? 0.0 : (level - 1);
        applyGrowth(player, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                HP_MODIFIER_ID, type == null ? 0.0 : type.growthHp() * bonus);
        applyGrowth(player, com.myriad.registry.ModAttributes.MAX_MANA,
                MP_MODIFIER_ID, type == null ? 0.0 : type.growthMp() * bonus);
        applyGrowth(player, com.myriad.registry.ModAttributes.PHYSICAL_ATTACK,
                AD_MODIFIER_ID, type == null ? 0.0 : type.growthAd() * bonus);
        applyGrowth(player, com.myriad.registry.ModAttributes.SPELL_POWER,
                SP_MODIFIER_ID, type == null ? 0.0 : type.growthSp() * bonus);
        applyGrowth(player, com.myriad.registry.ModAttributes.ATTACK_SPEED,
                AS_MODIFIER_ID, type == null ? 0.0 : type.growthAs() * bonus);
        applyGrowth(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                MS_MODIFIER_ID, type == null ? 0.0 : type.growthMs() * bonus);
        applyGrowth(player, com.myriad.registry.ModAttributes.PHYSICAL_DEFENSE,
                PD_MODIFIER_ID, type == null ? 0.0 : type.growthPd() * bonus);
        applyGrowth(player, com.myriad.registry.ModAttributes.MAGIC_RESISTANCE,
                MR_MODIFIER_ID, type == null ? 0.0 : type.growthMr() * bonus);
        PlayerHealthSystem.recalculate(player);
    }

    /** 覆盖式应用单项成长加成（无角色/无成长时清除） */
    private static void applyGrowth(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
            String modifierId, double amount) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.removeModifier(net.minecraft.resources.Identifier.parse(modifierId));
        if (amount != 0.0) {
            inst.addTransientModifier(new AttributeModifier(
                    net.minecraft.resources.Identifier.parse(modifierId), amount, LEVEL_HP_OP));
        }
    }
}
