package com.myriad.game;

import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.skills.sulan.rework.GaleThrustSkill;
import com.myriad.character.skills.sulan.rework.ReturningWindBladeSkill;
import com.myriad.character.skills.sulan.rework.SpiritWindMarkSkill;
import com.myriad.character.skills.sulan.rework.ThousandWindDashSkill;
import com.myriad.character.skills.sulan.rework.WindBarrierSkill;
import com.myriad.character.skills.sulan.rework.WindComboBladeSkill;
import com.myriad.character.skills.sulan.rework.WindDomainSkill;
import com.myriad.character.skills.sulan.rework.WindWalkingBladeSkill;
import com.myriad.combat.HeroProjectileTuning;
import java.util.stream.IntStream;

final class SulanSkillEffects {
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";
    private static final String NONE = "not_timed";

    private SulanSkillEffects() {}

    static String damageCondition(AbilitySkill skill, String id) {
        return switch (skill) {
            case WindWalkingBladeSkill ignored -> "未计算技能位移、传送或击退的自然水平移动达阈值后，一次有效普攻附伤";
            case WindComboBladeSkill ignored -> "主动直接命中敌方英雄后，在有效窗口内的下一次普攻";
            case GaleThrustSkill ignored -> "冲刺实际路径扫掠，每名合法且无遮挡目标一次";
            case SpiritWindMarkSkill ignored -> "锁定视线内的首个合法敌人，结算当前击后才施加标记";
            case ReturningWindBladeSkill ignored -> "实际飞行扫掠，每名目标去程、回程分别一次，墙可阻挡";
            case ThousandWindDashSkill ignored -> "完成蓄势并在合法冲刺终点或接触首名英雄时，终点附近伤害一次";
            case WindDomainSkill ignored -> "展开时靠近自身、视线通畅的敌人各一次";
            default -> throw new IllegalArgumentException(skill.getId());
        };
    }

