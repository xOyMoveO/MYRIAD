package com.myriad.game;

import static com.myriad.game.RosterSnapshot.effect;
import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.gap;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.skills.keheng.BeyondOneSecondSkill;
import com.myriad.character.skills.keheng.ConstantStepSkill;
import com.myriad.character.skills.keheng.DelayedBoundarySkill;
import com.myriad.character.skills.keheng.DelayedNeedleSkill;
import com.myriad.character.skills.keheng.EngravedEchoSkill;
import com.myriad.character.skills.keheng.FollowingSecondSkill;
import com.myriad.character.skills.keheng.KehengTimeFields;
import com.myriad.character.skills.keheng.OffbeatStepSkill;
import com.myriad.character.skills.luoxia.LastSun;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

final class SkillEffects {
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";
    private static final String NONE = "not_timed";
    private static final String FIELD_TARGETS = "在领域内且视线未被遮挡的敌方英雄、普通战场单位及独立敌方弹体，适用免疫检查";

    private SkillEffects() {}

    static String damageCondition(AbilitySkill skill, String id) {
        if (skill.role().equals("yike")) return YikeSkillEffects.damageCondition(skill);
        if (skill.role().equals("rivet")) return RivetSkillEffects.damageCondition(skill, id);
        if (skill.role().equals("shuo")) return ShuoSkillEffects.damageCondition(skill, id);
        if (skill.role().equals("chixiao")) return ChixiaoSkillEffects.damageCondition(skill, id);
        if (skill.role().equals("lingyuan") || skill.role().equals("hanze")) {
            return SwordFrostSkillEffects.damageCondition(skill, id);
        }
        if (skill.role().equals("xuanchen") || skill.role().equals("yunsheng")) {
            return XuanchenYunshengSkillEffects.damageCondition(skill, id);
        }
        return switch (skill) {
            case EngravedEchoSkill ignored -> "同一敌方英雄在窗口内受不同次主动直接技能有效命中，达到刻痕触发层数";
            case DelayedNeedleSkill ignored -> "起手完成，针体首次有效接触合法敌人";
            case DelayedBoundarySkill ignored -> "地面预警完成，开场时目标留在迟界中";
            case FollowingSecondSkill ignored -> "随行领域自然结束时目标仍在圈内，不能提前关闭或被硬控打断";
            case BeyondOneSecondSkill ignored -> "目标在本次脉冲结算时仍在穹层内，检查空间遮挡";
            case LastSun ignored -> "起手完成后发射，命中首个合法敌方单位";
            default -> "对应伤害段有效命中，需另核对实际命中、触发以及其他乘区";
        };
    }

    static String supportCondition(AbilitySkill skill) {
        if (skill.role().equals("yike")) return YikeSkillEffects.supportCondition(skill);
        if (skill.role().equals("rivet")) return RivetSkillEffects.supportCondition(skill);
        if (skill.role().equals("shuo")) return ShuoSkillEffects.supportCondition(skill);
        if (skill.role().equals("chixiao")) return ChixiaoSkillEffects.supportCondition(skill);
        if (skill.role().equals("lingyuan") || skill.role().equals("hanze")) {
            return SwordFrostSkillEffects.supportCondition(skill);
        }
        if (skill.role().equals("xuanchen") || skill.role().equals("yunsheng")) {
            return XuanchenYunshengSkillEffects.supportCondition(skill);
        }
        return switch (skill) {
            case ConstantStepSkill ignored -> "选择恒步分支，进入自己领域的瞬间且内置冷却已结束";
            case OffbeatStepSkill ignored -> "错拍步合法施放后给自身加盾";
            default -> "对应支援段触发并作用于合法友方，战斗场景需给出目标状态";
        };
    }

