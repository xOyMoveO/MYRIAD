package com.myriad.game;

import static com.myriad.game.RosterSnapshot.effect;
import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.gap;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.yike.DistantBombardment;
import com.myriad.character.skills.yike.FixedArtillery;
import com.myriad.character.skills.yike.MobileArray;
import com.myriad.character.skills.yike.PrismaticBeam;
import com.myriad.character.skills.yike.RainbowCalibration;
import com.myriad.character.skills.yike.RainbowShell;
import com.myriad.character.skills.yike.RefractionDenial;
import com.myriad.character.skills.yike.SteadyBarrier;
import com.myriad.character.skills.yike.YikeProjectiles;
import com.myriad.character.skills.yike.YikeStance;
import com.myriad.combat.HeroProjectileTuning;
import java.util.Locale;
import java.util.Map;

final class YikeSkillEffects {
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";
    private static final String NONE = "not_timed";
    private static final String PAID_HIT = "同一敌方英雄被伊珂不同次付费主动有效直接命中；每次施法只计一层";
    private static final String STEADY_CAST = "水平累计路程满足条件、内置冷却结束，且主动技能成功提交";
    private static final String WARNING = "允许的可见地面成功提交，目标在引爆时位于作用球体内且从中心可达";
    private static final String HEAVY = "合法落点已提交、蓄势完成且抛射未被墙体或风墙阻断";
    private static final String BEAM = "已完成蓄势且光束仍在引导，目标在脉冲时处于未被遮挡的光束内";

    private YikeSkillEffects() {}

    static String damageCondition(AbilitySkill skill) {
        return switch (skill) {
            case RainbowCalibration ignored -> PAID_HIT + "且达到触发层数；完全被护盾吸收仍计有效命中";
            case RainbowShell ignored -> "炮弹撞击敌人、地面障碍或到达射程末端；按出膛时姿态倍率结算球形爆炸";
            case RefractionDenial ignored -> WARNING;
            case DistantBombardment ignored -> HEAVY + "；落地球形爆炸每个目标最多一次";
            case PrismaticBeam ignored -> BEAM;
            default -> "对应伤害段有效命中，需另核对实际命中、触发以及其他乘区";
        };
    }

    static String supportCondition(AbilitySkill skill) {
        return skill instanceof SteadyBarrier ? STEADY_CAST : "对应支援段触发并作用于合法友方";
    }

