package com.myriad.game;

/**
 * 训练模式（服务端全局开关，命令 /myriad train on|off / GUI）。
 * 开启后：跳过正式对局阶段校验，只要已选择角色即可施放技能；玩家仍可选择
 * 玄霄宗或幽冥宗，技能、普攻和战术装置沿用正式对局的敌我规则。
 * 额外开关（train on 后默认全部关闭，由训练 GUI 单独开启）：
 *  - 无冷却：技能施放不进入冷却；
 *  - 无限金币：商店购买不消耗金币；
 *  - 无限蓝：技能施放不检查、不扣除法力。
 */
public final class TrainingMode {
    private static boolean enabled = false;
    private static boolean noCooldown = false;
    private static boolean noGoldCost = false;
    private static boolean noMana = false;

    private TrainingMode() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isNoCooldown() {
        return noCooldown;
    }

    public static void setNoCooldown(boolean value) {
        noCooldown = value;
    }

    public static boolean isNoGoldCost() {
        return noGoldCost;
    }

    public static void setNoGoldCost(boolean value) {
        noGoldCost = value;
    }

    public static boolean isNoMana() {
        return noMana;
    }

    public static void setNoMana(boolean value) {
        noMana = value;
    }
}
