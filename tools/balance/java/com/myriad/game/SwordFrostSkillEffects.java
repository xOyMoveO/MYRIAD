package com.myriad.game;

import static com.myriad.game.RosterSnapshot.effect;
import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.gap;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.skills.hanze.rework.*;
import com.myriad.character.skills.lingyuan.rework.*;
import com.myriad.combat.HeroProjectileTuning;

final class SwordFrostSkillEffects {
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";
    private static final String NONE = "not_timed";
    private static final String PULSE = "每次脉冲重新判定仍在范围内且视线通畅的合法敌人；间接伤害";
    private static final String SELF_SHIELD = "合法施放后授予自身模组护盾；同源取较高有效值";
    private static final String KNOCKED_OUT = "已提交资源后驻足蓄力，受眩晕、沉默或强制位移可中断";
    private static final String FIRST_TARGET = "每枚按实际发射朝向独立命中首敌；墙或风墙可截断";

    private SwordFrostSkillEffects() {}

    static String damageCondition(AbilitySkill skill, String id) {
        return switch (skill) {
            case SwordHeartSkill ignored -> "已凝剑且最近普攻目标仍在射程及视线内；每柄独立间接伤害";
            case SwordResonanceSkill ignored -> "成功提交三次主动施法后首次有效普攻的独立魔法附伤";
            case CloudBreakSkill ignored -> "实际身体突进路径的扫掠命中同一敌人至多一次";
            case SwordVolleySkill ignored -> FIRST_TARGET;
            case SwordArraySkill ignored -> PULSE;
            case ThousandSwordsSkill ignored -> PULSE;
            case HeavenSwordSkill ignored -> "蓄力与落剑完成后，目标本体碰撞箱与起手朝向矩形相交且视线通畅；一次伤害";
            case FrostStacksSkill ignored -> "同目标四次合格直接命中；每次施法在该目标最多一层，免疫窗中不叠层";
            case IceVolleySkill ignored -> FIRST_TARGET;
            case AbsoluteZeroSkill ignored -> "付费蓄力未被打断，爆发时目标仍在选点范围内且视线通畅";
            case GlacierLanceSkill ignored -> "冰棱实际穿过且未先命中墙或风墙；同一敌人至多一次";
            case FrozenExpanseSkill ignored -> PULSE + "，不会叠加寒霜";
            case WinterBastionSkill ignored -> "合法施放，施法者身边伤害半径内且视线通畅的每个敌人受一次伤害";
            default -> throw new IllegalArgumentException("No damage for " + skill.getId() + "." + id);
        };
    }

    static String supportCondition(AbilitySkill skill) {
        return switch (skill) {
            case SwordResonanceSkill ignored -> "三次主动提交后首次有效普攻；消耗蓄势并授予自身护盾";
            case SwordMantleSkill ignored -> SELF_SHIELD;
            case FrostShieldSkill ignored -> SELF_SHIELD;
            case RimeSkinSkill ignored -> "自身冷却结束后的直接有效普攻或技能命中；授予自身护盾";
            case WinterBastionSkill ignored -> "授予自身及范围内存活、非旁观、视线通畅的友方英雄，逐人结算";
            default -> throw new IllegalArgumentException("No support for " + skill.getId());
        };
    }

