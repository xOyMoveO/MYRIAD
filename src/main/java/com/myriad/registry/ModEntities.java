package com.myriad.registry;

import com.myriad.Myriad;
import com.myriad.game.DefenseTowerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() { }
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Myriad.MODID);
    public static final DeferredHolder<EntityType<?>,EntityType<DefenseTowerEntity>> BASE_CRYSTAL=
            ENTITIES.register("base_crystal",id->EntityType.Builder.of(DefenseTowerEntity::new,MobCategory.MISC)
                    .sized(4F,5F).clientTrackingRange(10).fireImmune().build(ResourceKey.create(Registries.ENTITY_TYPE,id)));
    public static final DeferredHolder<EntityType<?>, EntityType<com.myriad.game.LaneMinionEntity>> LANE_MINION =
            ENTITIES.register("lane_minion", id -> EntityType.Builder
                    .of(com.myriad.game.LaneMinionEntity::new, MobCategory.MISC).sized(.85F,1.55F)
                    .clientTrackingRange(10).updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,id)));
    public static final DeferredHolder<EntityType<?>, EntityType<DefenseTowerEntity>> DEFENSE_TOWER =
            ENTITIES.register("defense_tower", id -> EntityType.Builder
                    .of(DefenseTowerEntity::new, MobCategory.MISC).sized(3.0F, 8.0F)
                    .clientTrackingRange(10).fireImmune()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredHolder<EntityType<?>, EntityType<com.myriad.game.LaneMinionEntity>> SIEGE_MINION =
            ENTITIES.register("siege_minion", id -> EntityType.Builder
                    .of(com.myriad.game.LaneMinionEntity::new, MobCategory.MISC).sized(1.5F,2F)
                    .clientTrackingRange(10).updateInterval(1).build(ResourceKey.create(Registries.ENTITY_TYPE,id)));
}
