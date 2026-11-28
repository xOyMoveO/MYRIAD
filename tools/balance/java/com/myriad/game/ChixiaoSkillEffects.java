package com.myriad.game;

import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.skills.chixiao.rework.BattleIntentSkill;
import com.myriad.character.skills.chixiao.rework.CrimsonParrySkill;
import com.myriad.character.skills.chixiao.rework.MountainCleaveSkill;
import com.myriad.character.skills.chixiao.rework.RendingWaveSkill;
import com.myriad.character.skills.chixiao.rework.SoulPursuitSkill;
import com.myriad.character.skills.chixiao.rework.UnbrokenWillSkill;
import com.myriad.character.skills.chixiao.rework.UnreturningLeapSkill;
import com.myriad.character.skills.chixiao.rework.WarBladeSkill;
import com.myriad.combat.HeroProjectileTuning;

final class ChixiaoSkillEffects {
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";
    private static final String NONE = "not_timed";

    private ChixiaoSkillEffects() {}

    static String damageCondition(AbilitySkill skill, String id) {
        return switch (skill) {
            case BattleIntentSkill ignored -> "对同一敌方英雄叠满战意后，下一次不同施法实例的有效直接技能命中";
            case MountainCleaveSkill ignored -> "前方扇区、视线未受阻，每个合法目标一次；系数读取有效额外物攻";
            case SoulPursuitSkill ignored -> "施法者沿实际去程扫掠且视线允许时命中，每名目标一次，回程无伤";
            case CrimsonParrySkill ignored -> "架刃期间触发减伤，窗口结束时反击范围内合法目标";
            case RendingWaveSkill ignored -> "刀罡实际飞行扫掠，遇墙提前终止，每个敌人一次";
            case UnreturningLeapSkill ignored -> "成功落于可站立地面后，落点周围合法目标各一次";
            case WarBladeSkill ignored -> "限时内最多强化三次有效普攻，独立伤害按实际命中时属性读取";
            default -> throw new IllegalArgumentException(skill.getId());
        };
    }

    static String supportCondition(AbilitySkill skill) {
        return switch (skill) {
            case UnbrokenWillSkill ignored -> "自身生命不高于阈值，合法有效命中且内置冷却结束";
            case WarBladeSkill ignored -> "合法主动施放后立即为自身授予护盾";
            default -> throw new IllegalArgumentException(skill.getId());
        };
    }

