package com.myriad.game;

import net.minecraft.server.level.ServerPlayer;

/** Bounded economy and objective scaling. Values are shared by runtime and regression tests. */
public final class MatchBalance {
    static final double FARM_GOLD_GAP_WEIGHT = .9;
    static final double FARM_LEVEL_GAP_WEIGHT = .15;
    static final double FARM_CATCH_UP_CAP = .45;
    static final int FARM_GOLD_INCOME_FLOOR = 500;
    static final double PASSIVE_GOLD_CATCH_UP_WEIGHT = 0.0;
    static final int ASSIST_GOLD_POOL = 101;
    static final int ASSIST_XP_POOL = 51;

    private MatchBalance() { }
    public static long elapsed(ServerPlayer p) {
        var m=GameManager.get(); return m==null?0:Math.max(0,m.getMatchElapsedTicks(p.level().getGameTime()));
    }
    public static double towerScale(long ticks, int consecutive) {
        return (1+Math.min(1,Math.max(0,ticks)/24000.0))*(1+.30*Math.min(4,Math.max(0,consecutive)));
    }
    /** 前 5 分钟防御塔承伤 65%，随后平滑衰减保护，并在 12 分钟恢复为正常承伤。 */
    public static double towerDamageTakenScale(long ticks) {
        long safeTicks = Math.max(0, ticks);
        if (safeTicks <= 6000) return .65;
        double progress = Math.min(1.0, (safeTicks - 6000) / 8400.0);
        return .65 + .35 * progress;
    }
    public static double deathScale(int deathsWithoutKill) {
        return Math.max(.3,1-.15*Math.max(0,deathsWithoutKill-1));
    }
    public static double underdogScale(int levelGap) {
        return levelGap<2?1:1+Math.min(1.0,.25*(levelGap-1));
    }
    public static int killReward(boolean xp,long elapsed,int streak,int priorDeaths,int levelGap) {
        double time=Math.min(1,Math.max(0,elapsed)/9600.0);
        double base=xp?30+50*time:70+30*time;
        double bounty=Math.min(5,Math.max(0,streak))*(xp?12:30);
        return (int)Math.round((base+bounty)*deathScale(priorDeaths)*underdogScale(levelGap));
    }
    public static int pigGold(long ticks) { return 160+(int)(240*Math.min(1,Math.max(0,ticks)/9600.0)); }
    public static boolean isUnderdog(int ownLevel,int enemyLevel) { return enemyLevel-ownLevel>=2; }
    public static int farmReward(ServerPlayer player, int base, boolean xp) {
        return farmReward(player, base, xp, 1.0);
    }

    static int passiveGoldReward(ServerPlayer player) {
        return GlobalBalance.goldIncome(farmReward(player, GameManager.PASSIVE_GOLD_BASE,
                false, PASSIVE_GOLD_CATCH_UP_WEIGHT));
    }

    private static int farmReward(ServerPlayer player, int base, boolean xp, double catchUpWeight) {
        if (catchUpWeight == 0) return base;
        var match = GameManager.get();
        if (!ModState.isEnabled() || match == null || match.getPhase() != GameManager.Phase.PLAYING
                || TrainingMode.isEnabled(player)) return base;
        double bonus = match.farmBonus(player, xp);
        return (int) Math.round(base * (1 + bonus * catchUpWeight));
    }

    static double goldCatchUp(double ownIncome, double enemyIncome) {
        return Math.min(FARM_CATCH_UP_CAP, Math.max(0,
                (enemyIncome - ownIncome) / Math.max(FARM_GOLD_INCOME_FLOOR, enemyIncome)) * FARM_GOLD_GAP_WEIGHT);
    }

    static double xpCatchUp(int ownLevel, double enemyLevel) {
        return Math.min(FARM_CATCH_UP_CAP, Math.max(0, enemyLevel - ownLevel) * FARM_LEVEL_GAP_WEIGHT);
    }

    static int assistShare(int pool, int count, int index) {
        return pool / count + (index < pool % count ? 1 : 0);
    }
}