    static void add(JsonArray effects, AbilitySkill skill) {
        switch (skill) {
            case WindWalkingBladeSkill ignored -> {
                add(effects, skill, "charge_distance", "trigger", WindWalkingBladeSkill.CHARGE_DISTANCE,
                        "blocks", "CHARGE_DISTANCE", "累计认可的落地水平位移", NONE);
                add(effects, skill, "step_limit", "threshold", WindWalkingBladeSkill.STEP_LIMIT,
                        "blocks", "STEP_LIMIT", "每次自然移动采样的最高位移量", REAL);
            }
            case WindComboBladeSkill ignored -> {
                add(effects, skill, "ready_window", "timing", WindComboBladeSkill.READY_TICKS,
                        "ticks", "READY_TICKS", "直接技能命中英雄才开启", REAL);
                add(effects, skill, "internal_cooldown", "timing", WindComboBladeSkill.COOLDOWN_TICKS,
                        "ticks", "COOLDOWN_TICKS", "从合格主动命中计", REAL);
                add(effects, skill, "slow_rate", "movement_slow", WindComboBladeSkill.SLOW_RATE,
                        "ratio", "SLOW_RATE", "强化普攻命中", NONE);
                add(effects, skill, "slow_duration", "control_duration", WindComboBladeSkill.SLOW_TICKS,
                        "ticks", "SLOW_TICKS", "强化普攻命中", REAL);
            }
            case GaleThrustSkill ignored -> {
                add(effects, skill, "dash_range", "displacement", GaleThrustSkill.RANGE,
                        "blocks", "RANGE", "无阻且朝向固定时的名义位移", ACTION);
                add(effects, skill, "dash_duration", "timing", GaleThrustSkill.DASH_TICKS,
                        "ticks", "DASH_TICKS", "无阻时的名义位移时长", ACTION);
                add(effects, skill, "path_radius", "range", GaleThrustSkill.PATH_RADIUS,
                        "blocks", "PATH_RADIUS", "碰撞还检查实体尺寸与视线", NONE);
            }
            case SpiritWindMarkSkill ignored -> {
                add(effects, skill, "range", "range", SpiritWindMarkSkill.RANGE,
                        "blocks", "RANGE", "视线、可见性与视角均需合法", NONE);
                add(effects, skill, "mark_window", "timing", SpiritWindMarkSkill.MARK_TICKS,
                        "ticks", "MARK_TICKS", "命中后施加，自己下一次有效直接非真实伤害消耗", REAL);
                add(effects, skill, "slow_rate", "movement_slow", SpiritWindMarkSkill.SLOW_RATE,
                        "ratio", "SLOW_RATE", "本次直接命中", NONE);
                add(effects, skill, "movement_rate", "movement_multiplier", 1 - SpiritWindMarkSkill.SLOW_RATE,
                        "ratio", "SLOW_RATE", "当前状态的剩余移速倍率", NONE);
                add(effects, skill, "slow_duration", "control_duration", SpiritWindMarkSkill.SLOW_TICKS,
                        "ticks", "SLOW_TICKS", "本次直接命中", REAL);
                add(effects, skill, "mark_multiplier", "damage_multiplier", IntStream.rangeClosed(1, skill.maxRank()).mapToObj(SpiritWindMarkSkill::damageMultiplier).toList(),
                        "ratio", "damageMultiplier", "按技能等级列出；只消耗当前目标的标记", NONE);
            }
            case WindBarrierSkill ignored -> {
                add(effects, skill, "wall_duration", "timing", IntStream.rangeClosed(1, skill.maxRank()).mapToObj(WindBarrierSkill::wallTicks).toList(),
                        "ticks", "wallTicks", "按技能等级列出；仅拦截合法敌方投射物", REAL);
            }
            case ReturningWindBladeSkill ignored -> {
                add(effects, skill, "range", "range", ReturningWindBladeSkill.RANGE,
                        "blocks", "RANGE", "无阻时去程最大距离", ACTION);
                add(effects, skill, "projectile_speed", "projectile_speed", ReturningWindBladeSkill.PROJECTILE_SPEED * HeroProjectileTuning.SPEED,
                        "blocks/tick", "PROJECTILE_SPEED", "共享倍率已计入；飞行另受局部缓时", ACTION);
                add(effects, skill, "projectile_radius", "range", ReturningWindBladeSkill.PROJECTILE_RADIUS * HeroProjectileTuning.ENTITY_PADDING,
                        "blocks", "PROJECTILE_RADIUS", "共享碰撞扩张倍率已计入", NONE);
            }
            case ThousandWindDashSkill ignored -> {
                add(effects, skill, "windup", "windup", ThousandWindDashSkill.WINDUP_TICKS,
                        "ticks", "WINDUP_TICKS", "提交后，按施法者行动进度", ACTION);
                add(effects, skill, "dash_range", "displacement", ThousandWindDashSkill.RANGE,
                        "blocks", "RANGE", "未遭碰撞或敌方英雄时的最大位移", ACTION);
                add(effects, skill, "dash_duration", "timing", ThousandWindDashSkill.DASH_TICKS,
                        "ticks", "DASH_TICKS", "未遭碰撞或敌方英雄时的名义位移时长", ACTION);
                add(effects, skill, "path_radius", "range", ThousandWindDashSkill.PATH_RADIUS,
                        "blocks", "PATH_RADIUS", "扫掠接触英雄可使冲刺提前结束", NONE);
                add(effects, skill, "impact_radius", "range", ThousandWindDashSkill.IMPACT_RADIUS,
                        "blocks", "IMPACT_RADIUS", "从实际冲刺终点算", NONE);
                add(effects, skill, "slow_rate", "movement_slow", ThousandWindDashSkill.SLOW_RATE,
                        "ratio", "SLOW_RATE", "实际终点附近的合法敌人", NONE);
                add(effects, skill, "movement_rate", "movement_multiplier", 1 - ThousandWindDashSkill.SLOW_RATE,
                        "ratio", "SLOW_RATE", "当前状态的剩余移速倍率", NONE);
                add(effects, skill, "slow_duration", "control_duration", ThousandWindDashSkill.SLOW_TICKS,
                        "ticks", "SLOW_TICKS", "实际终点附近的合法敌人", REAL);
            }
            case WindDomainSkill ignored -> {
                add(effects, skill, "radius", "range", WindDomainSkill.RADIUS,
                        "blocks", "RADIUS", "持续随施放者移动", NONE);
                add(effects, skill, "field_duration", "timing", WindDomainSkill.FIELD_TICKS,
                        "ticks", "FIELD_TICKS", "从展开计，结束刻含最后一轮刷新", REAL);
                add(effects, skill, "refresh_interval", "timing", WindDomainSkill.REFRESH_TICKS,
                        "ticks", "REFRESH_TICKS", "在领域内才续状态", REAL);
                add(effects, skill, "status_duration", "control_duration", WindDomainSkill.STATUS_TICKS,
                        "ticks", "STATUS_TICKS", "末次刷新后各自到期", REAL);
                add(effects, skill, "slow_rate", "movement_slow", WindDomainSkill.SLOW_RATE,
                        "ratio", "SLOW_RATE", "合法敌人", NONE);
                add(effects, skill, "haste_rate", "movement_haste", WindDomainSkill.HASTE_RATE,
                        "ratio", "HASTE_RATE", "合法友方英雄", NONE);
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
