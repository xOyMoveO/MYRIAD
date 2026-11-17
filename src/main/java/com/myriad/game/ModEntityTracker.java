package com.myriad.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/** 为模组临时实体添加持久标记，并在新一局开启前统一回收。 */
public final class ModEntityTracker {
    public static final String GENERATED_TAG = "myriad_generated";

    private ModEntityTracker() {}

    /** Runtime snapshots recreate map combatants; never also load their stale saved proxies. */
    public static boolean allowLoad(Entity entity, net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.EntitySpawnReason reason, boolean loadedFromDisk) {
        return !loadedFromDisk || !entity.entityTags().contains(GENERATED_TAG);
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
