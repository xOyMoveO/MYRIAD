package com.myriad.character;

import net.minecraft.server.level.ServerPlayer;

import com.myriad.player.PlayerData;

/**
 * 技能基类：id、冷却（刻）、法力消耗。
 * 施法前置校验（冷却/法力/对局阶段/阵营）统一由 {@link SkillSystem} 完成，
 * 子类只负责技能效果本身。
 */
public abstract class Skill {
    private final String id;
    private final int cooldownTicks;
    private final float manaCost;

    protected Skill(String id, int cooldownTicks, float manaCost) {
        this.id = id;
        this.cooldownTicks = cooldownTicks;
        this.manaCost = manaCost;
    }

    public String getId() {
        return id;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    /** 当前技能等级对应的基础冷却（刻）；默认不随等级变化。 */
    public int getCooldownTicks(int level) {
        return cooldownTicks;
    }

    public float getManaCost() {
        return manaCost;
    }

    /** 名称语言键：skill.myriad.<id> */
    public String nameKey() {
        return "skill.myriad." + id;
    }

    /** 描述语言键：skill.myriad.<id>.desc */
    public String descKey() {
        return nameKey() + ".desc";
    }

    /** 是否被动技能（常驻效果，不占用技能栏/冷却/施放） */
    public boolean isPassive() {
        return false;
    }

    /** 是否大招（终极技能）：使用大招强化点升级，每 3 级可强化一次 */
    public boolean isUltimate() {
        return false;
    }

    /** 最大充能数；大于 1 时 HUD 自动在该技能图标下显示同步充能数字。 */
    public int getMaxCharges() {
        return 1;
    }

    /** 当前技能等级的效果描述（属性面板技能悬停显示，含等级成长数值） */
    public String levelEffects(int level) {
        return "";
    }

    /** 释放技能效果（服务端） */
    public abstract void cast(ServerPlayer player, PlayerData data);
}