    static void add(JsonArray effects, AbilitySkill skill) {
        switch (skill) {
            case RainbowCalibration ignored -> {
                add(effects, skill, "marks_to_trigger", "trigger", RainbowCalibration.TRIGGER_MARKS, "stack",
                        "TRIGGER_MARKS", PAID_HIT, "on_effective_hit", NONE);
                add(effects, skill, "mark_window", "timing", RainbowCalibration.MARK_WINDOW_TICKS, "ticks",
                        "MARK_WINDOW_TICKS", PAID_HIT, "on_latest_mark", REAL);
            }
            case SteadyBarrier ignored -> {
                add(effects, skill, "path_window", "timing", SteadyBarrier.PATH_WINDOW_TICKS, "ticks",
                        "PATH_WINDOW_TICKS", STEADY_CAST, "before_cast_commit", REAL);
                add(effects, skill, "path_limit", "condition", SteadyBarrier.MAX_PATH_BLOCKS, "blocks",
                        "MAX_PATH_BLOCKS", "连续取水平移动累计路程，折返也计入", "before_cast_commit", NONE);
                add(effects, skill, "shield_duration", "shield_duration", SteadyBarrier.SHIELD_TICKS, "ticks",
                        "SHIELD_TICKS", STEADY_CAST, "on_shield_grant", REAL);
                add(effects, skill, "internal_cooldown", "timing", SteadyBarrier.INTERNAL_COOLDOWN_TICKS, "ticks",
                        "INTERNAL_COOLDOWN_TICKS", STEADY_CAST, "on_shield_grant", REAL);
            }
            case RainbowShell shell -> {
                for (var form : YikeStance.ShotForm.values()) addForm(effects, shell, form);
                var muzzle = field(YikeProjectiles.SHELL_MUZZLE_OFFSET, "blocks",
                        source(YikeProjectiles.class, "SHELL_MUZZLE_OFFSET"), "沿出膛方向从眼位向前",
                        "on_projectile_launch", NONE);
                effects.add(effect(skill, "muzzle_offset", "projectile_origin", muzzle));
            }
            case RefractionDenial ignored -> {
                add(effects, skill, "target_range", "range", RefractionDenial.RANGE, "blocks", "RANGE",
                        WARNING, "on_point_confirm", NONE);
                add(effects, skill, "blast_radius", "radius", RefractionDenial.BLAST_RADIUS, "blocks",
                        "BLAST_RADIUS", WARNING, "on_explosion", NONE);
                add(effects, skill, "warning_ticks", "timing", RefractionDenial.WARNING_TICKS, "ticks",
                        "WARNING_TICKS", WARNING, "on_cast_commit", REAL);
                add(effects, skill, "movement_slow", "movement_slow", RefractionDenial.MOVEMENT_SLOW, "ratio",
                        "MOVEMENT_SLOW", WARNING, "on_explosion", NONE);
                add(effects, skill, "slow_duration", "control_duration", RefractionDenial.SLOW_TICKS, "ticks",
                        "SLOW_TICKS", "引爆时合法目标；实际时长受韧性影响", "on_explosion", REAL);
                add(effects, skill, "recovery_movement", "movement_rate", RefractionDenial.RECOVERY_MOVEMENT,
                        "ratio", "RECOVERY_MOVEMENT", "成功提交后处于恢复窗口，和炮台取更强限制",
                        "on_cast_commit", NONE);
            }
            case FixedArtillery ignored -> addStance(effects, skill, YikeStance.ShotForm.FIXED);
            case MobileArray ignored -> addStance(effects, skill, YikeStance.ShotForm.MOBILE);
            case DistantBombardment ignored -> {
                add(effects, skill, "target_range", "range", DistantBombardment.RANGE, "blocks",
                        "RANGE", HEAVY, "on_point_confirm", NONE);
                add(effects, skill, "blast_radius", "radius", DistantBombardment.BLAST_RADIUS, "blocks",
                        "BLAST_RADIUS", HEAVY, "on_explosion", NONE);
                add(effects, skill, "release_windup", "windup", DistantBombardment.WINDUP_TICKS, "ticks",
                        "WINDUP_TICKS", "确认落点即扣蓝并退出炮台，蓄势后抛射", "on_cast_commit", ACTION);
                effects.add(effect(skill, "flight", "projectile_timing", field(Map.of(
                        "minimumActionTicks", YikeProjectiles.heavyFlightTicks(0),
                        "speedBlocksPerSecond", YikeProjectiles.HEAVY_BASE_SPEED * HeroProjectileTuning.SPEED),
                        "rule_set", source(YikeProjectiles.class, "heavyFlightTicks"), HEAVY,
                        "on_projectile_launch", ACTION)));
            }
            case PrismaticBeam ignored -> {
                add(effects, skill, "release_windup", "windup", PrismaticBeam.WINDUP_TICKS, "ticks",
                        "WINDUP_TICKS", "按下终极即扣蓝并退出炮台，定身蓄势", "on_cast_commit", ACTION);
                add(effects, skill, "beam_range", "range", PrismaticBeam.RANGE, "blocks", "RANGE",
                        BEAM, "on_pulse", NONE);
                add(effects, skill, "beam_width", "width", PrismaticBeam.HALF_WIDTH * 2, "blocks",
                        "HALF_WIDTH", BEAM, "on_pulse", NONE);
                add(effects, skill, "turn_speed", "angular_speed", PrismaticBeam.TURN_DEGREES_PER_SECOND,
                        "degrees_per_second", "TURN_DEGREES_PER_SECOND", BEAM, "on_beam_tick", REAL);
                add(effects, skill, "pulse_interval", "timing", PrismaticBeam.PULSE_INTERVAL_TICKS, "ticks",
                        "PULSE_INTERVAL_TICKS", BEAM, "after_release", REAL);
                add(effects, skill, "beam_duration", "timing", PrismaticBeam.DURATION_TICKS, "ticks",
                        "DURATION_TICKS", BEAM + "；主动取消或被控制可提前终止", "after_release", REAL);
            }
            default -> { }
        }
        inapplicable(effects, skill, "heal", "该技能没有治疗效果");
        if (!(skill instanceof SteadyBarrier)) {
            inapplicable(effects, skill, "shield", "该技能没有独立护盾，符合稳态屏障条件时由被动授盾");
        }
        if (!(skill instanceof RefractionDenial)) {
            inapplicable(effects, skill, "control", "该技能不控制敌方动作或移速");
        }
        if (skill instanceof FixedArtillery || skill instanceof MobileArray || skill instanceof SteadyBarrier) {
            inapplicable(effects, skill, "damage", skill instanceof SteadyBarrier
                    ? "仅提供护盾" : "炮台本身开合姿态，逐发伤害由虹轨弹的对应炮台取值结算");
        }
    }

    private static void inapplicable(JsonArray effects, AbilitySkill skill, String type, String reason) {
        effects.add(effect(skill, type, type, gap("not_applicable", "none", source(skill.getClass(), "release"),
                "该技能自身", "on_effect", NONE, reason)));
    }

