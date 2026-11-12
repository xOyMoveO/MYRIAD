package com.myriad.game;

import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.EquipmentRegistry;
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
        var m=GameManager.get(); var d=PlayerDataProvider.get(p);
        if(m==null||m.getPhase()!=GameManager.Phase.PLAYING||TrainingMode.isEnabled()||m.factionOf(p)==null)return base;
        double ownLevel=0,enemyLevel=0,ownWealth=0,enemyWealth=0; int own=0,enemy=0;
        for(var other:p.level().getServer().getPlayerList().getPlayers()) {
            if(m.factionOf(other)==null)continue;
            var od=PlayerDataProvider.get(other); if(od.getCharacterId()==null)continue;
            double wealth=od.getGold();
            for(String id:od.getEquipmentSlots()) {var e=EquipmentRegistry.get(id);if(e!=null)wealth+=e.price();}
            if(m.factionOf(other)==m.factionOf(p)){own++;ownLevel+=od.getLevel();ownWealth+=wealth;}
            else{enemy++;enemyLevel+=od.getLevel();enemyWealth+=wealth;}
        }
        if(own==0||enemy==0)return base;
        double bonus=xp?Math.min(.45,Math.max(0,enemyLevel/enemy-d.getLevel())*.15)
                :Math.min(.45,Math.max(0,(enemyWealth/enemy-ownWealth/own)/Math.max(500,enemyWealth/enemy))*.9);
        return (int)Math.round(base*(1+bonus));
    }
}
