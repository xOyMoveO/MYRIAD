package com.myriad;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.entity.EntityTypes;
import com.myriad.registry.*;

public final class Myriad implements ModInitializer {
    public static final String MODID="myriad";
    public static final Logger LOGGER=LogUtils.getLogger();
    @Override public void onInitialize() {
        ModBlocks.initialize();
        ModItems.initialize();
        ModEntities.initialize();
        ModAttributes.initialize();
        ModSounds.initialize();
        ModParticles.initialize();
        ModCreativeTabs.initialize();
        com.myriad.game.CombatChunks.initialize();
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(ModEntities.BASE_CRYSTAL.get(),com.myriad.game.DefenseTowerEntity.createAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(ModEntities.DEFENSE_TOWER.get(),com.myriad.game.DefenseTowerEntity.createAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(ModEntities.SIEGE_MINION.get(),com.myriad.game.LaneMinionEntity.createAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(ModEntities.LANE_MINION.get(),com.myriad.game.LaneMinionEntity.createAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                ModEntities.CHERRY_RIFT_MONSTER.get(), com.myriad.game.CherryRiftMonsterEntity.createAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.MODIFY.register(Myriad::onModifyAttributes);
        com.myriad.player.PlayerDataProvider.initialize();
        com.myriad.player.EntityState.initialize();
        com.myriad.game.VanillaSession.initialize();
        com.myriad.combat.VanillaArrowTime.initialize();
        com.myriad.game.TrainingGround.initialize();
        com.myriad.game.JiarenManager.initialize();
        com.myriad.network.NetworkSetup.register();
        com.myriad.platform.ServerCallbacks.initialize();
        com.myriad.network.LobbyNetworking.register();
        com.myriad.game.QueueLobby.initialize();
        Config.load();
    }

    private static void onModifyAttributes(net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.ModifyContext context) {
        var attributes = java.util.List.<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>>of(
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
        context.modify(EntityTypes.PLAYER, (type, builder) -> attributes.forEach(builder::add));
        // 野怪载体与训练假人需要实际持有模组双防属性，伤害公式和客户端同步才能读取。
        java.util.List<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>> defendedTypes = java.util.List.of(
                EntityTypes.RABBIT, EntityTypes.PIGLIN, EntityTypes.FOX, EntityTypes.WOLF,
                EntityTypes.FROG, EntityTypes.TURTLE, EntityTypes.PIG, EntityTypes.DROWNED,
                EntityTypes.CAVE_SPIDER, EntityTypes.RAVAGER, EntityTypes.HOGLIN,
                EntityTypes.PHANTOM, EntityTypes.IRON_GOLEM, EntityTypes.VILLAGER,
                EntityTypes.GUARDIAN, EntityTypes.ELDER_GUARDIAN, EntityTypes.HUSK, EntityTypes.PILLAGER);
        context.modify(defendedTypes, (type, builder) -> builder.add(ModAttributes.PHYSICAL_DEFENSE).add(ModAttributes.MAGIC_RESISTANCE));
    }
}