    static void add(JsonArray effects, AbilitySkill skill) {
        boolean control = true;
        switch (skill) {
            case SwordHeartSkill ignored -> {
                value(effects, skill, "qi_per_basic", "resource_gain", SwordHeartSkill.QI_PER_BASIC, "qi", "QI_PER_BASIC", "单刻一次有效普攻", NONE);
                value(effects, skill, "qi_per_sword", "resource_threshold", SwordHeartSkill.QI_PER_SWORD, "qi", "QI_PER_SWORD", "达到阈值时凝一柄", NONE);
                value(effects, skill, "max_swords", "trigger", SwordHeartSkill.MAX_SWORDS, "count", "MAX_SWORDS", "同时保留的飞剑数", NONE);
                value(effects, skill, "sword_range", "range", SwordHeartSkill.RANGE, "blocks", "RANGE", "检查最近普攻目标", NONE);
                time(effects, skill, "volley_interval", SwordHeartSkill.VOLLEY_TICKS, "VOLLEY_TICKS", "每轮飞剑开始时计时", REAL);
                time(effects, skill, "sword_gap", SwordHeartSkill.SWORD_GAP_TICKS, "SWORD_GAP_TICKS", "单轮中的相邻飞剑", ACTION);
                control = false;
            }
            case SwordResonanceSkill ignored -> {
                value(effects, skill, "casts_to_charge", "trigger", SwordResonanceSkill.CASTS_TO_CHARGE, "cast", "CASTS_TO_CHARGE", "在主动提交时加一，满后等有效普攻", NONE);
                shield(effects, skill, SwordResonanceSkill.SHIELD_TICKS);
                control = false;
            }
            case CloudBreakSkill ignored -> {
                value(effects, skill, "dash_range", "displacement", CloudBreakSkill.DASH_RANGE, "blocks", "DASH_RANGE", "有效直线移动，障碍可提前截断", NONE);
                value(effects, skill, "dash_radius", "range", CloudBreakSkill.HIT_RADIUS, "blocks", "HIT_RADIUS", "传给共用扫掠判定的名义参数", NONE);
                time(effects, skill, "dash_duration", CloudBreakSkill.DASH_TICKS, "DASH_TICKS", "完成位移的名义行动刻数", ACTION);
                control = false;
            }
            case SwordMantleSkill ignored -> {
                shield(effects, skill, SwordMantleSkill.SHIELD_TICKS);
                time(effects, skill, "guard_duration", SwordMantleSkill.GUARD_TICKS, "GUARD_TICKS", "受到非真实伤害时检查是否在此窗口", REAL);
                value(effects, skill, "incoming_rate", "incoming_damage_multiplier", SwordMantleSkill.INCOMING_RATE,
                        "ratio", "INCOMING_RATE", "窗口内已减抗的非真实伤害乘数，随后消耗护盾", NONE);
                control = false;
            }
            case SwordVolleySkill ignored -> {
                shot(effects, skill, SwordVolleySkill.RANGE, SwordVolleySkill.SPEED, SwordVolleySkill.HIT_RADIUS, FIRST_TARGET);
                time(effects, skill, "shot_gap", SwordVolleySkill.SHOT_GAP_TICKS, "SHOT_GAP_TICKS", "三柄逐枚发射", ACTION);
                control = false;
            }
            case SwordArraySkill ignored -> {
                pointField(effects, skill, SwordArraySkill.POINT_RANGE, SwordArraySkill.RADIUS, SwordArraySkill.PULSE_TICKS, SwordArraySkill.DURATION_TICKS);
                slow(effects, skill, SwordArraySkill.SLOW, SwordArraySkill.SLOW_TICKS, PULSE);
            }
            case ThousandSwordsSkill ignored -> {
                pointField(effects, skill, ThousandSwordsSkill.POINT_RANGE, ThousandSwordsSkill.RADIUS, ThousandSwordsSkill.PULSE_TICKS, ThousandSwordsSkill.DURATION_TICKS);
                control = false;
            }
            case HeavenSwordSkill ignored -> {
                value(effects, skill, "chop_range", "range", HeavenSwordSkill.REACH, "blocks", "REACH", "从起手点向前的实际矩形长度", NONE);
                value(effects, skill, "chop_width", "range", HeavenSwordSkill.WIDTH, "blocks", "WIDTH", "从起手固定朝向向左右均分", NONE);
                time(effects, skill, "charge", HeavenSwordSkill.CHARGE_TICKS, "CHARGE_TICKS", KNOCKED_OUT, ACTION);
                time(effects, skill, "chop", HeavenSwordSkill.CHOP_TICKS, "CHOP_TICKS", "开始挥落后的相同行动封锁；结束才结算", ACTION);
                control = false;
            }
            case FrostStacksSkill ignored -> {
                value(effects, skill, "stacks", "trigger", FrostStacksSkill.STACKS, "stack", "STACKS", "每目标独立；到阈值清空层数、追加伤害和定身", NONE);
                time(effects, skill, "min_hit_gap", FrostStacksSkill.MIN_HIT_GAP_TICKS, "MIN_HIT_GAP_TICKS", "同目标下两次有效叠层需要的最小间隔", REAL);
                time(effects, skill, "stack_window", FrostStacksSkill.STACK_WINDOW_TICKS, "STACK_WINDOW_TICKS", "有效叠层刷新；超过末刻清空", REAL);
                time(effects, skill, "immunity", FrostStacksSkill.IMMUNITY_TICKS, "IMMUNITY_TICKS", "触发后仅同一施法者对该目标的叠层冷却", REAL);
                root(effects, skill, FrostStacksSkill.ROOT_TICKS, "达到叠层门槛，基础定身时长取韧性折减前");
            }
            case RimeSkinSkill ignored -> {
                shield(effects, skill, RimeSkinSkill.SHIELD_TICKS);
                time(effects, skill, "internal_cooldown", RimeSkinSkill.COOLDOWN_TICKS, "COOLDOWN_TICKS", "以施法者计，最先合格命中启动", REAL);
                closeRange(effects, skill, RimeSkinSkill.RADIUS, "凝霜甲触发时的施法者周围");
                slow(effects, skill, RimeSkinSkill.SLOW, RimeSkinSkill.SLOW_TICKS, "触发时在施法者周围且视线通畅的敌人");
            }
            case IceVolleySkill ignored -> {
                shot(effects, skill, IceVolleySkill.RANGE, IceVolleySkill.SPEED, IceVolleySkill.HIT_RADIUS, FIRST_TARGET);
                time(effects, skill, "shot_gap", IceVolleySkill.SHOT_GAP_TICKS, "SHOT_GAP_TICKS", "三枚逐枚发射", ACTION);
                slow(effects, skill, IceVolleySkill.SLOW, IceVolleySkill.SLOW_TICKS, "冰锥与首个合法敌人相撞");
            }
            case FrostShieldSkill ignored -> {
                shield(effects, skill, FrostShieldSkill.SHIELD_TICKS);
                closeRange(effects, skill, FrostShieldSkill.RADIUS, "施法时的施法者周围");
                slow(effects, skill, FrostShieldSkill.SLOW, FrostShieldSkill.SLOW_TICKS, "施放时在施法者周围且视线通畅的敌人");
            }
            case AbsoluteZeroSkill ignored -> {
                point(effects, skill, AbsoluteZeroSkill.POINT_RANGE, AbsoluteZeroSkill.RADIUS);
                time(effects, skill, "windup", AbsoluteZeroSkill.WINDUP_TICKS, "WINDUP_TICKS", KNOCKED_OUT, ACTION);
                root(effects, skill, AbsoluteZeroSkill.ROOT_TICKS, "蓄力结束且目标在爆发范围内并可见");
            }
            case GlacierLanceSkill ignored -> {
                shot(effects, skill, GlacierLanceSkill.RANGE, GlacierLanceSkill.SPEED, GlacierLanceSkill.HIT_RADIUS, "冰棱贯穿；墙和风墙可提前截断");
                slow(effects, skill, GlacierLanceSkill.SLOW, GlacierLanceSkill.SLOW_TICKS, "每个实际碰到的敌人");
            }
            case FrozenExpanseSkill ignored -> {
                pointField(effects, skill, FrozenExpanseSkill.POINT_RANGE, FrozenExpanseSkill.RADIUS, FrozenExpanseSkill.PULSE_TICKS, FrozenExpanseSkill.DURATION_TICKS);
                slow(effects, skill, FrozenExpanseSkill.SLOW, FrozenExpanseSkill.SLOW_TICKS, PULSE);
            }
            case WinterBastionSkill ignored -> {
                value(effects, skill, "ally_radius", "range", WinterBastionSkill.ALLY_RADIUS, "blocks", "ALLY_RADIUS", supportCondition(skill), NONE);
                value(effects, skill, "enemy_radius", "range", WinterBastionSkill.ENEMY_RADIUS, "blocks", "ENEMY_RADIUS", damageCondition(skill, "damage"), NONE);
                shield(effects, skill, WinterBastionSkill.SHIELD_TICKS);
                root(effects, skill, WinterBastionSkill.ROOT_TICKS, "施放位置周围在伤害半径内并可见的敌人");
            }
            default -> throw new IllegalArgumentException(skill.getId());
        }
        if (!control) unavailable(effects, skill, "control");
        if (skill.damageFormulas().isEmpty()) unavailable(effects, skill, "damage");
        if (skill.supportAmounts().isEmpty()) unavailable(effects, skill, "shield");
        unavailable(effects, skill, "heal");
    }

