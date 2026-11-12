package com.myriad.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/** 为模组临时实体添加持久标记，并在新一局开启前统一回收。 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "myriad")
public final class ModEntityTracker {
    public static final String GENERATED_TAG = "myriad_generated";

    private ModEntityTracker() {}

    /** Runtime snapshots recreate map combatants; never also load their stale saved proxies. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onLoad(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk()
                && event.getEntity().entityTags().contains(GENERATED_TAG)) event.setCanceled(true);
    }

    public static <T extends Entity> T markGenerated(T entity) {
        entity.addTag(GENERATED_TAG);
        return entity;
    }

    /** 清除所有维度中由仙凡劫生成的实体；玩家及未标记的原版实体不会受影响。 */
    public static int clearGenerated(MinecraftServer server) {
        int removed = 0;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            java.util.List<Entity> stale = new java.util.ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity != null && !entity.isRemoved() && entity.entityTags().contains(GENERATED_TAG)) {
                    stale.add(entity);
                }
            }
            stale.forEach(Entity::discard);
            removed += stale.size();
        }
        return removed;
    }
}
