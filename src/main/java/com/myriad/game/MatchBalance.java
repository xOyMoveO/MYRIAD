package com.myriad.game;

import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerPlayer;

/** Bounded economy and objective scaling. Values are shared by runtime and regression tests. */
public final class MatchBalance {
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
    public static int farmReward(ServerPlayer p,int base,boolean xp) {
        var m=GameManager.get();
        if(!ModState.isEnabled()||m==null||m.getPhase()!=GameManager.Phase.PLAYING||TrainingMode.isEnabled(p))return base;
        Faction faction=m.factionOf(p);
        if(faction==null)return base;
        double enemyLevel=0,ownIncome=0,enemyIncome=0; int own=0,enemy=0;
        for(var other:p.level().getServer().getPlayerList().getPlayers()) {
            if (!m.isParticipant(other.getUUID())) continue;
            Faction otherFaction=m.factionOf(other);
            if(otherFaction==null)continue;
            var data=PlayerDataProvider.get(other);
            if(data.getCharacterId()==null)continue;
            if(otherFaction==faction){own++;ownIncome+=data.getMatchGoldEarned();}
            else{enemy++;enemyLevel+=data.getLevel();enemyIncome+=data.getMatchGoldEarned();}
        }
        if(own==0||enemy==0)return base;
        double averageEnemyIncome=enemyIncome/enemy;
        double bonus=xp?Math.min(.45,Math.max(0,enemyLevel/enemy-PlayerDataProvider.get(p).getLevel())*.15)
                :Math.min(.45,Math.max(0,(averageEnemyIncome-ownIncome/own)/Math.max(500,averageEnemyIncome))*.9);
        return (int)Math.round(base*(1+bonus));
    }
}
