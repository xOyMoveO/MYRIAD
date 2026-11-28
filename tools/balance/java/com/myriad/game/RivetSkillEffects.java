package com.myriad.game;

import static com.myriad.game.RosterSnapshot.effect;
import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.gap;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.skills.rivet.BreachCharge;
import com.myriad.character.skills.rivet.BreachRhythm;
import com.myriad.character.skills.rivet.EmergencyStrut;
import com.myriad.character.skills.rivet.HoldProtocol;
import com.myriad.character.skills.rivet.ImpactHammer;
import com.myriad.character.skills.rivet.RivetPush;
import com.myriad.character.skills.rivet.StationArmor;
import com.myriad.character.skills.rivet.TacticalAnchor;
import com.myriad.character.skills.rivet.TowCable;

final class RivetSkillEffects {
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";
    private static final String NONE = "not_timed";
    private static final String ANCHOR_STRIKE = "有效撞墙触点在自身锚点的触发范围内，且锚击已就绪";
    private static final String TOW_TARGET = "自身锚点可用，目视无遮挡，玩家身体扫掠及墙前落点合法";

    private RivetSkillEffects() {}

    static String damageCondition(AbilitySkill skill, String id) {
        return switch (skill) {
            case BreachRhythm ignored -> "冲击锤实际撞墙并获得强化后，窗口内下一次普通攻击直接有效命中";
            case ImpactHammer ignored -> id.equals("wall_collision")
                    ? "准星附近首敌被冲击锤有效击退，且在撞墙奖励窗口内实际撞墙"
                    : "锤击扇区内的合法敌方单位";
            case BreachCharge ignored -> id.equals("wall_collision")
                    ? "被带行的首名敌人在本次冲锋中实际撞墙"
                    : "完成蓄力冲锋后首次有效接触敌人，撞墙段另算";
            default -> throw new IllegalArgumentException(skill.getId());
        };
    }

    static String supportCondition(AbilitySkill skill) {
        return switch (skill) {
            case StationArmor ignored -> "在自身有效锚点附近累计接地后获得自身盾，离阵及破盾分别处理";
            case EmergencyStrut ignored -> "合法施放时自身及范围内存活合法友方获盾，同类撑架盾只计最高剩余值";
            case HoldProtocol ignored -> "施法者首次进入已锁定的固守区域时获得源独立护盾，至区域结束";
            default -> throw new IllegalArgumentException(skill.getId());
        };
    }