    private static void pointField(JsonArray effects, AbilitySkill skill, double reach, double radius, int period, int duration) {
        point(effects, skill, reach, radius);
        time(effects, skill, "pulse_interval", period, "PULSE_TICKS", "第一个完整间隔才结算首次伤害", REAL);
        time(effects, skill, "field_duration", duration, "DURATION_TICKS", "最后一跳在字段结束刻", REAL);
    }

    private static void point(JsonArray effects, AbilitySkill skill, double reach, double radius) {
        value(effects, skill, "point_range", "range", reach, "blocks", "POINT_RANGE", "施放者确认的合法地面选点范围", NONE);
        value(effects, skill, "radius", "range", radius, "blocks", "RADIUS", "从确认时的固定区心测量三维距离", NONE);
    }

    private static void closeRange(JsonArray effects, AbilitySkill skill, double radius, String condition) {
        value(effects, skill, "radius", "range", radius, "blocks", "RADIUS", condition, NONE);
    }

    private static void shot(JsonArray effects, AbilitySkill skill, double reach, double speed, double radius, String condition) {
        value(effects, skill, "projectile_range", "range", reach, "blocks", "RANGE", condition, NONE);
        value(effects, skill, "projectile_speed", "projectile_speed", speed * HeroProjectileTuning.SPEED,
                "blocks_per_tick", "SPEED", "乘通用英雄弹体速度系数；原始未缓时推进", ACTION);
        value(effects, skill, "projectile_padding", "projectile_padding", radius * HeroProjectileTuning.ENTITY_PADDING,
                "blocks", "HIT_RADIUS", "乘英雄弹体扩张系数后的额外判定箱", NONE);
    }

