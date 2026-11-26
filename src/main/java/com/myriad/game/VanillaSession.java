package com.myriad.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myriad.player.PlayerNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** The player's ordinary loadout is saved alongside the replacement loadout until it is returned. */
public final class VanillaSession {
    private record Snapshot(String session, List<ItemStack> items, List<MobEffectInstance> effects, int slot, int mode,
            float health, int food, float saturation, int xp, int level, float progress, Optional<String> team) {
        private static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("session").forGetter(Snapshot::session),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(Snapshot::items),
                MobEffectInstance.CODEC.listOf().fieldOf("effects").forGetter(Snapshot::effects),
                Codec.INT.fieldOf("slot").forGetter(Snapshot::slot),
                Codec.INT.fieldOf("mode").forGetter(Snapshot::mode),
                Codec.FLOAT.fieldOf("health").forGetter(Snapshot::health),
                Codec.INT.fieldOf("food").forGetter(Snapshot::food),
                Codec.FLOAT.fieldOf("saturation").forGetter(Snapshot::saturation),
                Codec.INT.fieldOf("xp").forGetter(Snapshot::xp),
                Codec.INT.fieldOf("level").forGetter(Snapshot::level),
                Codec.FLOAT.fieldOf("progress").forGetter(Snapshot::progress),
                Codec.STRING.optionalFieldOf("team").forGetter(Snapshot::team)
        ).apply(instance, Snapshot::new));
    }

    private static final AttachmentType<Snapshot> SAVED = AttachmentRegistry.create(
            Identifier.parse("myriad:vanilla_session"), builder -> builder.persistent(Snapshot.CODEC).copyOnDeath());

    private VanillaSession() {}

    /** Register the persistent type before player data is decoded. */
    public static void initialize() {}

    public static void capture(ServerPlayer player) {
        var target = (AttachmentTarget) player;
        Snapshot previous = target.getAttached(SAVED);
        if (previous != null) {
            if (previous.session().equals(ModState.session())) return;
            // This player was offline for both closing the previous session and opening this one.
            restore(player);
        }
        // Closing even the personal inventory returns its 2x2 crafting inputs and carried stack.
        player.closeContainer();
        var inventory = player.getInventory();
        var items = new ArrayList<ItemStack>(inventory.getContainerSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) items.add(inventory.getItem(i).copy());
        var effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        var team = player.getTeam();
        target.setAttached(SAVED, new Snapshot(ModState.session(), List.copyOf(items), effects, inventory.getSelectedSlot(),
                player.gameMode.getGameModeForPlayer().getId(), player.getHealth(), player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel(), player.totalExperience, player.experienceLevel,
                player.experienceProgress, team == null ? Optional.empty() : Optional.of(team.getName())));
    }

    public static void restore(ServerPlayer player) {
        var target = (AttachmentTarget) player;
        Snapshot saved = target.getAttached(SAVED);
        if (saved == null) return;
        com.myriad.player.PlayerDataProvider.set(player, new com.myriad.player.PlayerData());
        com.myriad.character.CharacterType.applyVanillaAttributes(player);
        player.setCamera(player);
        player.setGameMode(GameType.byId(saved.mode()));
        var inventory = player.getInventory();
        inventory.clearContent();
        for (int i = 0; i < saved.items().size() && i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, saved.items().get(i).copy());
        }
        inventory.setSelectedSlot(saved.slot());
        player.removeAllEffects();
        for (var effect : saved.effects()) player.addEffect(new MobEffectInstance(effect));
        player.setHealth(Math.min(saved.health(), player.getMaxHealth()));
        player.getFoodData().setFoodLevel(saved.food());
        player.getFoodData().setSaturation(saved.saturation());
        player.totalExperience = saved.xp();
        player.experienceLevel = saved.level();
        player.experienceProgress = saved.progress();
        var board = player.level().getServer().getScoreboard();
        board.removePlayerFromTeam(player.getScoreboardName());
        saved.team().map(board::getPlayerTeam).ifPresent(team -> board.addPlayerToTeam(player.getScoreboardName(), team));
        player.containerMenu.broadcastChanges();
        PlayerNames.refresh(player);
        target.removeAttached(SAVED);
    }
}