    private static void addStance(JsonArray effects, AbilitySkill skill, YikeStance.ShotForm form) {
        String condition = "完成起手、合法缴费后进入姿态；期间不能普攻，Q 另行缴费";
        String formSource = source(YikeStance.class, "ShotForm." + form.name());
        effects.add(effect(skill, "stance_form", "form", field(form.name().toLowerCase(Locale.ROOT), "form",
                formSource, condition, "on_stance_deploy", NONE)));
        boolean mobile = form == YikeStance.ShotForm.MOBILE;
        effects.add(effect(skill, "movement_rate", "movement_rate", field(mobile ? YikeStance.MOBILE_MOVEMENT : 0,
                "ratio", source(YikeStance.class, "refreshMovement"), condition + "；禁止疾跑",
                "while_deployed", NONE)));
        effects.add(effect(skill, "turn_speed", "angular_speed", field(YikeStance.TURN_DEGREES_PER_SECOND,
                "degrees_per_second", source(YikeStance.class, "TURN_DEGREES_PER_SECOND"), condition,
                "on_stance_tick", REAL)));
    }

    private static void addForm(JsonArray effects, RainbowShell skill, YikeStance.ShotForm form) {
        boolean artillery = form != YikeStance.ShotForm.NORMAL;
        String condition = switch (form) {
            case NORMAL -> "普通形态且 Q 已学习";
            case FIXED -> "定轨炮台；Q 已学习、有弹有蓝且射击时点已到";
            case MOBILE -> "游走阵列；Q 已学习、有弹有蓝且射击时点已到";
        };
        String formSource = source(YikeStance.class, "ShotForm." + form.name());
        var fields = new JsonObject();
        var cooldowns = new JsonArray();
        for (int rank = 1; rank <= skill.maxRank(); rank++) {
            var value = field(skill.cooldownTicks(rank, artillery), "ticks", artillery
                    ? source(RainbowShell.class, "cooldownTicks") : "src/main/resources/"
                    + FirstGroupSkills.definitionResource(skill.getId()) + "#skills/" + skill.getId() + "/cooldowns",
                    condition, "on_projectile_launch", REAL);
            value.addProperty("rank", rank);
            cooldowns.add(value);
        }
        fields.add("skillCooldown", cooldowns);
        fields.add("range", field(form.range, "blocks", formSource, condition, "on_projectile_launch", NONE));
        fields.add("speed", field(form.speed, "blocks_per_second", formSource, condition, "on_projectile_launch", ACTION));
        fields.add("effectiveSpeed", field(form.speed * HeroProjectileTuning.SPEED, "blocks_per_second",
                source(YikeProjectiles.class, "shell"), condition + "；未受弹体缓时", "on_projectile_step", ACTION));
        fields.add("radius", field(form.radius, "blocks", formSource, condition, "on_projectile_launch", NONE));
        fields.add("damageMultiplier", field(Map.of("base", form.baseMultiplier, "growthPerStanceRank", form.multiplierPerRank),
                "ratio", formSource, condition, "on_projectile_launch", NONE));
        if (artillery) {
            fields.add("shots", field(form.shells, "shell", formSource, condition, "on_stance_deploy", NONE));
            fields.add("duration", field(YikeStance.DURATION_TICKS, "ticks", source(YikeStance.class, "DURATION_TICKS"),
                    condition, "on_stance_deploy", REAL));
            fields.add("nextShot", field(form.nextShotTicks, "ticks", formSource,
                    condition + "；从上一发出膛计，原 Q 冷却挡首发，W 暂停可以推迟下一发", "on_projectile_launch", REAL));
            fields.add("shotRequestBuffer", field(YikeStance.SHOT_REQUEST_BUFFER_TICKS, "ticks",
                    source(YikeStance.class, "SHOT_REQUEST_BUFFER_TICKS"),
                    condition + "；只允许上膛后提前起手，出膛仍要等真实截止刻", "on_cast_request", REAL));
            fields.add("pauseForW", field(YikeStance.PAUSE_FOR_W_TICKS, "ticks", source(YikeStance.class, "PAUSE_FOR_W_TICKS"),
                    condition + "且 W 已完成合法预警提交", "on_w_commit", REAL));
            fields.add("exitCooldown", field("max_of_existing_and_haste_adjusted_normal", "rule",
                    source(YikeStance.class, "finish"), "本次炮台至少出膛一次且未启用训练免冷却", "on_stance_exit", REAL));
        } else {
            fields.add("shots", gap("not_applicable", "shell", formSource, condition, "on_projectile_launch",
                    NONE, "离开炮台姿态没有弹仓，仅看共用蓝耗与冷却"));
        }
        effects.add(effect(skill, "form_" + form.name().toLowerCase(Locale.ROOT), "form",
                field(fields, "rule_set", formSource, condition, "on_projectile_launch", NONE)));
    }

    private static void add(JsonArray effects, AbilitySkill skill, String id, String type, Object value, String unit,
            String member, String condition, String sampledAt, String clock) {
        effects.add(effect(skill, id, type, field(value, unit, source(skill.getClass(), member),
                condition, sampledAt, clock)));
    }
}