    static void add(JsonArray effects, AbilitySkill skill) {
        switch (skill) {
            case StationArmor ignored -> {
                add(effects, skill, "anchor_radius", "range", StationArmor.ANCHOR_RADIUS, "blocks", "ANCHOR_RADIUS",
                        "自身锚点可用", "inside_station", NONE);
                add(effects, skill, "grounded_time", "timing", StationArmor.GROUNDED_TICKS, "ticks", "GROUNDED_TICKS",
                        "累计接地，不要求连续接地；持续离阵或破盾会清空累计", "inside_station", REAL);
                add(effects, skill, "leave_time", "timing", StationArmor.LEAVE_TICKS, "ticks", "LEAVE_TICKS",
                        "持续离开阵地才移除该来源残盾", "outside_station", REAL);
                add(effects, skill, "break_recovery", "timing", StationArmor.BREAK_RECOVERY_TICKS, "ticks", "BREAK_RECOVERY_TICKS",
                        "此来源护盾被实际来伤耗尽", "on_shield_exhausted", REAL);
                add(effects, skill, "return_fraction", "shield_ratio", StationArmor.RETURN_SHIELD_FRACTION, "ratio", "RETURN_SHIELD_FRACTION",
                        "持续离阵后回阵并累计接地，按当时满盾值取强刷新", "on_shield_grant", NONE);
                inapplicable(effects, skill, "damage", "control", "heal");
            }
            case BreachRhythm ignored -> {
                add(effects, skill, "bonus_window", "timing", BreachRhythm.WINDOW_TICKS, "ticks", "WINDOW_TICKS",
                        "冲击锤在有效窗口内实际撞墙，且被动恢复完毕", "on_hammer_wall_hit", REAL);
                add(effects, skill, "internal_cooldown", "timing", BreachRhythm.RECOVERY_TICKS, "ticks", "RECOVERY_TICKS",
                        "墙击已触发被动", "on_hammer_wall_hit", REAL);
                add(effects, skill, "major_refund", "cooldown_refund", BreachRhythm.MAJOR_REFUND_TICKS, "ticks", "MAJOR_REFUND_TICKS",
                        "下一次直接普通攻击有效命中，归属已选 E，余量最低为零", "on_effective_basic", REAL);
                inapplicable(effects, skill, "control", "shield", "heal");
            }
            case ImpactHammer ignored -> {
                add(effects, skill, "hit_range", "range", ImpactHammer.HIT_RANGE, "blocks", "HIT_RANGE",
                        "扇区内敌方单位，判身体碰撞箱", "on_cast_commit", NONE);
                add(effects, skill, "push", "displacement", ImpactHammer.PUSH_DISTANCE, "blocks", "PUSH_DISTANCE",
                        "准星附近首敌有效受击，且无固守区域延长；碰撞和推移减免可截断", "on_effective_hit", NONE);
                add(effects, skill, "protocol_push", "displacement", ImpactHammer.ANCHORED_PUSH_DISTANCE, "blocks", "ANCHORED_PUSH_DISTANCE",
                        "准星附近首敌有效受击且在施法者固守区域内", "on_effective_hit", NONE);
                effects.add(effect(skill, "wall_window", "timing", field(RivetPush.HAMMER_WALL_WINDOW_TICKS, "ticks",
                        source(RivetPush.class, "HAMMER_WALL_WINDOW_TICKS"), "实际侧墙碰撞可在窗口内追加伤害与控制",
                        "on_primary_push_start", REAL)));
                wallRoots(effects, skill, ImpactHammer.WALL_ROOT_TICKS, ImpactHammer.ANCHOR_ROOT_TICKS);
                inapplicable(effects, skill, "shield", "heal");
            }
            case TacticalAnchor ignored -> {
                add(effects, skill, "placement_range", "range", TacticalAnchor.PLACEMENT_RANGE, "blocks", "PLACEMENT_RANGE",
                        "准星命中已加载且边界内的实体方块侧面，依附形状有效", "on_cast_request", NONE);
                add(effects, skill, "anchor_lifetime", "timing", TacticalAnchor.LIFETIME_TICKS, "ticks", "LIFETIME_TICKS",
                        "新部署或替换锚点时起算；支撑被破坏则移除，支撑区块未加载时停用", "on_placement_commit", REAL);
                add(effects, skill, "strike_radius", "range", TacticalAnchor.STRIKE_RADIUS, "blocks", "STRIKE_RADIUS",
                        "正式战斗中撞墙触点与该来源可用锚点的距离", "on_wall_collision", NONE);
                add(effects, skill, "strike_recovery", "timing", TacticalAnchor.STRIKE_RECOVERY_TICKS, "ticks", "STRIKE_RECOVERY_TICKS",
                        "锚击有效触发才开始，换锚不重置", "on_anchor_strike", REAL);
                effects.add(effect(skill, "preparation_mana", "cost", field(0, "mana", source(AbilityRuntime.class, "preparationFor"),
                        "选角后的准备期且墙面有效；锚击要等正式战斗", "on_placement_commit", NONE)));
                inapplicable(effects, skill, "damage", "control", "shield", "heal");
            }
            case EmergencyStrut ignored -> {
                add(effects, skill, "ally_radius", "range", EmergencyStrut.ALLY_RADIUS, "blocks", "ALLY_RADIUS",
                        "盟友存活且位于施法者周围，自身始终计入", "on_cast_commit", NONE);
                add(effects, skill, "shield_duration", "shield_duration", EmergencyStrut.SHIELD_TICKS, "ticks", "SHIELD_TICKS",
                        "同来源按同类撑架最高剩余盾规则更新", "on_shield_grant", REAL);
                add(effects, skill, "self_knockback_factor", "displacement_ratio", EmergencyStrut.KNOCKBACK_FACTOR, "ratio", "KNOCKBACK_FACTOR",
                        "仅施法者在撑架持续时间内受到击退", "on_knockback", NONE);
                add(effects, skill, "anchor_radius", "range", EmergencyStrut.ANCHOR_RADIUS, "blocks", "ANCHOR_RADIUS",
                        "施法点与自己有效锚点之间的距离", "on_cast_commit", NONE);
                add(effects, skill, "anchor_refund", "cooldown_refund", EmergencyStrut.ANCHOR_REFUND_TICKS, "ticks", "ANCHOR_REFUND_TICKS",
                        "施放时在锚点返还范围内，W 剩余冷却最低为零", "on_cast_commit", REAL);
                inapplicable(effects, skill, "damage", "control", "heal");
            }
            case TowCable ignored -> {
                add(effects, skill, "anchor_range", "range", TowCable.RANGE, "blocks", "RANGE", TOW_TARGET, "on_cast_request", NONE);
                add(effects, skill, "motion_duration", "displacement_duration", TowCable.MOTION_TICKS, "ticks", "MOTION_TICKS",
                        "沿合法路径牵引自身，重按、路径受阻或锚点失效提前结束", "on_cast_commit", ACTION);
                inapplicable(effects, skill, "damage", "control", "shield", "heal");
            }
            case HoldProtocol ignored -> {
                add(effects, skill, "anchor_range", "range", HoldProtocol.ANCHOR_RANGE, "blocks", "ANCHOR_RANGE",
                        "以当前自身有效锚点为中心锁定区域", "on_cast_request", NONE);
                add(effects, skill, "zone_radius", "range", HoldProtocol.ZONE_RADIUS, "blocks", "ZONE_RADIUS",
                        "锁圈后换锚或支撑损毁不移动区域", "inside_zone", NONE);
                add(effects, skill, "zone_duration", "shield_duration", HoldProtocol.ZONE_TICKS, "ticks", "ZONE_TICKS",
                        "从施法提交到区域结束，自盾在施法者首次入区时取剩余时间", "on_cast_commit", REAL);
                add(effects, skill, "incoming_damage_factor", "damage_reduction_ratio", HoldProtocol.INCOMING_DAMAGE_FACTOR, "ratio", "INCOMING_DAMAGE_FACTOR",
                        "区域内自身及友方的普通来伤，包括仅标记复盘类型的 NPC 真伤。带技能真伤上下文的来伤绕过减伤", "on_incoming_damage", NONE);
                add(effects, skill, "hammer_extension_rule", "displacement_rule", "target_inside_zone", "rule", "extendsHammer",
                        "目标在施法者自己的固守区域内才采用冲击锤延长距离", "on_effective_hammer_hit", NONE);
                inapplicable(effects, skill, "damage", "control", "heal");
            }
            case BreachCharge ignored -> {
                add(effects, skill, "dash_range", "displacement", BreachCharge.DISTANCE, "blocks", "DISTANCE",
                        "完成蓄力后开始，地形和中断可缩短；首位有效命中者受带行", "on_cast_commit", NONE);
                add(effects, skill, "motion_duration", "displacement_duration", BreachCharge.MOTION_TICKS, "ticks", "MOTION_TICKS",
                        "受行动缓时影响的冲锋进度，碰撞或中断可提前结束", "on_cast_commit", ACTION);
                wallRoots(effects, skill, BreachCharge.WALL_ROOT_TICKS, BreachCharge.ANCHOR_ROOT_TICKS);
                inapplicable(effects, skill, "shield", "heal");
            }
            default -> throw new IllegalArgumentException(skill.getId());
        }
    }

