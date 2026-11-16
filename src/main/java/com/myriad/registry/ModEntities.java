package com.myriad.registry;

import com.myriad.Myriad;
import com.myriad.game.DefenseTowerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    private ModEntities() { }
    /** Forces synchronous registration of this class's static entries during mod initialization. */
    public static void initialize() { }
    private static <T extends net.minecraft.world.entity.Entity> java.util.function.Supplier<EntityType<T>> register(String name, java.util.function.Function<net.minecraft.resources.Identifier, EntityType<T>> factory) {
        var id = net.minecraft.resources.Identifier.fromNamespaceAndPath(Myriad.MODID, name);
        EntityType<T> type = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE, id, factory.apply(id));
        return () -> type;
    }
    public static final java.util.function.Supplier<EntityType<DefenseTowerEntity>> BASE_CRYSTAL=
            register("base_crystal",id->EntityType.Builder.of(DefenseTowerEntity::new,MobCategory.MISC)
                    .sized(4F,5F).clientTrackingRange(10).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE,id)));
    public static final java.util.function.Supplier<EntityType<com.myriad.game.LaneMinionEntity>> LANE_MINION =
            register("lane_minion", id -> EntityType.Builder
                    .of(com.myriad.game.LaneMinionEntity::new, MobCategory.MISC).sized(.85F,1.55F)
                    .clientTrackingRange(10).updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,id)));
    public static final java.util.function.Supplier<EntityType<DefenseTowerEntity>> DEFENSE_TOWER =
            register("defense_tower", id -> EntityType.Builder
                    .of(DefenseTowerEntity::new, MobCategory.MISC).sized(3.0F, 8.0F)
                    .clientTrackingRange(10).fireImmune()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final java.util.function.Supplier<EntityType<com.myriad.game.LaneMinionEntity>> SIEGE_MINION =
            register("siege_minion", id -> EntityType.Builder
                    .of(com.myriad.game.LaneMinionEntity::new, MobCategory.MISC).sized(1.5F,2F)
                    .clientTrackingRange(10).updateInterval(1).build(ResourceKey.create(Registries.ENTITY_TYPE,id)));
}