    static void add(JsonArray effects, AbilitySkill skill) {
        switch (skill.role()) {
            case "yike" -> YikeSkillEffects.add(effects, skill);
            case "rivet" -> RivetSkillEffects.add(effects, skill);
            case "shuo" -> ShuoSkillEffects.add(effects, skill);
            case "chixiao" -> ChixiaoSkillEffects.add(effects, skill);
            case "lingyuan", "hanze" -> SwordFrostSkillEffects.add(effects, skill);
            case "xuanchen", "yunsheng" -> XuanchenYunshengSkillEffects.add(effects, skill);
            default -> addStandard(effects, skill);
        }
    }

    private static void addStandard(JsonArray effects, AbilitySkill skill) {
        switch (skill) {
            case EngravedEchoSkill ignored -> {
                add(effects, skill, "marks_to_trigger", "trigger", EngravedEchoSkill.TRIGGER_STACKS,
                        "stack", "TRIGGER_STACKS", "同一敌方英雄，当前窗口内按每次施法去重", "on_effective_hit", NONE);
                add(effects, skill, "mark_window", "timing", EngravedEchoSkill.WINDOW_TICKS,
                        "ticks", "WINDOW_TICKS", "首个有效直接技能命中建立窗口", "on_first_mark", REAL);
                inapplicable(effects, skill, "control", "仅叠刻痕并造成伤害");
                inapplicable(effects, skill, "heal", "该被动无治疗");
                inapplicable(effects, skill, "shield", "该被动无护盾");
            }
            case ConstantStepSkill ignored -> {
                add(effects, skill, "internal_cooldown", "timing", ConstantStepSkill.COOLDOWN_TICKS,
                        "ticks", "COOLDOWN_TICKS", "自己领域入场时护盾触发", "on_shield_grant", REAL);
                add(effects, skill, "shield_duration", "shield_duration", ConstantStepSkill.SHIELD_TICKS,
                        "ticks", "SHIELD_TICKS", "自身护盾", "on_shield_grant", REAL);
                inapplicable(effects, skill, "control", "入场只给自身护盾，控制来自领域本身所归属技能");
                inapplicable(effects, skill, "damage", "该被动无伤害");
                inapplicable(effects, skill, "heal", "该被动无治疗");
            }
            case DelayedNeedleSkill ignored -> {
                add(effects, skill, "release_windup", "windup", DelayedNeedleSkill.WINDUP_TICKS, "ticks",
                        "WINDUP_TICKS", "已扣蓝开始冷却，延迟出针", "after_cast_commit", ACTION);
                add(effects, skill, "needle_range", "range", DelayedNeedleSkill.RANGE, "blocks",
                        "RANGE", "撞击首个合法敌方单位或地形可提前截断", "on_projectile_launch", NONE);
                add(effects, skill, "target_action_rate", "action_slow", rankValues(skill, DelayedNeedleSkill::actionRate),
                        "ratio", "actionRate", "命中后实际造成生命或盾损失，且目标可受缓时", "on_effective_hit", ACTION);
                add(effects, skill, "target_action_slow_duration", "control_duration", field(KehengTimeFields.NEEDLE_TICKS,
                        "ticks", source(KehengTimeFields.class, "NEEDLE_TICKS"),
                        "有效命中且目标可受缓时，基础时长取韧性折减前", "on_effective_hit", REAL));
                inapplicable(effects, skill, "heal", "迟针无治疗");
                inapplicable(effects, skill, "shield", "迟针无护盾");
            }
            case OffbeatStepSkill ignored -> {
                add(effects, skill, "field_actor_and_flight_rate", "action_slow", OffbeatStepSkill.FIELD_RATE,
                        "ratio", "FIELD_RATE", FIELD_TARGETS, "inside_field", ACTION);
                add(effects, skill, "field_duration", "control_duration", OffbeatStepSkill.FIELD_TICKS,
                        "ticks", "FIELD_TICKS", "领域自起脚点展开", "on_cast_commit", REAL);
                add(effects, skill, "field_radius", "range", OffbeatStepSkill.FIELD_RADIUS,
                        "blocks", "FIELD_RADIUS", FIELD_TARGETS, "on_cast_commit", NONE);
                add(effects, skill, "dash_range", "displacement", OffbeatStepSkill.DASH_RANGE,
                        "blocks", "DASH_RANGE", "未被禁锢，采用移动输入否则朝向；地形可截断", "on_cast_commit", NONE);
                add(effects, skill, "dash_duration", "displacement_duration", OffbeatStepSkill.DASH_TICKS,
                        "ticks", "DASH_TICKS", "未被禁锢且未被障碍阻止", "on_cast_commit", ACTION);
                add(effects, skill, "shield_duration", "shield_duration", OffbeatStepSkill.SHIELD_TICKS,
                        "ticks", "SHIELD_TICKS", "合法施放给自身盾", "on_shield_grant", REAL);
                inapplicable(effects, skill, "damage", "位移、原地缓时和自身护盾本身无伤害");
                inapplicable(effects, skill, "heal", "错拍步无治疗");
            }
            case DelayedBoundarySkill ignored -> {
                fieldEffects(effects, skill, DelayedBoundarySkill.RANGE, DelayedBoundarySkill.RADIUS,
                        DelayedBoundarySkill.WARNING_TICKS);
                add(effects, skill, "field_duration", "control_duration", rankValues(skill, DelayedBoundarySkill::durationTicks),
                        "ticks", "durationTicks", "领域从预警结束展开至到期", "on_field_open", REAL);
                add(effects, skill, "field_actor_and_flight_rate", "action_slow", rankValues(skill, DelayedBoundarySkill::actionRate),
                        "ratio", "actionRate", FIELD_TARGETS, "inside_field", ACTION);
                inapplicable(effects, skill, "heal", "迟界无治疗");
                inapplicable(effects, skill, "shield", "迟界无护盾");
            }
            case FollowingSecondSkill ignored -> {
                add(effects, skill, "field_duration", "control_duration", rankValues(skill, FollowingSecondSkill::durationTicks),
                        "ticks", "durationTicks", "不提前取消且未被打断的最长持续", "on_field_open", REAL);
                add(effects, skill, "field_actor_and_flight_rate", "action_slow", rankValues(skill, FollowingSecondSkill::actionRate),
                        "ratio", "actionRate", FIELD_TARGETS, "inside_field", ACTION);
                add(effects, skill, "release_windup", "windup", FollowingSecondSkill.WINDUP_TICKS,
                        "ticks", "WINDUP_TICKS", "扣蓝并开始冷却后才进入展开起手", "after_cast_commit", ACTION);
                add(effects, skill, "field_radius", "range", field(KehengTimeFields.FOLLOW_RADIUS, "blocks",
                        source(KehengTimeFields.class, "FOLLOW_RADIUS"), "领域随自身移动", "inside_field", NONE));
                inapplicable(effects, skill, "heal", "随行秒域无治疗");
                inapplicable(effects, skill, "shield", "该技能本身无护盾，进入领域可另触发恒步分支");
            }
            case BeyondOneSecondSkill ignored -> {
                fieldEffects(effects, skill, BeyondOneSecondSkill.RANGE, BeyondOneSecondSkill.RADIUS,
                        BeyondOneSecondSkill.WARNING_TICKS);
                add(effects, skill, "field_duration", "control_duration", BeyondOneSecondSkill.DURATION_TICKS,
                        "ticks", "DURATION_TICKS", "领域从预警结束展开至到期", "on_field_open", REAL);
                add(effects, skill, "field_actor_rate", "action_slow", rankValues(skill, BeyondOneSecondSkill::actionRate),
                        "ratio", "actionRate", FIELD_TARGETS + "中的单位", "inside_field", ACTION);
                add(effects, skill, "field_flight_rate", "projectile_slow", rankValues(skill, BeyondOneSecondSkill::flightRate),
                        "ratio", "flightRate", FIELD_TARGETS + "中的弹体", "inside_field", ACTION);
                add(effects, skill, "damage_interval", "timing", field(KehengTimeFields.FIELD_PULSE_TICKS,
                        "ticks", source(KehengTimeFields.class, "FIELD_PULSE_TICKS"),
                        "开启后首轮间隔至到期时最后一跳", "on_field_open", REAL));
                inapplicable(effects, skill, "heal", "一秒之外无治疗");
                inapplicable(effects, skill, "shield", "一秒之外无护盾");
            }
            case LastSun ignored -> markDamageOnly(effects, skill, "重箭只有直接伤害，命中可受到通用效果和其他技能修饰");
            default -> documentUnmapped(effects, skill);
        }
    }

