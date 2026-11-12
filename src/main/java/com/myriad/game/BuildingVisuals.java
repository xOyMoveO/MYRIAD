package com.myriad.game;

import com.mojang.math.Transformation;
import com.myriad.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Model files are normalized to 0..16; these scales are dimensions in world blocks. */
public final class BuildingVisuals {
    private static final String LAYOUT_TAG = "myriad_building_layout";
    private static final String KEY_PREFIX = "myriad_building:";
    public record Placement(Vec3 base, Faction faction, int tier) { }
    private BuildingVisuals() { }

    /** Reconcile loaded map previews by persistent key, including after chunk reloads. */
    public static void syncLayout(ServerLevel level, java.util.Map<String, Placement> desired) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<net.minecraft.world.entity.Entity> stale = new java.util.ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if (entity == null || entity.isRemoved() || !entity.entityTags().contains(LAYOUT_TAG)) continue;
            String key = entity.entityTags().stream().filter(t -> t.startsWith(KEY_PREFIX))
                    .map(t -> t.substring(KEY_PREFIX.length())).findFirst().orElse("");
            Placement placement = desired.get(key);
            if (placement == null || entity.position().distanceToSqr(placement.base()) > .001
                    || !seen.add(key)) stale.add(entity);
        }
        // getAllEntities is a live lookup: discard mutates its backing collection.
        // Finish scanning before removals or new display creation.
        stale.forEach(net.minecraft.world.entity.Entity::discard);
        for (var entry : desired.entrySet()) {
            Placement p = entry.getValue();
            if (seen.contains(entry.getKey()) || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(p.base()))) continue;
            ItemDisplay display = p.tier() == 0 ? crystal(level, p.base(), p.faction())
                    : tower(level, p.base(), p.faction(), p.tier());
            if (display != null) {
                display.addTag(LAYOUT_TAG);
                display.addTag(KEY_PREFIX + entry.getKey());
            }
        }
    }
    public static ItemDisplay tower(ServerLevel level, Vec3 base, Faction faction, int tier) {
        return spawn(level, base, ModItems.defenseTower(faction, tier), 3, 8);
    }
    public static ItemDisplay crystal(ServerLevel level, Vec3 base, Faction faction) {
        return spawn(level, base, faction == Faction.XUANXIAO
                ? ModItems.CRYSTAL_RED.get() : ModItems.CRYSTAL_BLUE.get(), 4, 5);
    }
    private static ItemDisplay spawn(ServerLevel level, Vec3 base, Item item, float width, float height) {
        ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (display == null) return null;
        display.getSlot(0).set(new ItemStack(item));
        display.setItemTransform(ItemDisplayContext.FIXED);
        display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        display.setPos(base.x, base.y, base.z);
        display.setTransformation(new Transformation(new Vector3f(0, height / 2, 0),
                new Quaternionf(), new Vector3f(width, height, width), new Quaternionf()));
        level.addFreshEntity(ModEntityTracker.markGenerated(display));
        return display;
    }
}
