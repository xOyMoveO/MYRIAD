package com.myriad.game;

import com.myriad.player.EntityState;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** One shared practice dimension with an independent, temporary loadout for every visitor. */
public final class TrainingGround {
    private static final String BEFORE_TRAINING = "myriad_before_training";
    private static final Map<UUID, Options> visitors = new HashMap<>();

    static final class Options {
        boolean noCooldown;
        boolean noMana;
        boolean noGoldCost;
        boolean sparring;
    }

    private TrainingGround() {}

    public static boolean isTraining(ServerLevel level) {
        return level.dimension().equals(TrainingGroundMap.KEY);
    }

    public static boolean isTraining(ServerPlayer player) {
        return isTraining(player.level()) && visitors.containsKey(player.getUUID());
    }

    static Options options(ServerPlayer player) {
        return isTraining(player) ? visitors.get(player.getUUID()) : null;
    }

    public static boolean isSparring(ServerPlayer player) {
        var options = options(player);
        return options != null && options.sparring;
    }

    public static boolean canFight(ServerPlayer player) {
        if (!ModState.isEnabled()) return false;
        if (TrainingMode.isEnabled(player)) return true;
        var match = GameManager.get();
        return match != null && match.getPhase() == GameManager.Phase.PLAYING
                && match.isParticipant(player.getUUID());
    }

    /** Apply at target selection and again at impact, as either player can leave or revoke consent. */
    public static boolean mayInteract(ServerPlayer actor, LivingEntity target) {
        if (actor.level() != target.level()) return false;
        if (!isTraining(actor.level())) {
            var match = GameManager.get();
            if (target instanceof ServerPlayer other && match != null && match.isQueuedMatch())
                return match.isParticipant(actor.getUUID()) == match.isParticipant(other.getUUID());
            return true;
        }
        if (!isTraining(actor)) return false;
        if (actor == target) return true;
        if (target instanceof ServerPlayer opponent) return isSparring(actor) && isSparring(opponent);
        return !JiarenManager.isJiaren(target.getUUID()) || JiarenManager.ownedBy(actor, target);
    }