    private static void slow(JsonArray effects, AbilitySkill skill, float amount, int ticks, String condition) {
        value(effects, skill, "movement_rate", "movement_slow", 1.0f - amount, "ratio", "SLOW", condition, NONE);
        value(effects, skill, "slow_duration", "control_duration", ticks, "ticks", "SLOW_TICKS", condition, REAL);
    }

    private static void root(JsonArray effects, AbilitySkill skill, int ticks, String condition) {
        value(effects, skill, "root_duration", "control_duration", ticks, "ticks", "ROOT_TICKS", condition, REAL);
    }

    private static void shield(JsonArray effects, AbilitySkill skill, int ticks) {
        value(effects, skill, "shield_duration", "shield_duration", ticks, "ticks", "SHIELD_TICKS", supportCondition(skill), REAL);
    }

    private static void time(JsonArray effects, AbilitySkill skill, String id, int ticks, String member, String condition, String clock) {
        value(effects, skill, id, "timing", ticks, "ticks", member, condition, clock);
    }

    private static void value(JsonArray effects, AbilitySkill skill, String id, String type, Object value,
                              String unit, String member, String condition, String clock) {
        effects.add(effect(skill, id, type, field(value, unit, source(skill.getClass(), member), condition, "效果结算", clock)));
    }

    private static void unavailable(JsonArray effects, AbilitySkill skill, String type) {
        effects.add(effect(skill, type, type, gap("not_applicable", "not_applicable", source(skill.getClass(), "release"),
                "独立技能定义", "技能结算", NONE, "该候选没有该效果")));
    }
}
