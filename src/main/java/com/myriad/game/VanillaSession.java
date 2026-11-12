package com.myriad.game;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;

/** Restore the player's ordinary inventory and mode after an opt-in mod session. */
public final class VanillaSession {
    private record Snapshot(java.util.List<ItemStack> items, java.util.List<MobEffectInstance> effects,
            GameType mode, float health, int food, float saturation, int xp, int level, float progress, String team) {}
    private static final java.util.Map<java.util.UUID,Snapshot> SAVED = new java.util.HashMap<>();
    public static void capture(ServerPlayer p) {
        if (SAVED.containsKey(p.getUUID())) return;
        var items = new java.util.ArrayList<ItemStack>();
        for (int i=0;i<p.getInventory().getContainerSize();i++)items.add(p.getInventory().getItem(i).copy());
        SAVED.put(p.getUUID(),new Snapshot(items,p.getActiveEffects().stream().map(MobEffectInstance::new).toList(),
                p.gameMode.getGameModeForPlayer(),p.getHealth(),p.getFoodData().getFoodLevel(),p.getFoodData().getSaturationLevel(),
                p.totalExperience,p.experienceLevel,p.experienceProgress,p.getTeam()==null?null:p.getTeam().getName()));
    }
    public static void restore(ServerPlayer p) {
        Snapshot s=SAVED.remove(p.getUUID()); if(s==null)return;
        p.setCamera(p); p.setGameMode(s.mode); p.getInventory().clearContent();
        for(int i=0;i<s.items.size();i++)p.getInventory().setItem(i,s.items.get(i));
        p.removeAllEffects(); s.effects.forEach(p::addEffect); p.setHealth(Math.min(s.health,p.getMaxHealth()));
        p.getFoodData().setFoodLevel(s.food);p.getFoodData().setSaturation(s.saturation);
        p.totalExperience=s.xp;p.experienceLevel=s.level;p.experienceProgress=s.progress;
        var board=p.level().getServer().getScoreboard();board.removePlayerFromTeam(p.getScoreboardName());
        if(s.team!=null && board.getPlayerTeam(s.team)!=null)board.addPlayerToTeam(p.getScoreboardName(),board.getPlayerTeam(s.team));
        p.containerMenu.broadcastChanges();p.refreshTabListName();
    }
    public static void clear(){SAVED.clear();}
}
