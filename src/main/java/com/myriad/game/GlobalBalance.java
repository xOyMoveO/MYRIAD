package com.myriad.game;

/** 仅保留经济奖励的统一换算；角色、装备、技能、野怪与建筑使用各自定义的实际数值。 */
public final class GlobalBalance {
    private GlobalBalance() {}

    public static final double GOLD_INCOME_SCALE = 1.40;

    public static int goldIncome(int amount) {
        return amount <= 0 ? amount : (int)Math.min(Integer.MAX_VALUE, Math.round(amount * GOLD_INCOME_SCALE));
    }
}
