package com.myriad.game;

import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.skills.shuo.CanYueSkill;
import com.myriad.character.skills.shuo.CangFengSkill;
import com.myriad.character.skills.shuo.DuanLiuSkill;
import com.myriad.character.skills.shuo.JianQieSkill;
import com.myriad.character.skills.shuo.NaDaoSkill;
import com.myriad.character.skills.shuo.QiaoFanSkill;
import com.myriad.character.skills.shuo.ShuoCombat;
import com.myriad.character.skills.shuo.WuShengSkill;
import com.myriad.character.skills.shuo.YiShanSkill;

final class ShuoSkillEffects {
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";
    private static final String NONE = "not_timed";

    private ShuoSkillEffects() {}

    static String damageCondition(AbilitySkill skill, String id) {
        return switch (skill) {
            case CangFengSkill ignored -> "藏锋出鞘时按当前锋意比例追加一段，名义值为满锋意；普攻与出鞘技能各走实际命中";
            case JianQieSkill ignored -> "成功格挡后的下一次出鞘，实际有效命中才结算";
            case NaDaoSkill ignored -> "出鞘时先斩再侧退，在原起点回斩，同一敌人可分别命中";
            case QiaoFanSkill ignored -> id.equals("riposte")
                    ? "收鞘成功格挡后反击命中，可能另有拔刀增量" : "拔刀姿态下斩击范围内命中";
            case YiShanSkill ignored -> switch (id) {
                case "tailwind" -> "出鞘时即时短突进，扫掠有效命中";
                case "dash_hit" -> "收鞘蓄势完毕出鞘，长突进扫掠有效命中，可另加首次拔刀伤害";
                default -> "已被收鞘长突进有效命中且仍为合法目标，突进结束后延迟结算";
            };
            case DuanLiuSkill ignored -> id.equals("double_slash")
                    ? "出鞘后近身双斩各自实际命中，两次均命中同一目标才禁锢"
                    : "收鞘蓄势出鞘后沿最初直线斩两段，两次均命中同一目标才禁锢";
            case WuShengSkill ignored -> id.equals("empty_break")
                    ? "出鞘时先短突进、再斩击终点正前方"
                    : "收鞘蓄势穿过目标时标记，回鞘后结算标记，按目标缺失生命比例乘有上限的倍率";
            case CanYueSkill ignored -> id.equals("moon_final")
                    ? "已经出鞘、未开启残月三式时，单次收尾横斩"
                    : "收鞘开启残月，前两段各独立出刀，每次续段需有效输入，第三段全中同一目标才禁锢";
            default -> throw new IllegalArgumentException(skill.getId());
        };
    }

    static String supportCondition(AbilitySkill skill) {
        if (skill instanceof QiaoFanSkill) return "收鞘成功格挡后反击命中至少一个敌人，再给自身加盾";
        throw new IllegalArgumentException(skill.getId());
    }

