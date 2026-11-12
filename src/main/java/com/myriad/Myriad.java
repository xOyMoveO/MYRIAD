package com.myriad;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.entity.EntityTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import com.myriad.network.NetworkSetup;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModBlocks;
import com.myriad.registry.ModCreativeTabs;
import com.myriad.registry.ModItems;
import com.myriad.registry.ModParticles;
import com.myriad.registry.ModSounds;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Myriad.MODID)
public class Myriad {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "myriad";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Myriad(IEventBus modEventBus, ModContainer modContainer) {
        // Register the Deferred Registers to the mod event bus
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        com.myriad.game.CombatChunks.TYPES.register(modEventBus);
        if (Boolean.getBoolean("myriad.combatTests")) com.myriad.game.CombatRegressionTests.FUNCTIONS.register(modEventBus);
        com.myriad.registry.ModEntities.ENTITIES.register(modEventBus);
        modEventBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
                event.put(com.myriad.registry.ModEntities.BASE_CRYSTAL.get(),com.myriad.game.DefenseTowerEntity.createAttributes().build()));
        modEventBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
                event.put(com.myriad.registry.ModEntities.SIEGE_MINION.get(), com.myriad.game.LaneMinionEntity.createAttributes().build()));
        modEventBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
                event.put(com.myriad.registry.ModEntities.LANE_MINION.get(), com.myriad.game.LaneMinionEntity.createAttributes().build()));
        modEventBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
                event.put(com.myriad.registry.ModEntities.DEFENSE_TOWER.get(),
                        com.myriad.game.DefenseTowerEntity.createAttributes().build()));
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);

        // 玩家数据 Attachment
        PlayerDataProvider.ATTACHMENTS.register(modEventBus);
        // 网络包注册
        modEventBus.addListener(NetworkSetup::register);
        // 自定义属性挂到玩家身上
        modEventBus.addListener(Myriad::onModifyAttributes);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

/** 将自定义属性加入玩家属性表 */
    private static void onModifyAttributes(EntityAttributeModificationEvent event) {
        var attributes = java.util.List.<net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, ?>>of(
                ModAttributes.PHYSICAL_ATTACK,
                ModAttributes.SPELL_POWER,
                ModAttributes.MAGIC_RESISTANCE,
                ModAttributes.PHYSICAL_DEFENSE,
                ModAttributes.CRIT_CHANCE,
                ModAttributes.CRIT_DAMAGE,
                ModAttributes.PHYSICAL_LIFESTEAL,
                ModAttributes.TENACITY,
                ModAttributes.FLAT_ARMOR_PEN,
                ModAttributes.FLAT_MAGIC_PEN,
                ModAttributes.PERCENT_ARMOR_PEN,
                ModAttributes.PERCENT_MAGIC_PEN,
                ModAttributes.ATTACK_SPEED,
                ModAttributes.MAX_MANA,
                ModAttributes.MANA_REGEN,
                ModAttributes.COOLDOWN_REDUCTION,
                ModAttributes.HEALTH_REGEN,
                ModAttributes.SPELL_LIFESTEAL);
        for (var holder : attributes) {
            if (!event.has(EntityTypes.PLAYER, holder)) {
                event.add(EntityTypes.PLAYER, holder);
            }
        }
        // 野怪载体与训练假人需要实际持有模组双防属性，伤害公式和客户端同步才能读取。
        var defendedTypes = java.util.List.of(
                EntityTypes.RABBIT, EntityTypes.PIGLIN, EntityTypes.FOX, EntityTypes.WOLF,
                EntityTypes.FROG, EntityTypes.TURTLE, EntityTypes.PIG, EntityTypes.DROWNED,
                EntityTypes.CAVE_SPIDER, EntityTypes.RAVAGER, EntityTypes.HOGLIN,
                EntityTypes.PHANTOM, EntityTypes.IRON_GOLEM, EntityTypes.VILLAGER,
                EntityTypes.GUARDIAN, EntityTypes.ELDER_GUARDIAN, EntityTypes.HUSK, EntityTypes.PILLAGER);
        for (var type : defendedTypes) {
            if (!event.has(type, ModAttributes.PHYSICAL_DEFENSE)) {
                event.add(type, ModAttributes.PHYSICAL_DEFENSE);
            }
            if (!event.has(type, ModAttributes.MAGIC_RESISTANCE)) {
                event.add(type, ModAttributes.MAGIC_RESISTANCE);
            }
        }
    }
}
