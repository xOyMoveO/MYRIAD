package com.myriad.game;

import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.skills.fenjue.rework.BurningLastStandSkill;
import com.myriad.character.skills.fenjue.rework.EmberBurnSkill;
import com.myriad.character.skills.fenjue.rework.FlameArmorSkill;
import com.myriad.character.skills.fenjue.rework.FlameBurstSkill;
import com.myriad.character.skills.fenjue.rework.FlameSlashAim;
import com.myriad.character.skills.fenjue.rework.FlameSlashSkill;
import com.myriad.character.skills.fenjue.rework.FlameStepSkill;
import com.myriad.character.skills.fenjue.rework.HeavenFireSeaSkill;
import com.myriad.character.skills.fenjue.rework.LavaCageSkill;
import java.util.stream.IntStream;

final class FenjueSkillEffects {
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";
    private static final String NONE = "not_timed";

    private FenjueSkillEffects() {}

    static String damageCondition(AbilitySkill skill, String id) {
        return switch (skill) {
            case EmberBurnSkill ignored -> "每名敌人独立的一层灼烧；直接主动命中时施加或刷新，非直接效果不递归";
            case FlameSlashSkill ignored -> "两个锁定起手视角的实际扇形分别扫掠，逐段按碰撞与遮挡核验";
            case FlameStepSkill ignored -> "沿突进实际路径敌人各一次，被墙挡住就不能命中墙后目标";
            case LavaCageSkill ignored -> "预警后每个间隔检测仍留在固定火圈内的合法敌人，火圈伤害不刷新灼烧";
            case FlameBurstSkill ignored -> "起手完成时当前位置附近各一段，按触发时自身已损生命提高伤害";
            case HeavenFireSeaSkill ignored -> id.equals("segment.landing")
                    ? "成功落地才造成每目标一次直接物理伤害，可触发所选伤害被动"
                    : "落地后火海在固定点逐跳核验当前仍在圈内的合法目标，按间接物理伤害结算";
            case FlameArmorSkill ignored -> "自身火盾同源效果仍在时，附近合法敌人每间隔结算一次火环";
            default -> throw new IllegalArgumentException(skill.getId());
        };
    }

    static String supportCondition(AbilitySkill skill) {
        if (!(skill instanceof FlameArmorSkill)) throw new IllegalArgumentException(skill.getId());
        return "合法施放后立即授予自身火盾，供来伤实际消耗";
    }

