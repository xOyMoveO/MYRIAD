package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.LevelSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.player.PlayerHealthSystem;
import com.myriad.registry.ModAttributes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** Both the practice ground and the older operator sandbox validate every panel request on the server. */
public final class TrainingActions {
    private TrainingActions() {}

    public static void apply(ServerPlayer player, String action, boolean value) {
        if (!ModState.isEnabled()) {
            player.sendSystemMessage(Component.translatable("screen.myriad.train.require_mod"));
            return;
        }
        if (action.equals("enter")) {
            TrainingGround.enter(player);
            return;
        }
        if (action.equals("leave")) {
            TrainingGround.leave(player);
            return;
        }
        var personal = TrainingGround.options(player);
        if (action.equals("enabled")) {
            // Older operator panels use this separately from the public training ground entrance.
            var manager = GameManager.get();
            if (personal != null || !player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) return;
            if (manager != null && manager.getPhase() != GameManager.Phase.WAITING) {
                player.sendSystemMessage(Component.translatable("screen.myriad.train.match_running"));
                return;
            }
            TrainingMode.setEnabled(value);
            syncSandbox(player);
            return;
        }
        if (!TrainingMode.isEnabled(player)) {
            player.sendSystemMessage(Component.translatable("screen.myriad.train.require_training"));
            return;
        }
        var data = PlayerDataProvider.get(player);
        boolean option = action.equals("nocooldown") || action.equals("nomana") || action.equals("nogold");
        if (personal == null && option && !player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)) return;
        if (action.startsWith("level:")) {
            int level;
            try { level = Integer.parseInt(action.substring(6)); }
            catch (NumberFormatException e) { return; }
            if (level < 1 || level > LevelSystem.maxLevel(data) || !FirstGroupSkills.active(data.getCharacterId())) return;
            float health = player.getHealth(), mana = data.getMana();
            if (level < data.getLevel()) {
                AbilityRuntime.clear(player);
                data.setLevel(level);
                data.resetForTrainingLevel();
                data.clearSkillCooldowns();
            } else {
                data.setLevel(level);
                int spent = data.spentSkillPoints();
                data.setSkillPoints(Math.max(0, level - spent));
            }
            data.setXp(0);
            LevelSystem.applyLevelAttributes(player, level);
            PlayerHealthSystem.recalculate(player);
            player.setHealth(Math.min(health, player.getMaxHealth()));
            data.setMana((float) Math.min(mana, player.getAttributeValue(ModAttributes.MAX_MANA)));
        } else switch (action) {
            case "level" -> LevelSystem.addXp(player, data, LevelSystem.xpToNext(data.getLevel()));
            case "gold" -> data.addWalletGold(value ? 10000 : 1000);
            case "refresh" -> data.clearSkillCooldowns();
            case "health" -> PlayerHealthSystem.fill(player);
            case "mana" -> data.setMana((float) player.getAttributeValue(ModAttributes.MAX_MANA));
            case "restore" -> {
                PlayerHealthSystem.fill(player);
                data.setMana((float) player.getAttributeValue(ModAttributes.MAX_MANA));
            }
            case "dummy" -> JiarenManager.create(player);
            case "friendly_dummy" -> JiarenManager.createFriendly(player);
            case "remove_dummy" -> JiarenManager.removeNearby(player);
            case "sparring" -> {
                if (personal == null) return;
                TrainingGround.setSparring(player, value);
                QueueLobby.sync(player);
            }
            case "faction_guardian", "faction_destruction" -> {
                var faction = action.equals("faction_guardian") ? Faction.GUARDIAN : Faction.DESTRUCTION;
                if (personal != null) data.setFaction(faction);
                else PlayerDataProvider.assignTeam(player, faction);
            }
            case "nocooldown" -> {
                if (personal != null) personal.noCooldown = value;
                else TrainingMode.setNoCooldown(value);
                if (value) {
                    data.clearSkillCooldowns();
                    if (personal == null) for (var other : player.level().getServer().getPlayerList().getPlayers())
                        if (!TrainingGround.isTraining(other)) PlayerDataProvider.get(other).clearSkillCooldowns();
                }
            }
            case "nomana" -> {
                if (personal != null) personal.noMana = value;
                else TrainingMode.setNoMana(value);
                if (value) data.setMana((float) player.getAttributeValue(ModAttributes.MAX_MANA));
            }
            case "nogold" -> {
                if (personal != null) personal.noGoldCost = value;
                else TrainingMode.setNoGoldCost(value);
            }
            default -> { return; }
        }
        if (personal == null && option) syncSandbox(player);
        else PlayerDataProvider.syncToClient(player);
    }

    private static void syncSandbox(ServerPlayer source) {
        for (var player : source.level().getServer().getPlayerList().getPlayers()) {
            if (player != source && !TrainingGround.isTraining(player)) PlayerDataProvider.syncToClient(player);
        }
        PlayerDataProvider.syncToClient(source);
    }
}
