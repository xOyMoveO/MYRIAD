package com.myriad.game;

import static com.myriad.game.RosterSnapshot.effect;
import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.gap;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.skills.xuanchen.rework.*;
import com.myriad.character.skills.yunsheng.rework.*;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

final class XuanchenYunshengSkillEffects {
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";
    private static final String NONE = "not_timed";

    private XuanchenYunshengSkillEffects() {}

    static String damageCondition(AbilitySkill skill, String id) {
        return switch (skill) {
            case SealResonanceSkill ignored -> "同一敌方英雄处于标记窗口且受不同技能 ID 的有效直接主动命中，目标独立冷却已结束";
            case SpiritBindSkill ignored -> "锁链首个合法敌方碰撞体；墙体及敌方风墙可拦截";
            case HeavenCoverArraySkill ignored -> "引导完成后固定法阵范围内、施法者有视线，逐次真实刻脉冲；为派生伤害";
            case SealedBoundarySkill ignored -> "预警结束时位于目标区域内且施法者有视线，开场一次";
            case PaperCraneSkill ignored -> "敌方锁定纸鹤完成弧线飞行且目标合法、未超距、未被地形或风墙阻挡";
            case SpiritTetherSkill ignored -> "命中后首次定身；第二段要求连续存活、同世界、距离与视线有效";
            case EchoBeaconSkill ignored -> "每个合法敌方单位在区域内的首次轮询，后续离开再入也不重复伤害";
            case ConvergingChorusSkill ignored -> "引导完成后位于目标区域内且施法者有视线的敌方单位，逐次脉冲；为派生伤害";
            default -> throw new IllegalArgumentException(skill.getId() + "." + id);
        };
    }

    static String supportCondition(AbilitySkill skill) {
        return switch (skill) {
            case ArrayGuardianSkill ignored -> "主动扣费并提交时；可见存活友军中排除自己取最低生命比例，无合格友军时给自己";
            case InkShieldSkill ignored -> "准星范围内可见合法队友，否则施法者；同源刷新保留较高的剩余盾";
            case InkProtectionArraySkill ignored -> "开阵瞬间且可见、存活的范围内友军；后续驻留只刷新减伤";
            case SpiritEchoSkill ignored -> "同一目标短时间内实际得到两次主动技能有效治疗或新增盾，有最小间隔与独立冷却";
            case SpiritChimeSkill ignored -> "直接有效命中敌方英雄且内置冷却可用；治疗施法者与可见邻近友军";
            case PaperCraneSkill ignored -> "友方选路或无目标回到施法者；纸鹤到达并且受援者当前存在缺血";
            case CloudEscortSkill ignored -> "合法落点提交后按实际运动停止位置判定，要求可见友军在范围内";
            case EchoBeaconSkill ignored -> "界标每次护盾轮询时在区域内且施法者有视线；同源剩余量不低于名义盾时只刷新";
            case ConvergingChorusSkill ignored -> "引导完成后的逐次脉冲，在区域内且施法者有视线，治疗按实际缺血";
            case CovenantShelterSkill ignored -> "主动提交后立即给施法者与可见邻近友军授盾";
            default -> throw new IllegalArgumentException(skill.getId());
        };
    }

