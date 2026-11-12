package com.myriad.game;

/**
 * 策划案核心数据速查（第十五节）与全局玩法常量。
 */
public final class GameRules {
    private GameRules() {}

    // ---- 胜负条件（可在开局前通过 /myriad settings 修改） ----
    /** 双方宗门通过占领灵脉获得的胜利分数 */
    public static int TEAM_TARGET_SCORE = 500;
    /** 对局时长上限（秒）：到时积分更高的宗门胜利（30 分钟） */
    public static int MATCH_DURATION_SECONDS = 1800;

    // ---- 灵脉争夺节奏 ----
    /** 已占领灵脉被敌方单独进入后的免衰减窗口（4 秒）。 */
    public static final int VEIN_CONTEST_GRACE_TICKS = 80;
    /** 完全控制时全队资源脉冲间隔（5 秒）。 */
    public static final int VEIN_INCOME_PULSE_TICKS = 100;

    /** 对局时长（刻） */
    public static long matchDurationTicks() {
        return MATCH_DURATION_SECONDS * 20L;
    }

    // ---- 汲取与运输（可在开局前修改） ----
    /** 单次汲取耗时（tick，20tick = 1秒） */
    public static int CHANNEL_TICKS = 60;
    /** 单次汲取获得的灵源点数 */
    public static int SPIRIT_PER_EXTRACT = 25;
    /** 汲取时允许的最大位移（方块），超过即打断 */
    public static final double CHANNEL_MOVE_TOLERANCE = 0.02;
    /** 携带灵源时减速等级（药水效果等级，0为I级） */
    public static final int CARRYING_SLOWNESS_LEVEL = 1;
    /** 携带灵源时发光/减速效果刷新时长（tick） */
    public static final int CARRYING_EFFECT_TICKS = 40;

    // ---- 等级成长 ----
    /** 双方统一等级上限。 */
    public static final int MAX_LEVEL = 15;
    /** 每级生命值上限加成（百分比） */
    public static final double HP_PER_LEVEL_PERCENT = 2.0;
    /** 每级攻击力加成（百分比） */
    public static final double AD_PER_LEVEL_PERCENT = 3.0;
    /** 升级经验曲线基数：xpToNext(level) = BASE + (level-1) * STEP */
    public static final int XP_BASE = 100;
    public static final int XP_STEP = 50;


    // ---- 大招升级门槛（双方统一） ----
    public static final int[] ULTIMATE_GATES = {0, 4, 8, 12};
    public static final int ULTIMATE_MAX = 3;

    // ---- 野怪经济（阶段2启用） ----
    public static final int MOB_RESPAWN_SECONDS = 60;
    public static final int MOB_FOX_XP = 20;
    public static final int MOB_FOX_GOLD = 8;
    public static final int MOB_GOLEM_XP = 35;
    public static final int MOB_GOLEM_GOLD = 15;
    public static final int MOB_BEAST_XP = 50;
    public static final int MOB_BEAST_GOLD = 25;
}
