package com.myriad.game;

import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.skills.tianxing.rework.NineThunderSkill;
import com.myriad.character.skills.tianxing.rework.ThunderArcSkill;
import com.myriad.character.skills.tianxing.rework.ThunderBlinkSkill;
import com.myriad.character.skills.tianxing.rework.ThunderGuardSkill;
import com.myriad.character.skills.tianxing.rework.ThunderJudgmentSkill;
import com.myriad.character.skills.tianxing.rework.ThunderResonanceSkill;
import com.myriad.character.skills.tianxing.rework.ThunderSlashSkill;
import com.myriad.character.skills.tianxing.rework.ThunderThirdStrikeSkill;
import com.myriad.combat.HeroProjectileTuning;

final class TianxingSkillEffects {
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";
    private static final String NONE = "not_timed";

    private TianxingSkillEffects() {}

    static String damageCondition(AbilitySkill skill, String id) {
        return switch (skill) {
            case ThunderThirdStrikeSkill ignored -> id.endsWith(".splash")
                    ? "同一目标第三次有效普攻后，向以主目标为中心的其他合法敌人溅射，按实际目标查询顺序取前两名"
                    : "同一目标连续有效普攻达到第三击；换目标、上次有效普攻间隔超过允许窗口时从第一击重新计数";
            case ThunderResonanceSkill ignored -> id.endsWith(".splash")
                    ? "消耗强化普攻后对其目标周围其他合法敌人溅射，由查询顺序决定两名"
                    : "主动技能直接有效命中敌方英雄，内置冷却可用，并在限时内以一次有效普攻消耗";
            case ThunderBlinkSkill ignored -> "合法地面完成闪烁后，实际落点周围合法且可见的敌人各一次";
            case ThunderArcSkill ignored -> id.endsWith(".chain")
                    ? "首个碰撞目标与施法者均可见的附近其他敌人，各结算一次派生伤害"
                    : "行动刻起手后电弧有效碰撞到的首个合法敌人";
            case ThunderGuardSkill ignored -> "每次脉冲结算时仍在施法者身周并满足视线的合法敌人";
            case ThunderSlashSkill ignored -> "行动刻起手后，剑气有效扫掠到每个合法敌人一次";
            case NineThunderSkill ignored -> "每次真实刻落雷时仍在所选落点范围内且满足视线的合法敌人";
            case ThunderJudgmentSkill ignored -> "行动刻起手结束，以结算时朝向为准，在扇面内无遮挡的合法敌人";
            default -> throw new IllegalArgumentException(skill.getId());
        };
    }