    static void add(JsonArray effects, AbilitySkill skill) {
        switch (skill) {
            case CangFengSkill ignored -> {
                add(effects, skill, "gauge_per_tick", "trigger", ShuoCombat.GAUGE_PER_TICK, "gauge/tick",
                        ShuoCombat.class, "GAUGE_PER_TICK", "收鞘且行动未锁定", "on_passive_tick", REAL);
                add(effects, skill, "gauge_max", "trigger", ShuoCombat.GAUGE_MAX, "gauge",
                        ShuoCombat.class, "GAUGE_MAX", "出鞘时消耗当前锋意；附伤乘当前锋意与上限之比",
                        "on_draw", NONE);
            }
            case JianQieSkill ignored -> add(effects, skill, "ready_window", "timing", ShuoCombat.RIPOSTE_READY_TICKS,
                    "ticks", ShuoCombat.class, "RIPOSTE_READY_TICKS", "成功格挡开窗，下一次出鞘消耗", "on_parry", REAL);
            case NaDaoSkill ignored -> {
                own(effects, skill, "cut_range", "range", NaDaoSkill.CUT_RANGE, "blocks", "CUT_RANGE", "出鞘两斩以侧退前位置为准", NONE);
                own(effects, skill, "retreat_range", "displacement", NaDaoSkill.RETREAT_RANGE, "blocks", "RETREAT_RANGE", "合法侧退，遇障碍提前停", ACTION);
                own(effects, skill, "retreat_duration", "timing", NaDaoSkill.RETREAT_TICKS, "ticks", "RETREAT_TICKS", "侧退位移过程", ACTION);
                own(effects, skill, "drawn_action_lock", "timing", NaDaoSkill.RETREAT_TICKS + ShuoCombat.SHEATHE_TICKS,
                        "ticks", "release", "名义出鞘侧退后完成归鞘，不受阻时", ACTION);
                own(effects, skill, "action_lock", "timing", NaDaoSkill.RECOVERY_TICKS, "ticks", "RECOVERY_TICKS", "开始侧退", ACTION);
                own(effects, skill, "return_delay", "timing", NaDaoSkill.RETURN_TICKS, "ticks", "RETURN_TICKS", "出鞘起手后至原地回斩", ACTION);
                own(effects, skill, "return_slow_rate", "movement_slow", NaDaoSkill.SLOW, "ratio", "SLOW", "回斩有效命中", NONE);
                own(effects, skill, "return_movement_rate", "movement_multiplier", 1 - NaDaoSkill.SLOW, "ratio", "SLOW", "回斩后剩余移速倍率", NONE);
                own(effects, skill, "return_slow_duration", "control_duration", NaDaoSkill.SLOW_TICKS, "ticks", "SLOW_TICKS", "回斩有效命中", REAL);
                own(effects, skill, "sheathed_haste", "movement_haste", NaDaoSkill.HASTE, "ratio", "HASTE", "收鞘完成侧退且未受控", NONE);
                own(effects, skill, "sheathed_haste_duration", "timing", NaDaoSkill.HASTE_TICKS, "ticks", "HASTE_TICKS", "收鞘完成侧退且未受控", REAL);
            }
            case QiaoFanSkill ignored -> {
                add(effects, skill, "guard_window", "timing", ShuoCombat.GUARD_TICKS, "ticks",
                        ShuoCombat.class, "GUARD_TICKS", "收鞘起手，仅拦一笔合格正面攻击", "on_cast", REAL);
                add(effects, skill, "guard_half_angle", "angle", ShuoCombat.GUARD_HALF_ANGLE_DEGREES, "degrees",
                        ShuoCombat.class, "GUARD_HALF_ANGLE_DEGREES", "水平朝向与敌害来源的半角", "on_incoming_damage", NONE);
                add(effects, skill, "counter_window", "timing", ShuoCombat.COUNTER_WINDOW_TICKS, "ticks",
                        ShuoCombat.class, "COUNTER_WINDOW_TICKS", "成功格挡后，再按发动免费二段", "on_parry", REAL);
                own(effects, skill, "counter_range", "range", QiaoFanSkill.COUNTER_RANGE, "blocks", "COUNTER_RANGE", "格挡后反击扇区", NONE);
                own(effects, skill, "counter_stun", "control_duration", QiaoFanSkill.COUNTER_STUN_TICKS, "ticks", "COUNTER_STUN_TICKS", "反击有效命中，名义时长未减韧性", REAL);
                own(effects, skill, "counter_shield_duration", "shield_duration", QiaoFanSkill.COUNTER_SHIELD_TICKS, "ticks", "COUNTER_SHIELD_TICKS", "反击至少有效命中一人", REAL);
                own(effects, skill, "drawn_range", "range", QiaoFanSkill.DRAWN_RANGE, "blocks", "DRAWN_RANGE", "出鞘横扫", NONE);
                own(effects, skill, "drawn_slow_duration", "control_duration", QiaoFanSkill.DRAWN_SLOW_TICKS, "ticks", "DRAWN_SLOW_TICKS", "出鞘横扫有效命中", REAL);
                own(effects, skill, "drawn_movement_rate", "movement_multiplier", 1 - QiaoFanSkill.DRAWN_SLOW, "ratio", "DRAWN_SLOW", "出鞘横扫后剩余移速倍率", NONE);
                own(effects, skill, "drawn_slow_rate", "movement_slow", QiaoFanSkill.DRAWN_SLOW, "ratio", "DRAWN_SLOW", "出鞘横扫有效命中", NONE);
            }
            case YiShanSkill ignored -> {
                own(effects, skill, "windup", "windup", YiShanSkill.WINDUP_TICKS, "ticks", "WINDUP_TICKS", "收鞘起手", ACTION);
                own(effects, skill, "sheathed_range", "displacement", YiShanSkill.SHEATHED_RANGE, "blocks", "SHEATHED_RANGE", "收鞘长突进", ACTION);
                own(effects, skill, "sheathed_dash_duration", "timing", YiShanSkill.SHEATHED_DASH_TICKS, "ticks", "SHEATHED_DASH_TICKS", "收鞘长突进名义时长", ACTION);
                own(effects, skill, "drawn_range", "displacement", YiShanSkill.DRAWN_RANGE, "blocks", "DRAWN_RANGE", "出鞘短突进", ACTION);
                own(effects, skill, "drawn_dash_duration", "timing", YiShanSkill.DRAWN_DASH_TICKS, "ticks", "DRAWN_DASH_TICKS", "出鞘短突进名义时长", ACTION);
                own(effects, skill, "sheathed_dash_speed", "speed", YiShanSkill.SHEATHED_RANGE / YiShanSkill.SHEATHED_DASH_TICKS, "blocks/tick", "release", "长突进名义速度", ACTION);
                own(effects, skill, "drawn_dash_speed", "speed", YiShanSkill.DRAWN_RANGE / YiShanSkill.DRAWN_DASH_TICKS, "blocks/tick", "release", "短突进名义速度", ACTION);
                own(effects, skill, "wound_from_release", "timing", YiShanSkill.SHEATHED_DASH_TICKS + YiShanSkill.WOUND_DELAY, "ticks", "release", "无缓时无阻时从突进开始到刀痕结算的名义时长", NONE);
                own(effects, skill, "wound_delay", "timing", YiShanSkill.WOUND_DELAY, "ticks", "WOUND_DELAY", "收鞘长突进正常结束后", REAL);
                own(effects, skill, "wound_movement_rate", "movement_multiplier", 1 - YiShanSkill.WOUND_SLOW, "ratio", "WOUND_SLOW", "刀痕命中后剩余移速倍率", NONE);
                own(effects, skill, "drawn_movement_rate", "movement_multiplier", 1 - YiShanSkill.DRAWN_SLOW, "ratio", "DRAWN_SLOW", "短突进后剩余移速倍率", NONE);
                own(effects, skill, "wound_slow_rate", "movement_slow", YiShanSkill.WOUND_SLOW, "ratio", "WOUND_SLOW", "刀痕命中", NONE);
                own(effects, skill, "drawn_slow_rate", "movement_slow", YiShanSkill.DRAWN_SLOW, "ratio", "DRAWN_SLOW", "出鞘短突进命中", NONE);
                own(effects, skill, "slow_duration", "control_duration", YiShanSkill.SLOW_TICKS, "ticks", "SLOW_TICKS", "任一减速生效，名义时长未减韧性", REAL);
            }
            case DuanLiuSkill ignored -> {
                own(effects, skill, "windup", "windup", DuanLiuSkill.WINDUP_TICKS, "ticks", "WINDUP_TICKS", "收鞘起手", ACTION);
                own(effects, skill, "sheathed_range", "range", DuanLiuSkill.SHEATHED_RANGE, "blocks", "SHEATHED_RANGE", "收鞘两击各自校验线段", NONE);
                own(effects, skill, "drawn_range", "range", DuanLiuSkill.DRAWN_RANGE, "blocks", "DRAWN_RANGE", "出鞘两击各自校验扇区", NONE);
                own(effects, skill, "sheathed_follow", "timing", DuanLiuSkill.SHEATHED_FOLLOW_TICKS, "ticks", "SHEATHED_FOLLOW_TICKS", "收鞘版本两击间，受强控中断", ACTION);
                own(effects, skill, "drawn_follow", "timing", DuanLiuSkill.DRAWN_FOLLOW_TICKS, "ticks", "DRAWN_FOLLOW_TICKS", "出鞘版本两击间，受强控中断", ACTION);
                own(effects, skill, "sheathed_root", "control_duration", DuanLiuSkill.SHEATHED_ROOT_TICKS, "ticks", "SHEATHED_ROOT_TICKS", "收鞘版同一目标两击有效命中", REAL);
                own(effects, skill, "drawn_root", "control_duration", DuanLiuSkill.DRAWN_ROOT_TICKS, "ticks", "DRAWN_ROOT_TICKS", "出鞘版同一目标两击有效命中", REAL);
                own(effects, skill, "movement_rate", "movement_multiplier", 1 - DuanLiuSkill.SLOW, "ratio", "SLOW", "首击命中后剩余移速倍率", NONE);
                own(effects, skill, "slow_rate", "movement_slow", DuanLiuSkill.SLOW, "ratio", "SLOW", "收鞘版首击有效命中", NONE);
                own(effects, skill, "slow_duration", "control_duration", DuanLiuSkill.SLOW_TICKS, "ticks", "SLOW_TICKS", "收鞘版首击有效命中", REAL);
            }
            case WuShengSkill ignored -> {
                own(effects, skill, "windup", "windup", WuShengSkill.WINDUP_TICKS, "ticks", "WINDUP_TICKS", "收鞘起手", ACTION);
                own(effects, skill, "mark_range", "displacement", WuShengSkill.MARK_DASH_RANGE, "blocks", "MARK_DASH_RANGE", "收鞘长突进并标记通路目标", ACTION);
                own(effects, skill, "mark_dash_duration", "timing", WuShengSkill.MARK_DASH_TICKS, "ticks", "MARK_DASH_TICKS", "收鞘长突进名义时长", ACTION);
                own(effects, skill, "mark_cut_time", "timing", WuShengSkill.MARK_DASH_TICKS + WuShengSkill.MARK_DELAY, "ticks", "release", "正常通路自长突进开始到引爆", ACTION);
                own(effects, skill, "drawn_follow_range", "range", WuShengSkill.DRAWN_DASH_RANGE + WuShengSkill.DRAWN_CUT_RANGE, "blocks", "release", "出鞘短突进加终点前方斩击的名义最大可达距离", NONE);
                own(effects, skill, "mark_delay", "timing", WuShengSkill.MARK_DELAY, "ticks", "MARK_DELAY", "收鞘长突进结束后，未受控时引爆标记", ACTION);
                own(effects, skill, "missing_hp_scale", "damage_multiplier", WuShengSkill.MISSING_HP_SCALE, "ratio", "MISSING_HP_SCALE", "标记伤害乘 1 加该值乘目标缺血比例", NONE);
                own(effects, skill, "drawn_dash_range", "displacement", WuShengSkill.DRAWN_DASH_RANGE, "blocks", "DRAWN_DASH_RANGE", "出鞘短突进", ACTION);
                own(effects, skill, "drawn_dash_duration", "timing", WuShengSkill.DRAWN_DASH_TICKS, "ticks", "DRAWN_DASH_TICKS", "出鞘短突进名义时长", ACTION);
                own(effects, skill, "drawn_cut_range", "range", WuShengSkill.DRAWN_CUT_RANGE, "blocks", "DRAWN_CUT_RANGE", "出鞘斩击从实际突进终点起算", NONE);
                own(effects, skill, "drawn_stun", "control_duration", WuShengSkill.DRAWN_STUN_TICKS, "ticks", "DRAWN_STUN_TICKS", "出鞘斩击有效命中", REAL);
            }
            case CanYueSkill ignored -> {
                own(effects, skill, "drawn_range", "range", CanYueSkill.DRAWN_RANGE, "blocks", "DRAWN_RANGE", "单式横斩", NONE);
                own(effects, skill, "follow_and_sheathe", "timing", CanYueSkill.FOLLOW_TICKS + ShuoCombat.SHEATHE_TICKS, "ticks", "release", "收尾斩后完成全部收鞘的名义时长", ACTION);
                own(effects, skill, "drawn_windup", "windup", CanYueSkill.DRAWN_WINDUP, "ticks", "DRAWN_WINDUP", "出鞘单式起手", ACTION);
                own(effects, skill, "opening_range", "range", CanYueSkill.FIRST_RANGE, "blocks", "FIRST_RANGE", "收鞘开启的第一和第二式", NONE);
                own(effects, skill, "final_range", "range", CanYueSkill.LAST_RANGE, "blocks", "LAST_RANGE", "收鞘开启的第三式", NONE);
                own(effects, skill, "windup", "windup", CanYueSkill.SHEATHED_WINDUP, "ticks", "SHEATHED_WINDUP", "残月三式各段起手", ACTION);
                own(effects, skill, "recast_window", "timing", CanYueSkill.RECAST_WINDOW, "ticks", "RECAST_WINDOW", "第一／二式动作结束后的续段窗口", REAL);
                own(effects, skill, "final_root", "control_duration", CanYueSkill.FINAL_ROOT_TICKS, "ticks", "FINAL_ROOT_TICKS", "同一目标三式全中", REAL);
                own(effects, skill, "opening_slow_rate", "movement_slow", CanYueSkill.OPENING_SLOW, "ratio", "OPENING_SLOW", "第一式有效命中", NONE);
                own(effects, skill, "opening_slow_duration", "control_duration", CanYueSkill.OPENING_SLOW_TICKS, "ticks", "OPENING_SLOW_TICKS", "第一式有效命中", REAL);
            }
            default -> throw new IllegalArgumentException(skill.getId());
        }
    }

    private static void own(JsonArray effects, AbilitySkill skill, String id, String type, Object value,
                            String unit, String member, String condition, String clock) {
        add(effects, skill, id, type, value, unit, skill.getClass(), member, condition, "on_effect", clock);
    }

    private static void add(JsonArray effects, AbilitySkill skill, String id, String type, Object value, String unit,
                            Class<?> owner, String member, String condition, String at, String clock) {
        effects.add(RosterSnapshot.effect(skill, id, type, field(value, unit, source(owner, member), condition, at, clock)));
    }
}