    static void add(JsonArray effects, AbilitySkill skill) {
        switch (skill) {
            case ArrayGuardianSkill ignored -> {
                range(effects, skill, "range", ArrayGuardianSkill.ALLY_RANGE, "ALLY_RANGE", "以施法者为中心选择最低生命比例队友");
                ticks(effects, skill, "internal_cooldown", ArrayGuardianSkill.INTERNAL_COOLDOWN, "INTERNAL_COOLDOWN", "主动提交触发时开始");
                ticks(effects, skill, "shield_duration", ArrayGuardianSkill.SHIELD_TICKS, "SHIELD_TICKS", "授予护盾时开始");
            }
            case SealResonanceSkill ignored -> {
                ticks(effects, skill, "mark_window", SealResonanceSkill.MARK_TICKS, "MARK_TICKS", "首个有效直接主动命中留下标记");
                ticks(effects, skill, "internal_cooldown", SealResonanceSkill.INTERNAL_COOLDOWN, "INTERNAL_COOLDOWN", "异技能 ID 的第二击消印后开始");
            }
            case SpiritBindSkill ignored -> {
                range(effects, skill, "range", SpiritBindSkill.RANGE, "RANGE", "首个合法敌人碰撞或地形可提前截断");
                range(effects, skill, "projectile_padding", SpiritBindSkill.entityPaddingBlocks(), "entityPaddingBlocks", "按通用英雄弹道倍率换算的实体扫掠扩张");
                add(effects, skill, "flight_speed", "projectile_speed", SpiritBindSkill.speedBlocksPerTick(), "blocks_per_tick",
                        "speedBlocksPerTick", "按通用投射物倍率换算，弹道未受缓时", ACTION);
                add(effects, skill, "root_duration", "control_duration", rankValues(skill, SpiritBindSkill::rootTicks),
                        "ticks", "rootTicks", "首个实体接触；韧性折减前", REAL);
            }
            case InkShieldSkill ignored -> {
                range(effects, skill, "range", InkShieldSkill.ALLY_RANGE, "ALLY_RANGE", "瞄准可见友军，未选中时可给自己");
                ticks(effects, skill, "shield_duration", InkShieldSkill.SHIELD_TICKS, "SHIELD_TICKS", "护符提交后立即授予");
            }
            case PairedPortalSkill ignored -> {
                add(effects, skill, "range", "range", rankValues(skill, PairedPortalSkill::range), "blocks", "range",
                        "需要两处可站立、合法端点", NONE);
                add(effects, skill, "portal_duration", "timing", rankValues(skill, PairedPortalSkill::durationTicks), "ticks",
                        "durationTicks", "一次仅一对阵点，友军主动下蹲且可站立时传送", REAL);
                effects.add(effect(skill, "portal_reuse", "timing", field(AbilityRuntime.PORTAL_TRAVEL_REUSE_TICKS,
                        "ticks", source(AbilityRuntime.class, "PORTAL_TRAVEL_REUSE_TICKS"),
                        "同一位传送者的最短复用间隔", "on_portal_travel", REAL)));
                effects.add(effect(skill, "portal_path_benefit", "displacement", gap("missing_input", "hp",
                        source(AbilityRuntime.class, "Cast.portal"), "需要站位、地形、下蹲输入和对照路线",
                        "on_portal_travel", NONE, "传送本身无伤害、盾疗或硬控；额外有效命中及避免掉血依赖双方路线")));
            }
            case InkProtectionArraySkill ignored -> {
                range(effects, skill, "range", InkProtectionArraySkill.RANGE, "RANGE", "地面选点");
                range(effects, skill, "radius", InkProtectionArraySkill.RADIUS, "RADIUS", "以选点为中心并过滤视线");
                ticks(effects, skill, "warning", InkProtectionArraySkill.WARNING_TICKS, "WARNING_TICKS", "提交后地面预警");
                ticks(effects, skill, "field_duration", InkProtectionArraySkill.FIELD_TICKS, "FIELD_TICKS", "从展开起算");
                ticks(effects, skill, "shield_duration", InkProtectionArraySkill.SHIELD_TICKS, "SHIELD_TICKS", "开阵一次性授盾");
                ticks(effects, skill, "refresh_interval", InkProtectionArraySkill.REFRESH_TICKS, "REFRESH_TICKS", "开阵后按驻留读取内外目标");
                ticks(effects, skill, "guard_duration", InkProtectionArraySkill.GUARD_TICKS, "GUARD_TICKS", "每轮驻留刷新，离场后余时按最近刷新计算");
                guard(effects, skill, AbilityRuntime.GUARD_12_DAMAGE_TAKEN, "GUARD_12_DAMAGE_TAKEN");
                slow(effects, skill, InkProtectionArraySkill.SLOW, InkProtectionArraySkill.SLOW_TICKS);
            }
            case HeavenCoverArraySkill ignored -> {
                range(effects, skill, "radius", HeavenCoverArraySkill.RADIUS, "RADIUS", "从引导完成时施法者位置固定展开");
                add(effects, skill, "windup_duration", "windup", HeavenCoverArraySkill.WINDUP_TICKS, "ticks", "WINDUP_TICKS",
                        "扣蓝进入冷却后引导，控制可打断，局部行动速率影响用时", ACTION);
                ticks(effects, skill, "field_duration", HeavenCoverArraySkill.FIELD_TICKS, "FIELD_TICKS", "成功展开后运行");
                ticks(effects, skill, "pulse_interval", HeavenCoverArraySkill.PULSE_TICKS, "PULSE_TICKS", "首跳间隔一周期，末次到场地有效期");
                ticks(effects, skill, "refresh_interval", HeavenCoverArraySkill.REFRESH_TICKS, "REFRESH_TICKS", "展开后按实际驻留刷新减伤和移速");
                ticks(effects, skill, "guard_duration", HeavenCoverArraySkill.GUARD_TICKS, "GUARD_TICKS", "每轮驻留刷新，离场后短余时");
                guard(effects, skill, AbilityRuntime.GUARD_15_DAMAGE_TAKEN, "GUARD_15_DAMAGE_TAKEN");
                slow(effects, skill, HeavenCoverArraySkill.SLOW, HeavenCoverArraySkill.SLOW_TICKS);
            }
            case SealedBoundarySkill ignored -> {
                range(effects, skill, "range", SealedBoundarySkill.RANGE, "RANGE", "地面选点");
                range(effects, skill, "radius", SealedBoundarySkill.RADIUS, "RADIUS", "以选点为中心并过滤视线");
                ticks(effects, skill, "warning", SealedBoundarySkill.WARNING_TICKS, "WARNING_TICKS", "提交后的地面预警，可离开");
                ticks(effects, skill, "field_duration", SealedBoundarySkill.FIELD_TICKS, "FIELD_TICKS", "开场伤害及定身后剩余地域的刷新周期");
                ticks(effects, skill, "refresh_interval", SealedBoundarySkill.REFRESH_TICKS, "REFRESH_TICKS", "只更新敌方移速减益");
                ticks(effects, skill, "root_duration", SealedBoundarySkill.ROOT_TICKS, "ROOT_TICKS", "开场合法区域内定身，韧性前名义值");
                slow(effects, skill, SealedBoundarySkill.SLOW, SealedBoundarySkill.SLOW_TICKS);
            }
            case SpiritEchoSkill ignored -> {
                add(effects, skill, "support_count", "trigger", SpiritEchoSkill.REQUIRED_SUPPORTS, "applications", "REQUIRED_SUPPORTS",
                        "同一友军实际增加生命或新增盾；完全溢出、同源无增量和被动授盾均不计", NONE);
                ticks(effects, skill, "support_interval", SpiritEchoSkill.MIN_INTERVAL, "MIN_INTERVAL", "两个可计受援事件相隔的最小真实刻数");
                ticks(effects, skill, "support_window", SpiritEchoSkill.SUPPORT_WINDOW, "SUPPORT_WINDOW", "同一友军各自计算，到期清零");
                ticks(effects, skill, "internal_cooldown", SpiritEchoSkill.INTERNAL_COOLDOWN, "INTERNAL_COOLDOWN", "每位受援者独立");
                ticks(effects, skill, "shield_duration", SpiritEchoSkill.SHIELD_TICKS, "SHIELD_TICKS", "实际触发后结算");
            }
            case SpiritChimeSkill ignored -> {
                ticks(effects, skill, "internal_cooldown", SpiritChimeSkill.INTERNAL_COOLDOWN, "INTERNAL_COOLDOWN", "直接有效英雄命中后起算，全局一次");
                range(effects, skill, "radius", SpiritChimeSkill.ALLY_RANGE, "ALLY_RANGE", "以施法者为中心、可见合法友军");
                haste(effects, skill, SpiritChimeSkill.HASTE, SpiritChimeSkill.HASTE_TICKS);
            }
            case PaperCraneSkill ignored -> {
                range(effects, skill, "range", PaperCraneSkill.RANGE, "RANGE", "按准星优先敌方、其次友方，均未选中时回自身");
                range(effects, skill, "flight_tether_range", PaperCraneSkill.MAX_TETHER_RANGE, "MAX_TETHER_RANGE", "飞行期间超过施法者到锁定单位的最大距离后消失");
                add(effects, skill, "flight_duration", "timing", PaperCraneSkill.flightTicks(), "ticks", "flightTicks",
                        "无遮挡、未受投射物缓时的首次到达时间，曲线和视线按运行时重查", ACTION);
                add(effects, skill, "mana_restore", "mana_restore", PaperCraneSkill.MANA_RESTORE, "mana", "MANA_RESTORE",
                        "纸鹤合法抵达友方，按最大法力限制有效返蓝", NONE);
                slow(effects, skill, PaperCraneSkill.SLOW, PaperCraneSkill.SLOW_TICKS);
            }
            case SpiritTetherSkill ignored -> {
                range(effects, skill, "range", SpiritTetherSkill.RANGE, "RANGE", "需要敌军可见，连接期间超过即断");
                ticks(effects, skill, "tether_duration", SpiritTetherSkill.TETHER_TICKS, "TETHER_TICKS", "持续满足距离与视线才触发次段");
                ticks(effects, skill, "root_duration", SpiritTetherSkill.ROOT_TICKS, "ROOT_TICKS", "锁定后第一段，韧性前");
                slow(effects, skill, SpiritTetherSkill.SLOW, SpiritTetherSkill.SLOW_TICKS);
            }
            case EchoBeaconSkill ignored -> {
                range(effects, skill, "range", EchoBeaconSkill.RANGE, "RANGE", "地面选点");
                range(effects, skill, "radius", EchoBeaconSkill.RADIUS, "RADIUS", "按区域和施法者视线选择敌友");
                ticks(effects, skill, "field_duration", EchoBeaconSkill.FIELD_TICKS, "FIELD_TICKS", "界标提交时起算");
                ticks(effects, skill, "first_entry_poll", EchoBeaconSkill.POLL_INTERVAL, "POLL_INTERVAL", "每次范围轮询，逐敌人最多一次");
                ticks(effects, skill, "shield_interval", EchoBeaconSkill.SHIELD_INTERVAL, "SHIELD_INTERVAL", "按留场与视线逐次判定盾刷新");
                ticks(effects, skill, "shield_duration", EchoBeaconSkill.SHIELD_TICKS, "SHIELD_TICKS", "同源盾只增至名义量并刷新期限");
                slow(effects, skill, EchoBeaconSkill.SLOW, EchoBeaconSkill.SLOW_TICKS);
            }
            case CloudEscortSkill ignored -> {
                range(effects, skill, "range", CloudEscortSkill.RANGE, "RANGE", "可站立点、合法路径且未被禁锢");
                range(effects, skill, "radius", CloudEscortSkill.RADIUS, "RADIUS", "从实际结束位置选择可见友军");
                add(effects, skill, "dash_duration", "displacement_duration", CloudEscortSkill.DASH_TICKS, "ticks", "DASH_TICKS",
                        "局部行动进度；障碍以实际停止位置为准", ACTION);
                ticks(effects, skill, "shield_duration", CloudEscortSkill.SHIELD_TICKS, "SHIELD_TICKS", "结束位置合法友军受盾");
                haste(effects, skill, CloudEscortSkill.HASTE, CloudEscortSkill.HASTE_TICKS);
            }
            case ConvergingChorusSkill ignored -> {
                range(effects, skill, "range", ConvergingChorusSkill.RANGE, "RANGE", "地面选点");
                range(effects, skill, "radius", ConvergingChorusSkill.RADIUS, "RADIUS", "地面中心范围并过滤敌友视线");
                add(effects, skill, "windup_duration", "windup", ConvergingChorusSkill.WINDUP_TICKS, "ticks", "WINDUP_TICKS",
                        "扣费后原地引导，可被打断；完成才开始脉冲排程", ACTION);
                ticks(effects, skill, "pulse_interval", ConvergingChorusSkill.PULSE_TICKS, "PULSE_TICKS", "引导完成后首轮间隔一周期");
                ticks(effects, skill, "field_duration", ConvergingChorusSkill.FIELD_TICKS, "FIELD_TICKS", "成功展开后运行");
            }
            case CovenantShelterSkill ignored -> {
                range(effects, skill, "radius", CovenantShelterSkill.ALLY_RANGE, "ALLY_RANGE", "以施法者为中心、可见合法友军");
                ticks(effects, skill, "shield_duration", CovenantShelterSkill.SHIELD_TICKS, "SHIELD_TICKS", "授盾时开启；同源盾只取最大剩余值");
                haste(effects, skill, CovenantShelterSkill.HASTE, CovenantShelterSkill.HASTE_TICKS);
            }
            default -> throw new IllegalArgumentException(skill.getId());
        }
        if (skill.damageFormulas().isEmpty()) absent(effects, skill, "damage", "该技能本身无任何直接或派生伤害段");
        if (skill.supportAmounts().stream().noneMatch(value -> value.effect() == AbilitySkill.SupportEffect.HEAL))
            absent(effects, skill, "heal", "该技能本身不治疗");
        if (skill.supportAmounts().stream().noneMatch(value -> value.effect() == AbilitySkill.SupportEffect.SHIELD))
            absent(effects, skill, "shield", "该技能本身不授予护盾");
        if (!(skill instanceof SpiritBindSkill || skill instanceof InkProtectionArraySkill
                || skill instanceof HeavenCoverArraySkill || skill instanceof SealedBoundarySkill
                || skill instanceof PaperCraneSkill || skill instanceof SpiritTetherSkill || skill instanceof EchoBeaconSkill))
            absent(effects, skill, "control", "该技能本身不改变敌方行动或移动速率");
    }