    private static void documentUnmapped(JsonArray effects, AbilitySkill skill) {
        if (skill.damageFormulas().isEmpty()) {
            missing(effects, skill, "damage", "hp_before_mitigation", "未接入伤害段或条件增伤；不可据此判定该技能无伤害");
        } else {
            missing(effects, skill, "damage_sequence", "event", "公式可单独估值；形态、伤害段能否相加及实际命中节拍需用场景与引擎读数确认");
        }
        var supportTypes = skill.supportAmounts().stream().map(support -> support.effect().id()).toList();
        if (!supportTypes.contains("heal")) {
            missing(effects, skill, "heal", "hp", "无已接入的治疗声明；尚未确认动态或间接治疗是否适用");
        }
        if (!supportTypes.contains("shield")) {
            missing(effects, skill, "shield", "hp", "无已接入的护盾声明；尚未确认动态或间接护盾是否适用");
        }
        if (!supportTypes.isEmpty()) {
            missing(effects, skill, "support_conditions", "event", "名义盾疗量已接入；触发、时长及有效盾疗还需事件与目标状态");
        }
        missing(effects, skill, "control", "effect", "独立持续、目标筛选、韧性、位移或形态依赖等控制输入待逐项接入");
        missing(effects, skill, "geometry", "blocks", "服务端距离／地形／碰撞尚未导出成可复算的效果条件");
    }

