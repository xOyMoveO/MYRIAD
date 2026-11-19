package com.myriad.platform;

import com.myriad.player.PlayerDataProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.world.InteractionResult;

/** Explicit server hooks. Movement modifiers run before native Player.tick; post work observes the connection's restored accepted position. */
public final class ServerCallbacks {
    private ServerCallbacks() { }
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(PlayerDataProvider::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(PlayerDataProvider::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(PlayerDataProvider::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(PlayerDataProvider::onServerStopped);
        ServerTickEvents.START_SERVER_TICK.register(com.myriad.character.skills.keheng.KehengTimeFields::clock);
        ServerTickEvents.END_SERVER_TICK.register(PlayerDataProvider::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(com.myriad.game.TeamSignals::tick);
        ServerTickEvents.END_SERVER_TICK.register(com.myriad.combat.ControlStatusSystem::tick);
        ServerTickEvents.END_SERVER_TICK.register(com.myriad.combat.DeathRecaps::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            com.myriad.game.TeamSignals.clear();
            com.myriad.combat.ControlStatusSystem.clearAll();
            com.myriad.combat.DeathRecaps.clearAll();
        });
        ServerPlayerEvents.LEAVE.register(player -> {
            com.myriad.combat.ControlStatusSystem.forget(player.getUUID());
            com.myriad.combat.DeathRecaps.forget(player.getUUID());
        });
        ServerPlayerEvents.JOIN.register(PlayerDataProvider::onPlayerLoggedIn);
        ServerPlayerEvents.LEAVE.register(player -> { PlayerDataProvider.onPlayerLoggedOut(player);com.myriad.character.ability.SkillChargeSystem.logout(player); });
        ServerPlayerEvents.COPY_FROM.register((original,player,alive) -> PlayerDataProvider.onPlayerClone(original,player));
        ServerEntityEvents.ALLOW_LOAD.register(com.myriad.game.ModEntityTracker::allowLoad);
        ServerEntityEvents.ENTITY_LOAD.register(com.myriad.player.RetiredRuneCleanup::onJoin);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity,source,amount) -> !PlayerDataProvider.onLivingDeath(entity,source));
        CommandRegistrationCallback.EVENT.register((dispatcher,context,selection) -> PlayerDataProvider.onRegisterCommands(dispatcher));
        AttackEntityCallback.EVENT.register((player,level,hand,target,hit) -> PlayerDataProvider.onPlayerAttack(player,target)?InteractionResult.FAIL:InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player,level,hand,target,hit) -> PlayerDataProvider.onEntityInteract(player,target)?InteractionResult.FAIL:InteractionResult.PASS);
        UseItemCallback.EVENT.register((player,level,hand) -> { PlayerDataProvider.onRightClickItem(player,player.getItemInHand(hand));return InteractionResult.PASS; });
        UseBlockCallback.EVENT.register((player,level,hand,hit) -> PlayerDataProvider.onRightClickBlock(player,player.getItemInHand(hand))?InteractionResult.FAIL:InteractionResult.PASS);
    }
    public static void beforePlayer(net.minecraft.server.level.ServerPlayer player) {
        com.myriad.character.skills.keheng.KehengTimeFields.movement(player);
        com.myriad.character.ability.SkillChargeSystem.beforeMove(player);
        com.myriad.character.skills.yike.YikeStance.beforeMove(player);
    }
}