    static void add(JsonArray effects, AbilitySkill skill) {
        switch (skill) {
            case BattleIntentSkill ignored -> {
                add(effects, skill, "stack_count", "trigger", BattleIntentSkill.STACKS, "stacks", "STACKS", "同一敌方英雄的合格触发次数", NONE);
                add(effects, skill, "stack_window", "timing", BattleIntentSkill.WINDOW_TICKS, "ticks", "WINDOW_TICKS", "同一目标；每次合格命中刷新超时", REAL);
                add(effects, skill, "hit_interval", "timing", BattleIntentSkill.HIT_INTERVAL_TICKS, "ticks", "HIT_INTERVAL_TICKS", "两次计入战意的有效命中之间", REAL);
            }
            case UnbrokenWillSkill ignored -> {
                add(effects, skill, "health_threshold", "threshold", UnbrokenWillSkill.HEALTH_RATIO, "ratio", "HEALTH_RATIO", "触发前自身生命须不高于该比例", NONE);
                add(effects, skill, "internal_cooldown", "timing", UnbrokenWillSkill.COOLDOWN_TICKS, "ticks", "COOLDOWN_TICKS", "从触发时计", REAL);
                add(effects, skill, "shield_duration", "shield_duration", UnbrokenWillSkill.SHIELD_TICKS, "ticks", "SHIELD_TICKS", "低血有效命中且内置冷却结束", REAL);
                add(effects, skill, "movement_haste", "movement_haste", UnbrokenWillSkill.HASTE, "ratio", "HASTE", "护盾触发同时加速", NONE);
                add(effects, skill, "haste_duration", "timing", UnbrokenWillSkill.HASTE_TICKS, "ticks", "HASTE_TICKS", "护盾触发同时加速", REAL);
            }
            case MountainCleaveSkill ignored ->
                add(effects, skill, "range", "range", MountainCleaveSkill.RANGE, "blocks", "RANGE", "朝向扇区，障碍物可挡住目标", NONE);
            case SoulPursuitSkill ignored -> {
                add(effects, skill, "dash_range", "displacement", SoulPursuitSkill.DASH_RANGE, "blocks", "DASH_RANGE", "仅施法者；遇障碍缩短实际去程", ACTION);
                add(effects, skill, "dash_duration", "timing", SoulPursuitSkill.DASH_TICKS, "ticks", "DASH_TICKS", "无阻挡去程或回程名义行动刻", ACTION);
                add(effects, skill, "dash_hit_radius", "range", SoulPursuitSkill.HIT_RADIUS, "blocks", "HIT_RADIUS", "去程技能参数；身体扫掠还有通用扩张", NONE);
                add(effects, skill, "recast_window", "timing", SoulPursuitSkill.RECAST_WINDOW, "ticks", "RECAST_WINDOW", "去程提交后允许返回原点", REAL);
                add(effects, skill, "dash_speed", "speed", SoulPursuitSkill.DASH_RANGE / SoulPursuitSkill.DASH_TICKS,
                        "blocks/tick", "release", "无缓时和阻挡时的名义速度", ACTION);
                add(effects, skill, "movement_rate", "movement_multiplier", 1 - SoulPursuitSkill.SLOW,
                        "ratio", "SLOW", "命中后剩余移速倍率", NONE);
                add(effects, skill, "slow_rate", "movement_slow", SoulPursuitSkill.SLOW, "ratio", "SLOW", "沿实际去程命中", NONE);
                add(effects, skill, "slow_duration", "control_duration", SoulPursuitSkill.SLOW_TICKS, "ticks", "SLOW_TICKS", "沿实际去程命中，名义时长未减韧性", REAL);
            }
            case CrimsonParrySkill ignored -> {
                add(effects, skill, "range", "range", CrimsonParrySkill.RANGE, "blocks", "RANGE", "架刃曾触发时的前方反击范围", NONE);
                add(effects, skill, "brace_window", "timing", CrimsonParrySkill.BRACE_TICKS, "ticks", "BRACE_TICKS", "施放后即开始减伤，结束时才判断反击", REAL);
                add(effects, skill, "damage_taken_rate", "damage_multiplier", CrimsonParrySkill.DAMAGE_TAKEN_RATE, "ratio", "DAMAGE_TAKEN_RATE", "窗口内实际进入适用减伤路径的来伤", NONE);
                add(effects, skill, "stun_duration", "control_duration", CrimsonParrySkill.STUN_TICKS, "ticks", "STUN_TICKS", "实际反击时在前方扇区内，名义时长未减韧性", REAL);
            }
            case RendingWaveSkill ignored -> {
                add(effects, skill, "range", "range", RendingWaveSkill.RANGE, "blocks", "RANGE", "投射物名义最大距离，墙体可提前截断", ACTION);
                add(effects, skill, "projectile_speed", "projectile_speed", RendingWaveSkill.SPEED, "blocks/tick", "SPEED", "技能原速，实际通过共享投射物速度倍率", ACTION);
                add(effects, skill, "projectile_radius", "range", RendingWaveSkill.RADIUS, "blocks", "RADIUS", "贯穿目标的扫掠半径", NONE);
                add(effects, skill, "effective_projectile_speed", "projectile_speed", RendingWaveSkill.SPEED * HeroProjectileTuning.SPEED,
                        "blocks/tick", "release", "使用共享英雄弹道调节，未受局部缓时", ACTION);
                add(effects, skill, "movement_rate", "movement_multiplier", 1 - RendingWaveSkill.SLOW,
                        "ratio", "SLOW", "命中后剩余移速倍率", NONE);
                add(effects, skill, "slow_rate", "movement_slow", RendingWaveSkill.SLOW, "ratio", "SLOW", "命中目标", NONE);
                add(effects, skill, "slow_duration", "control_duration", RendingWaveSkill.SLOW_TICKS, "ticks", "SLOW_TICKS", "命中目标，名义时长未减韧性", REAL);
            }
            case UnreturningLeapSkill ignored -> {
                add(effects, skill, "range", "range", UnreturningLeapSkill.RANGE, "blocks", "RANGE", "选点可站立且跃进弧线无碰撞", NONE);
                add(effects, skill, "leap_height", "displacement", UnreturningLeapSkill.HEIGHT, "blocks", "HEIGHT", "弧线相对名义直线的最大高度", ACTION);
                add(effects, skill, "leap_duration", "timing", UnreturningLeapSkill.LEAP_TICKS, "ticks", "LEAP_TICKS", "合法弧线的名义时长", ACTION);
                add(effects, skill, "impact_radius", "range", UnreturningLeapSkill.RADIUS, "blocks", "RADIUS", "从施法者实际落点算", NONE);
                add(effects, skill, "movement_rate", "movement_multiplier", 1 - UnreturningLeapSkill.SLOW,
                        "ratio", "SLOW", "命中后剩余移速倍率", NONE);
                add(effects, skill, "slow_rate", "movement_slow", UnreturningLeapSkill.SLOW, "ratio", "SLOW", "成功落地后合法附近敌人", NONE);
                add(effects, skill, "slow_duration", "control_duration", UnreturningLeapSkill.SLOW_TICKS, "ticks", "SLOW_TICKS", "成功落地后合法附近敌人，名义时长未减韧性", REAL);
            }
            case WarBladeSkill ignored -> {
                add(effects, skill, "shield_duration", "shield_duration", WarBladeSkill.WINDOW_TICKS, "ticks", "WINDOW_TICKS", "开始横刀镇岳即授予", REAL);
                add(effects, skill, "bonus_window", "timing", WarBladeSkill.WINDOW_TICKS, "ticks", "WINDOW_TICKS", "开始横刀镇岳即允许强化攻击", REAL);
                add(effects, skill, "bonus_hits", "trigger", WarBladeSkill.BONUS_HITS, "hits", "BONUS_HITS", "本次施法内最多强化的有效普攻", NONE);
                add(effects, skill, "bonus_interval", "timing", WarBladeSkill.HIT_INTERVAL_TICKS, "ticks", "HIT_INTERVAL_TICKS", "相邻两次普攻附伤的最短时间", REAL);
            }
            default -> throw new IllegalArgumentException(skill.getId());
        }
    }

    private static void add(JsonArray effects, AbilitySkill skill, String id, String type, Object value,
                            String unit, String member, String condition, String clock) {
        effects.add(RosterSnapshot.effect(skill, id, type, field(value, unit, source(skill.getClass(), member),
                condition, "on_effect", clock)));
    }
}