    static void add(JsonArray effects, AbilitySkill skill) {
        switch (skill) {
            case EmberBurnSkill ignored -> {
                add(effects, skill, "burn_duration", "timing", EmberBurnSkill.BURN_TICKS,
                        "ticks", "BURN_TICKS", "每次直接主动命中推进该目标的截止刻", REAL);
                add(effects, skill, "pulse_interval", "timing", EmberBurnSkill.PULSE_TICKS,
                        "ticks", "PULSE_TICKS", "再次直接命中延长持续时间，原跳相位保留", REAL);
            }
            case BurningLastStandSkill ignored -> {
                add(effects, skill, "hp_threshold", "threshold", BurningLastStandSkill.HEALTH_THRESHOLD,
                        "ratio", "HEALTH_THRESHOLD", "直接非真实主动伤害开始结算时，自身生命不高于阈值", NONE);
                add(effects, skill, "internal_cooldown", "timing", BurningLastStandSkill.COOLDOWN_TICKS,
                        "ticks", "COOLDOWN_TICKS", "首次受益时计；同一施放实例各直接段享受同一增幅", REAL);
                add(effects, skill, "damage_multiplier", "damage_multiplier", IntStream.rangeClosed(1, skill.maxRank()).mapToObj(BurningLastStandSkill::damageMultiplier).toList(),
                        "ratio", "damageMultiplier", "按等级列出；间接伤害与真伤不放大", NONE);
            }
            case FlameSlashSkill ignored -> {
                add(effects, skill, "first_cut", "timing", FlameSlashAim.hitTick(0),
                        "ticks", "release", "首段刀面扫掠", ACTION);
                add(effects, skill, "second_cut", "timing", FlameSlashAim.hitTick(1),
                        "ticks", "release", "第二段刀面扫掠", ACTION);
            }
            case FlameStepSkill ignored -> {
                add(effects, skill, "dash_range", "displacement", FlameStepSkill.DISTANCE,
                        "blocks", "DISTANCE", "开阔地的名义速度与位移", ACTION);
                add(effects, skill, "dash_duration", "timing", FlameStepSkill.DASH_TICKS,
                        "ticks", "DASH_TICKS", "开阔地不受缓时的名义位移时长", ACTION);
                add(effects, skill, "path_radius", "range", FlameStepSkill.PATH_RADIUS,
                        "blocks", "PATH_RADIUS", "共享扫掠另含实体尺寸", NONE);
            }
            case LavaCageSkill ignored -> {
                add(effects, skill, "range", "range", LavaCageSkill.RANGE,
                        "blocks", "RANGE", "必须通过合法地面选点", NONE);
                add(effects, skill, "radius", "range", LavaCageSkill.RADIUS,
                        "blocks", "RADIUS", "从固定选点算，遮挡由实际结算核验", NONE);
                add(effects, skill, "warning", "timing", LavaCageSkill.WARNING_TICKS,
                        "ticks", "WARNING_TICKS", "确认施法后预警", REAL);
                add(effects, skill, "field_duration", "timing", LavaCageSkill.FIELD_TICKS,
                        "ticks", "FIELD_TICKS", "从预警结束算，含最后一跳", REAL);
                add(effects, skill, "pulse_interval", "timing", LavaCageSkill.PULSE_TICKS,
                        "ticks", "PULSE_TICKS", "预警结束后的首个完整间隔才有伤害", REAL);
                add(effects, skill, "refresh_interval", "timing", LavaCageSkill.REFRESH_TICKS,
                        "ticks", "REFRESH_TICKS", "圈内每隔该时长刷新减速", REAL);
                add(effects, skill, "slow_rate", "movement_slow", LavaCageSkill.SLOW_RATE,
                        "ratio", "SLOW_RATE", "留在圈内", NONE);
                add(effects, skill, "slow_duration", "control_duration", LavaCageSkill.SLOW_TICKS,
                        "ticks", "SLOW_TICKS", "单次刷新时长", REAL);
            }
            case FlameBurstSkill ignored -> {
                add(effects, skill, "radius", "range", FlameBurstSkill.RADIUS,
                        "blocks", "RADIUS", "从完成起手时的施法者位置算", NONE);
                add(effects, skill, "windup", "windup", FlameBurstSkill.WINDUP_TICKS,
                        "ticks", "WINDUP_TICKS", "提交后的可移动起手", ACTION);
                add(effects, skill, "charge_move_rate", "movement_multiplier", FlameBurstSkill.WINDUP_MOVE_RATE,
                        "ratio", "WINDUP_MOVE_RATE", "起手期间可转向和低速走路", NONE);
                add(effects, skill, "health_steps", "threshold", FlameBurstSkill.HP_STEPS,
                        "divisions", "HP_STEPS", "整段已损生命比例阶梯", NONE);
                add(effects, skill, "damage_bonus_per_step", "damage_multiplier", FlameBurstSkill.BONUS_PER_STEP,
                        "ratio", "BONUS_PER_STEP", "已损生命每完整阶梯的额外倍率", NONE);
                add(effects, skill, "max_damage_bonus", "damage_multiplier", FlameBurstSkill.MAX_BONUS,
                        "ratio", "MAX_BONUS", "已损生命额外倍率封顶", NONE);
            }
            case HeavenFireSeaSkill ignored -> {
                add(effects, skill, "range", "range", HeavenFireSeaSkill.RANGE,
                        "blocks", "RANGE", "合法可站立点，且整段跃进无阻", NONE);
                add(effects, skill, "leap_duration", "timing", HeavenFireSeaSkill.FLIGHT_TICKS,
                        "ticks", "FLIGHT_TICKS", "名义跃进工作量，落地位置另核验", ACTION);
                add(effects, skill, "leap_height", "displacement", HeavenFireSeaSkill.MIN_HEIGHT,
                        "blocks", "MIN_HEIGHT", "相对起点最低高度；下坡增加弧线高度", ACTION);
                add(effects, skill, "impact_radius", "range", HeavenFireSeaSkill.IMPACT_RADIUS,
                        "blocks", "IMPACT_RADIUS", "以实际落地点为中心", NONE);
                add(effects, skill, "field_duration", "timing", HeavenFireSeaSkill.FIELD_TICKS,
                        "ticks", "FIELD_TICKS", "落地后固定地面区域，包含末次脉冲", REAL);
                add(effects, skill, "pulse_interval", "timing", HeavenFireSeaSkill.PULSE_TICKS,
                        "ticks", "PULSE_TICKS", "后续脉冲为间接物理伤害，不施加或刷新灼烧", REAL);
            }
            case FlameArmorSkill ignored -> {
                add(effects, skill, "radius", "range", FlameArmorSkill.RADIUS,
                        "blocks", "RADIUS", "随自身移动；火盾先破就结束", NONE);
                add(effects, skill, "shield_duration", "shield_duration", FlameArmorSkill.SHIELD_TICKS,
                        "ticks", "SHIELD_TICKS", "施放时授予的名义时长", REAL);
                add(effects, skill, "last_pulse", "timing", FlameArmorSkill.LAST_PULSE_TICK,
                        "ticks", "LAST_PULSE_TICK", "护盾存续时允许的末次脉冲时点", REAL);
                add(effects, skill, "pulse_interval", "timing", FlameArmorSkill.PULSE_TICKS,
                        "ticks", "PULSE_TICKS", "首个完整间隔后，结算前检查火盾", REAL);
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