    static void add(JsonArray effects, AbilitySkill skill) {
        switch (skill) {
            case ThunderThirdStrikeSkill ignored -> {
                add(effects, skill, "hit_count", "trigger", ThunderThirdStrikeSkill.REQUIRED_HITS, "hits", "REQUIRED_HITS", "连续计入同一目标的有效普攻", NONE);
                add(effects, skill, "hit_window", "timing", ThunderThirdStrikeSkill.HIT_WINDOW, "ticks", "HIT_WINDOW", "两次有效普攻最大间隔，恰好等于窗口仍有效", REAL);
                add(effects, skill, "splash_radius", "range", ThunderThirdStrikeSkill.SPLASH_RADIUS, "blocks", "SPLASH_RADIUS", "从主目标位置查找其他合法敌人", NONE);
                add(effects, skill, "splash_limit", "trigger", ThunderThirdStrikeSkill.SPLASH_LIMIT, "targets", "SPLASH_LIMIT", "单次触发的额外合法敌人上限", NONE);
            }
            case ThunderResonanceSkill ignored -> {
                add(effects, skill, "ready_window", "timing", ThunderResonanceSkill.READY_WINDOW, "ticks", "READY_WINDOW", "直接技能命中英雄起至强化普攻到期，到期刻不能消耗", REAL);
                add(effects, skill, "internal_cooldown", "timing", ThunderResonanceSkill.INTERNAL_COOLDOWN, "ticks", "INTERNAL_COOLDOWN", "直接技能命中英雄开始计时", REAL);
                add(effects, skill, "splash_radius", "range", ThunderResonanceSkill.SPLASH_RADIUS, "blocks", "SPLASH_RADIUS", "从强化普攻的主目标位置查找其他合法敌人", NONE);
                add(effects, skill, "splash_limit", "trigger", ThunderResonanceSkill.SPLASH_LIMIT, "targets", "SPLASH_LIMIT", "单次消耗的额外合法敌人上限", NONE);
            }
            case ThunderBlinkSkill ignored -> {
                add(effects, skill, "range", "range", ThunderBlinkSkill.HORIZONTAL, "blocks", "HORIZONTAL", "水平最大距离，需要可见且有支撑的落点", NONE);
                add(effects, skill, "maximum_rise", "range", ThunderBlinkSkill.RISE, "blocks", "RISE", "最大垂直上升距离", NONE);
                add(effects, skill, "maximum_drop", "range", ThunderBlinkSkill.DROP, "blocks", "DROP", "最大垂直下降距离", NONE);
                add(effects, skill, "radius", "range", ThunderBlinkSkill.IMPACT_RADIUS, "blocks", "IMPACT_RADIUS", "实际闪烁落点至敌人距离", NONE);
            }
            case ThunderArcSkill ignored -> {
                projectile(effects, skill, ThunderArcSkill.RANGE, ThunderArcSkill.SPEED, ThunderArcSkill.RADIUS, ThunderArcSkill.WINDUP);
                add(effects, skill, "chain_radius", "range", ThunderArcSkill.CHAIN_RADIUS, "blocks", "CHAIN_RADIUS", "首个碰撞目标周围，并检查施法者和碰撞目标各自的视线", NONE);
                add(effects, skill, "movement_rate", "movement_multiplier", 1 - ThunderArcSkill.SLOW_RATE, "ratio", "SLOW_RATE", "首个碰撞目标剩余移动倍率", NONE);
                add(effects, skill, "slow_duration", "control_duration", ThunderArcSkill.SLOW_TICKS, "ticks", "SLOW_TICKS", "首个碰撞目标名义减速时长", REAL);
            }
            case ThunderGuardSkill ignored -> {
                add(effects, skill, "guard_duration", "timing", ThunderGuardSkill.GUARD_TICKS, "ticks", "GUARD_TICKS", "施放后施法者携带雷环和非真伤减伤", REAL);
                add(effects, skill, "pulse_interval", "timing", ThunderGuardSkill.PULSE_INTERVAL, "ticks", "PULSE_INTERVAL", "施放后首次与后续脉冲间隔", REAL);
                add(effects, skill, "radius", "range", ThunderGuardSkill.PULSE_RADIUS, "blocks", "PULSE_RADIUS", "每次结算时以施法者为中心", NONE);
                effects.add(RosterSnapshot.effect(skill, "damage_taken_rate", "damage_multiplier",
                        field(AbilityRuntime.GUARD_15_DAMAGE_TAKEN, "ratio",
                                source(AbilityRuntime.class, "GUARD_15_DAMAGE_TAKEN"), "雷环期间物理与魔法来伤", "on_effect", NONE)));
            }
            case ThunderSlashSkill ignored -> {
                projectile(effects, skill, ThunderSlashSkill.RANGE, ThunderSlashSkill.SPEED, ThunderSlashSkill.RADIUS, ThunderSlashSkill.WINDUP);
                add(effects, skill, "root_duration", "control_duration", ThunderSlashSkill.ROOT_TICKS, "ticks", "ROOT_TICKS", "剑气接触的首个合法敌方英雄", REAL);
            }
            case NineThunderSkill ignored -> {
                add(effects, skill, "range", "range", NineThunderSkill.RANGE, "blocks", "RANGE", "合法所选落点距离", NONE);
                add(effects, skill, "radius", "range", NineThunderSkill.RADIUS, "blocks", "RADIUS", "以固定落点为中心判定每次落雷", NONE);
                add(effects, skill, "warning", "timing", NineThunderSkill.WARNING_TICKS, "ticks", "WARNING_TICKS", "施放至首击真实刻", REAL);
                add(effects, skill, "strike_interval", "timing", NineThunderSkill.STRIKE_INTERVAL, "ticks", "STRIKE_INTERVAL", "两次落雷的真实刻间隔", REAL);
                add(effects, skill, "strikes", "trigger", NineThunderSkill.STRIKES, "hits", "STRIKES", "单次施放的落雷次数", NONE);
                add(effects, skill, "root_duration", "control_duration", NineThunderSkill.ROOT_TICKS, "ticks", "ROOT_TICKS", "最后一击结算范围内的合法敌人", REAL);
            }
            case ThunderJudgmentSkill ignored -> {
                add(effects, skill, "windup", "timing", ThunderJudgmentSkill.WINDUP, "ticks", "WINDUP", "起手期间可转向且可被打断", ACTION);
                add(effects, skill, "range", "range", ThunderJudgmentSkill.RANGE, "blocks", "RANGE", "结算时扇面半径", NONE);
                add(effects, skill, "angle", "angle", ThunderJudgmentSkill.ANGLE, "degrees", "ANGLE", "结算时扇面全角", NONE);
                add(effects, skill, "stun_duration", "control_duration", ThunderJudgmentSkill.STUN, "ticks", "STUN", "结算时扇面内的合法敌人", REAL);
            }
            default -> throw new IllegalArgumentException(skill.getId());
        }
    }

    private static void projectile(JsonArray effects, AbilitySkill skill, double range, double speed, double radius, int windup) {
        add(effects, skill, "range", "range", range, "blocks", "RANGE", "名义射程，墙体或风墙可提前终止", ACTION);
        add(effects, skill, "windup", "timing", windup, "ticks", "WINDUP", "起手期间可转向且可被打断", ACTION);
        add(effects, skill, "projectile_speed", "projectile_speed", speed * HeroProjectileTuning.SPEED,
                "blocks/tick", "release", "包含共享弹道速度倍率，未受局部缓时", ACTION);
        add(effects, skill, "projectile_radius", "range", radius * HeroProjectileTuning.ENTITY_PADDING,
                "blocks", "release", "包含共享实体碰撞倍率", NONE);
    }

    private static void add(JsonArray effects, AbilitySkill skill, String id, String type, Object value,
                            String unit, String member, String condition, String clock) {
        effects.add(RosterSnapshot.effect(skill, id, type,
                field(value, unit, source(skill.getClass(), member), condition, "on_effect", clock)));
    }
}