    private static void markDamageOnly(JsonArray effects, AbilitySkill skill, String reason) {
        for (String type : List.of("heal", "shield", "control")) inapplicable(effects, skill, type, reason);
    }

    private static void fieldEffects(JsonArray effects, AbilitySkill skill, double range, double radius, int warning) {
        add(effects, skill, "target_range", "range", range, "blocks", "RANGE", "合法地面选点", "on_cast_request", NONE);
        add(effects, skill, "field_radius", "range", radius, "blocks", "RADIUS", FIELD_TARGETS, "on_field_open", NONE);
        add(effects, skill, "field_warning", "timing", warning, "ticks", "WARNING_TICKS",
                "成功设下地面预警后由场地独立推进", "on_cast_commit", REAL);
    }

    private static <T> List<T> rankValues(AbilitySkill skill, IntFunction<T> values) {
        return IntStream.rangeClosed(1, skill.maxRank()).mapToObj(values).toList();
    }

    private static void add(JsonArray effects, AbilitySkill skill, String id, String type, Object value,
            String unit, String member, String condition, String timing, String clock) {
        add(effects, skill, id, type, field(value, unit, source(skill.getClass(), member), condition, timing, clock));
    }

    private static void add(JsonArray effects, AbilitySkill skill, String id, String type, JsonObject value) {
        effects.add(effect(skill, id, type, value));
    }

    private static void inapplicable(JsonArray effects, AbilitySkill skill, String type, String reason) {
        add(effects, skill, type, type, gap("not_applicable", "none", source(skill.getClass(), "release"),
                "该技能自身", "on_effect", NONE, reason));
    }

    private static void missing(JsonArray effects, AbilitySkill skill, String type, String unit, String reason) {
        add(effects, skill, type, type, gap("missing_input", unit, source(skill.getClass(), "release"),
                "对应技能与场景", "on_effect", NONE, reason));
    }
}
