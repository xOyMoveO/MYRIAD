package com.myriad.character;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.myriad.Myriad;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;

/**
 * 技能释放的统一前置校验与分发（服务端）。
 * 校验顺序：对局进行中 -> 阵营已分配 -> 已选择角色 -> 角色阵营匹配 -> 已学习(等级>0) -> 冷却 -> 法力。
 */
public final class SkillSystem {
    private SkillSystem() {}

    /** 槽位技能：0=Q, 1=W, 2=E, 3=R, 4=F；训练模式下跳过对局/阵营/法力/冷却校验与扣除 */
    public static void cast(ServerPlayer player, int slot) {
        com.myriad.game.RecallSystem.cancel(player);
        if (player.isSpectator() || !player.isAlive()) return;
        if (!com.myriad.game.ModState.isEnabled()) {
            player.sendSystemMessage(Component.translatable("message.myriad.mod_off"));
            return;
        }
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null ) {
            return;
        }
        if (com.myriad.character.ability.SkillChargeSystem.active(player)) return;
        boolean training = com.myriad.game.TrainingMode.isEnabled();
        GameManager manager = GameManager.get();
        if (!training && (manager == null || manager.getPhase() != GameManager.Phase.PLAYING)) {
            Myriad.LOGGER.info("[cast] {} rejected: phase={}", player.getName().getString(),
                    manager == null ? "no-manager" : manager.getPhase());
            player.sendSystemMessage(Component.translatable("message.myriad.match_not_playing"));
            return;
        }
        if (!training && data.getFaction() == null) {
            Myriad.LOGGER.info("[cast] {} rejected: no faction (phase={})",
                    player.getName().getString(), manager == null ? "-" : manager.getPhase());
            player.sendSystemMessage(Component.translatable("message.myriad.no_faction"));
            return;
        }
        CharacterType type = CharacterType.byId(data.getCharacterId());
        if (type == null) {
            player.sendSystemMessage(Component.translatable("message.myriad.no_character"));
            return;
        }
        if (com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())) {
            Skill selected = com.myriad.character.rework.FirstGroupSkills.resolve(type, slot, data.getSkillLevels());
            if (selected instanceof com.myriad.character.ability.AbilitySkill ability)
                com.myriad.character.ability.AbilityRuntime.request(player, data, ability);
            return;
        }
        if (data.getBuff("g1_stun") > 0) return;
        if (com.myriad.character.skills.yunsheng.YunShengSkills.isSilenced(player)) {
            player.sendSystemMessage(Component.translatable("message.myriad.silenced"));
            return;
        }
        Skill skill = type.skill(slot);
        if (skill == null) {
            return;
        }
        // 未学习（等级 0）：加点后才可释放（训练模式为测试沙盒，不受限）
        if (!training && data.skillLevel(skill.getId()) <= 0) {
            player.sendSystemMessage(Component.translatable("message.myriad.skill_not_learned"));
            return;
        }
        boolean noCd = training && com.myriad.game.TrainingMode.isNoCooldown();
        boolean noMana = training && com.myriad.game.TrainingMode.isNoMana();
        if (type == CharacterType.MINGZHU) {
            com.myriad.character.skills.mingzhu.MingZhuSkills.handleCast(player, data, slot, skill, noCd, noMana);
            return;
        }
        if (type == CharacterType.WUXIANG) {
            com.myriad.character.skills.wuxiang.WuxiangSkills.handleCast(
                    player, data, slot, skill, noCd, noMana);
            return;
        }
        if (type == CharacterType.FEIYU) {
            com.myriad.character.skills.feiyu.FeiYuSkills.handleCast(
                    player, data, slot, skill, noCd, noMana);
            return;
        }
        if (type == CharacterType.WANYUE) {
            com.myriad.character.skills.wanyue.WanyueSkills.handleCast(
                    player, data, slot, skill, noCd, noMana);
            return;
        }
        if (type == CharacterType.LUOHENG) {
            com.myriad.character.skills.luoheng.LuoHengSkills.handleCast(
                    player, data, slot, skill, noCd, noMana);
            return;
        }
        if (type == CharacterType.SHENLI) {
            com.myriad.character.skills.shenli.ShenLiSkills.handleCast(
                    player, data, slot, skill, noCd, noMana);
            return;
        }
        if (type == CharacterType.ZHECHUAN) {
            com.myriad.character.skills.zhechuan.ZheChuanSkills.handleCast(
                    player, data, slot, skill, noCd, noMana);
            return;
        }
        // 冷却（训练模式无冷却开关开启时跳过）
        boolean chixiaoReturn = type == CharacterType.CHIXIAO && slot == CharacterType.SLOT_W
                && com.myriad.character.skills.chixiao.ChiXiaoSkills.canReturn(player);
        int cd = data.getCooldown(skill.getId());
        if (!chixiaoReturn && !noCd && cd > 0) {
            int seconds = (cd + 19) / 20;
            player.sendSystemMessage(Component.translatable("message.myriad.skill_cd", seconds));
            return;
        }
        // 法力（训练模式无限蓝开关开启时跳过）
        boolean swordUser = type == CharacterType.LINGYUAN;
        if (swordUser && slot == CharacterType.SLOT_R && data.getFlyingSwords() <= 0) {
            player.sendSystemMessage(Component.literal("至少需要 1 把飞剑才能施展万剑归宗"));
            return;
        }
        if (swordUser && slot == CharacterType.SLOT_W && data.getFlyingSwords() < 3) {
            player.sendSystemMessage(Component.translatable("message.myriad.lingyuan_sword_form_need_swords"));
            return;
        }
        if (type == CharacterType.QINGLUO && slot == CharacterType.SLOT_E
                && !com.myriad.character.skills.qingluo.QingLuoSkills.hasLinkTarget(player)) {
            player.sendSystemMessage(Component.literal("请面朝18格内的友方英雄施放牵丝夺厄"));
            return;
        }
        if (type == CharacterType.YUNSHENG
                && !com.myriad.character.skills.yunsheng.YunShengSkills.hasTarget(player, slot)) {
            player.sendSystemMessage(Component.translatable("message.myriad.no_skill_target"));
            return;
        }
        if (type == CharacterType.LIGE
                && !com.myriad.character.skills.lige.LiGeSkills.hasTarget(player, slot)) {
            player.sendSystemMessage(Component.translatable("message.myriad.no_skill_target"));
            return;
        }
        if (type == CharacterType.GUANLAN
                && !com.myriad.character.skills.guanlan.GuanLanSkills.canCast(player, slot)) {
            player.sendSystemMessage(Component.translatable("message.myriad.guanlan_no_meteor_charge"));
            return;
        }
        if (!chixiaoReturn && !swordUser && !noMana && data.getMana() < skill.getManaCost()) {
            player.sendSystemMessage(Component.translatable("message.myriad.not_enough_mana"));
            return;
        }
        skill.cast(player, data);
        SkillPresentation.cast(player, skill);
        // 墨尘被动【阵眼共鸣】：每次释放技能为周围 8 格友方回复 5 法力
        if (CharacterType.byId(data.getCharacterId()) == CharacterType.XUANCHEN) {
            com.myriad.character.skills.xuanchen.XuanChenPassiveSkill.onSkillCast(player);
        }
        if (skill.isUltimate()) {
            
        }
        if (!chixiaoReturn && !swordUser && !noMana) {
            data.spendMana(skill.getManaCost());
        }
        if (!chixiaoReturn && !noCd) {
            // 冷却缩减：cdr 百分比缩短冷却
            double cdr = Math.max(0.0, Math.min(60.0,
                    player.getAttributeValue(com.myriad.registry.ModAttributes.COOLDOWN_REDUCTION)));
            int cdTicks = (int) Math.max(1,
                    skill.getCooldownTicks(data.skillLevel(skill.getId())) * (1.0 - cdr / 100.0));
            // 墨尘被动【阵眼共鸣】：8 格内存在可见存活队友，技能冷却再缩减 20%。
            if (CharacterType.byId(data.getCharacterId()) == CharacterType.XUANCHEN
                    && com.myriad.character.skills.xuanchen.XuanChenPassiveSkill.nearAlly(player)) {
                cdTicks = (int) Math.max(1, cdTicks * 0.8);
            }
            data.setCooldown(skill.getId(), cdTicks);
        }
        PlayerDataProvider.syncToClient(player);
    }
}