    private static void wallRoots(JsonArray effects, AbilitySkill skill, int wallRoot, int anchorRoot) {
        add(effects, skill, "wall_root", "control_duration", wallRoot, "ticks", "WALL_ROOT_TICKS",
                "有效撞墙，普通墙击；名义定身在韧性及同目标递减前", "on_collision", REAL);
        add(effects, skill, "anchor_root", "control_duration", anchorRoot, "ticks", "ANCHOR_ROOT_TICKS",
                ANCHOR_STRIKE + "；名义定身在韧性及同目标递减前", "on_collision", REAL);
        effects.add(effect(skill, "wall_root_dr_window", "timing", field(RivetPush.WALL_ROOT_DR_TICKS, "ticks",
                source(RivetPush.class, "WALL_ROOT_DR_TICKS"), "同一目标在窗口内再次撞墙，名义定身减半",
                "on_previous_wall_collision", REAL)));
    }

    private static void add(JsonArray effects, AbilitySkill skill, String id, String type, Object value,
            String unit, String member, String condition, String sampledAt, String clock) {
        effects.add(effect(skill, id, type, field(value, unit, source(skill.getClass(), member), condition, sampledAt, clock)));
    }

    private static void inapplicable(JsonArray effects, AbilitySkill skill, String... types) {
        for (String type : types) {
            effects.add(effect(skill, type, type, gap("not_applicable", "none", source(skill.getClass(), "release"),
                    "该技能自身", "on_effect", NONE, "该效果无独立结算")));
        }
    }
}