    private static void guard(JsonArray rows, AbilitySkill skill, double multiplier, String member) {
        rows.add(effect(skill, "guard_multiplier", "damage_multiplier", field(multiplier, "ratio",
                source(AbilityRuntime.class, member), "驻留获益且实际受非真实伤害，重叠的同类阵法护持取更强档", "on_damage", NONE)));
    }

    private static void slow(JsonArray rows, AbilitySkill skill, float slow, int duration) {
        add(rows, skill, "movement_rate", "movement_multiplier", 1 - Double.parseDouble(Float.toString(slow)), "ratio",
                "SLOW", "有效目标的剩余移动速度倍率，原地施法及攻速保持", NONE);
        ticks(rows, skill, "slow_duration", duration, "SLOW_TICKS", "每次生效的名义时长，韧性折减前");
    }

    private static void haste(JsonArray rows, AbilitySkill skill, float haste, int duration) {
        add(rows, skill, "movement_haste", "movement_haste", haste, "ratio", "HASTE", "该次合格友军支援伴随加速", NONE);
        ticks(rows, skill, "haste_duration", duration, "HASTE_TICKS", "随本次支援起算");
    }

    private static void ticks(JsonArray rows, AbilitySkill skill, String id, int value, String member, String condition) {
        String type = switch (id) {
            case "shield_duration" -> "shield_duration";
            case "root_duration", "slow_duration" -> "control_duration";
            default -> "timing";
        };
        add(rows, skill, id, type, value, "ticks", member, condition, REAL);
    }

    private static void absent(JsonArray rows, AbilitySkill skill, String type, String reason) {
        rows.add(effect(skill, type, type, gap("not_applicable", "none", source(skill.getClass(), "release"),
                "技能本身", "on_effect", NONE, reason)));
    }

    private static void range(JsonArray rows, AbilitySkill skill, String id, double value, String member, String condition) {
        add(rows, skill, id, "range", value, "blocks", member, condition, NONE);
    }

    private static void add(JsonArray rows, AbilitySkill skill, String id, String type, Object value,
                            String unit, String member, String condition, String clock) {
        rows.add(effect(skill, id, type, field(value, unit, source(skill.getClass(), member), condition, "on_effect", clock)));
    }

    private static int[] rankValues(AbilitySkill skill, IntUnaryOperator value) {
        return IntStream.rangeClosed(1, skill.maxRank()).map(value).toArray();
    }
}