    public static boolean enter(ServerPlayer player) {
        if (!ModState.isEnabled()) return false;
        if (isTraining(player)) return true;
        var match = GameManager.get();
        if (match != null && match.isParticipant(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.myriad.training_participant"));
            return false;
        }
        var level = player.level().getServer().getLevel(TrainingGroundMap.KEY);
        if (level == null || !TrainingGroundMap.prepare(level)) {
            player.sendSystemMessage(Component.translatable("message.myriad.training_unavailable"));
            return false;
        }
        player.closeContainer();
        var stash = snapshot(player);
        if (!teleportToSpawn(player, level)) return false;
        EntityState.data(player).put(BEFORE_TRAINING, stash);
        visitors.put(player.getUUID(), new Options());
        PlayerDataProvider.clearMatchPlayerState(player);
        PlayerDataProvider.resetForHostSession(player);
        PlayerDataProvider.get(player).setPracticeWallet(true);
        PlayerDataProvider.get(player).setFaction(Faction.GUARDIAN);
        player.setGameMode(GameType.ADVENTURE);
        sync(player);
        return true;
    }

    public static boolean teleportToSpawn(ServerPlayer player, ServerLevel level) {
        var spawn = TrainingGroundMap.SPAWN;
        player.setCamera(player);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.resetFallDistance();
        return player.teleportTo(level, spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5,
                Set.of(), 0, 0, true);
    }

    public static boolean leave(ServerPlayer player) {
        if (!restore(player)) return false;
        sync(player);
        return true;
    }

    public static void leaveForMatch(ServerPlayer player) {
        // The draft is still in the lobby dimension and owns the next state/navigation updates.
        restore(player);
    }

    public static void onLogin(ServerPlayer player) {
        if (hasSnapshot(player) || isTraining(player.level())) restore(player);
    }

    public static void onLogout(ServerPlayer player) {
        // Keep the persistent snapshot until login. The disconnect event can fire after playerdata is saved.
        if (visitors.remove(player.getUUID()) != null) {
            JiarenManager.clearOwned(player);
            TacticalSupplySystem.clearPlayer(player);
        }
    }

    public static void closeAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (hasSnapshot(player) || isTraining(player.level())) restore(player);
        }
        visitors.clear();
    }

    public static void tick(MinecraftServer server) {
        var level = server.getLevel(TrainingGroundMap.KEY);
        if (level == null || visitors.isEmpty()) return;
        for (UUID id : Set.copyOf(visitors.keySet())) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null && !isTraining(player.level())) leave(player);
        }
        TacticalSupplySystem.tick(level);
        VisionSystem.tick(level);
    }

    private static boolean hasSnapshot(ServerPlayer player) {
        return EntityState.data(player).contains(BEFORE_TRAINING);
    }

    private static CompoundTag snapshot(ServerPlayer player) {
        var stash = new CompoundTag();
        var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        stash.store("hero", PlayerData.CODEC, PlayerDataProvider.get(player));
        var items = new ArrayList<ItemStack>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) items.add(inventory.getItem(i));
        stash.store("items", ItemStack.OPTIONAL_CODEC.listOf(), ops, items);
        stash.store("effects", MobEffectInstance.CODEC.listOf(), ops, new ArrayList<>(player.getActiveEffects()));
        stash.putInt("slot", inventory.getSelectedSlot());
        stash.putInt("mode", player.gameMode.getGameModeForPlayer().getId());
        stash.putInt("food", player.getFoodData().getFoodLevel());
        stash.putFloat("saturation", player.getFoodData().getSaturationLevel());
        stash.putFloat("health", player.getHealth());
        stash.putInt("xp", player.totalExperience);
        stash.putInt("level", player.experienceLevel);
        stash.putFloat("progress", player.experienceProgress);
        // A map without a configured lobby returns the visitor to their actual entry location.
        stash.store("dimension", net.minecraft.world.level.Level.RESOURCE_KEY_CODEC, player.level().dimension());
        stash.putDouble("x", player.getX());
        stash.putDouble("y", player.getY());
        stash.putDouble("z", player.getZ());
        stash.putFloat("yaw", player.getYRot());
        stash.putFloat("pitch", player.getXRot());
        return stash;
    }

    private static boolean restore(ServerPlayer player) {
        var tag = EntityState.data(player);
        var previous = tag.getCompound(BEFORE_TRAINING);
        if (previous.isEmpty() && !isTraining(player.level())) return false;
        visitors.remove(player.getUUID());
        JiarenManager.clearOwned(player);
        TacticalSupplySystem.clearPlayer(player);
        PlayerDataProvider.clearMatchPlayerState(player);
        PlayerDataProvider.resetForHostSession(player);
        var server = player.level().getServer();
        var fallback = server.overworld().getRespawnData();
        var pos = fallback.pos();
        var state = previous.orElseGet(CompoundTag::new);
        var destination = state.read("dimension", net.minecraft.world.level.Level.RESOURCE_KEY_CODEC)
                .map(server::getLevel).filter(level -> !isTraining(level)).orElse(server.overworld());
        player.setCamera(player);
        player.teleportTo(destination, state.getDoubleOr("x", pos.getX() + .5), state.getDoubleOr("y", pos.getY()),
                state.getDoubleOr("z", pos.getZ() + .5), Set.of(), state.getFloatOr("yaw", fallback.yaw()),
                state.getFloatOr("pitch", fallback.pitch()), true);
        if (previous.isPresent()) {
            var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var data = state.read("hero", PlayerData.CODEC).orElseGet(PlayerData::new);
            PlayerDataProvider.set(player, data);
            float originalMana = data.getMana();
            PlayerDataProvider.applyChosenCharacter(player, data);
            data.setMana(originalMana);
            player.getInventory().clearContent();
            state.read("items", ItemStack.OPTIONAL_CODEC.listOf(), ops).ifPresent(items -> {
                for (int i = 0; i < Math.min(items.size(), player.getInventory().getContainerSize()); i++)
                    player.getInventory().setItem(i, items.get(i));
            });
            player.getInventory().setSelectedSlot(state.getIntOr("slot", 0));
            player.setGameMode(GameType.byId(state.getIntOr("mode", GameType.ADVENTURE.getId())));
            state.read("effects", MobEffectInstance.CODEC.listOf(), ops).ifPresent(effects -> effects.forEach(player::addEffect));
            player.setHealth(Math.clamp(state.getFloatOr("health", player.getMaxHealth()), 1, player.getMaxHealth()));
            player.getFoodData().setFoodLevel(state.getIntOr("food", 20));
            player.getFoodData().setSaturation(state.getFloatOr("saturation", 5));
            player.totalExperience = state.getIntOr("xp", 0);
            player.experienceLevel = state.getIntOr("level", 0);
            player.experienceProgress = state.getFloatOr("progress", 0);
        }
        tag.remove(BEFORE_TRAINING);
        LobbySystem.teleportToLobby(player);
        player.containerMenu.broadcastChanges();
        return true;
    }

    private static void sync(ServerPlayer player) {
        PlayerDataProvider.syncToClient(player);
        QueueLobby.sync(player);
    }
}
