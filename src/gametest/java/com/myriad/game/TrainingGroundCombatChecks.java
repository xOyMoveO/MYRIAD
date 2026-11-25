package com.myriad.game;

import com.mojang.authlib.GameProfile;
import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.MeleeAssistTargeting;
import com.myriad.combat.SkillTargets;
import com.myriad.player.PlayerDataProvider;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class TrainingGroundCombatChecks {
    /** Run on the synthetic dedicated fixture: vanilla GameTestServer omits modded dimensions. */
    static void run(MinecraftServer server, BiConsumer<Boolean, String> check) {
        boolean enabled = ModState.isEnabled();
        boolean legacy = TrainingMode.isEnabled();
        boolean noCooldown = TrainingMode.isNoCooldown();
        boolean noMana = TrainingMode.isNoMana();
        boolean noGold = TrainingMode.isNoGoldCost();
        boolean createdManager = GameManager.get() == null;
        if (createdManager) GameManager.create();
        var manager = GameManager.get();
        var players = new ArrayList<ServerPlayer>();
        var channels = new ArrayList<EmbeddedChannel>();
        try {
            ModState.setEnabled(true);
            TrainingMode.setEnabled(false);
            manager.setServer(server);
            for (int i = 0; i < MatchArena.active().teamSize * 2 + 2; i++)
                players.add(player(server, channels, i));
            var actor = players.get(0);
            var partner = players.get(1);
            var entrants = players.subList(2, players.size());
            var official = entrants.getFirst();
            PlayerDataProvider.onPlayerLoggedIn(partner);
            PlayerDataProvider.chooseCharacter(partner, CharacterType.SHUO);
            PlayerDataProvider.get(partner).setGold(213);
            PlayerDataProvider.get(actor).setGold(137);
            enter(check, actor);
            PlayerDataProvider.chooseCharacter(actor, CharacterType.TIANXING);
            var practice = PlayerDataProvider.get(actor);
            var guard = FirstGroupSkills.node("tianxing", 4);
            practice.setSkillLevel(guard.getId(), 1);
            practice.setCooldown(guard.getId(), 80);
            TrainingActions.apply(actor, "nocooldown", true);
            TrainingActions.apply(actor, "nomana", true);
            TrainingActions.apply(actor, "nogold", true);
            TrainingActions.apply(actor, "dummy", true);
            var dummy = actor.level().getEntitiesOfClass(Villager.class, actor.getBoundingBox().inflate(6),
                    v -> JiarenManager.isJiaren(v.getUUID()) && JiarenManager.ownedBy(actor, v)).getFirst();

            check.accept(manager.startQueuedMatch(server, entrants), "Fixture roster opens official draft while the visitor practises");
            var roles = CharacterType.playableValues();
            for (int i = 0; i < entrants.size(); i++) {
                var picker = server.getPlayerList().getPlayer(manager.queueCurrentPicker());
                check.accept(manager.queueLock(picker, roles[i].getId()), "Official seat can lock its role");
            }
            manager.tick(server, manager.queuePreparationDeadline());
            check.accept(manager.getPhase() == GameManager.Phase.PLAYING && PlayerDataProvider.get(official).getGold() == 500,
                    "Official match starts and pays its own participants");
            check.accept(TrainingGround.isTraining(actor) && dummy.isAlive() && TrainingMode.isNoCooldown(actor)
                    && practice == PlayerDataProvider.get(actor), "Starting a match preserves training loadout, options and dummy");
            check.accept(!TrainingGround.enter(official) && !TrainingMode.isNoCooldown(official)
                    && !TrainingMode.isNoMana(official) && !TrainingMode.isNoGoldCost(official),
                    "Official players cannot enter training or inherit its costs");

            enter(check, partner);
            PlayerDataProvider.chooseCharacter(partner, CharacterType.TIANXING);
            check.accept(!TrainingMode.isNoMana(partner) && !TrainingMode.isNoCooldown(partner) && !TrainingMode.isNoGoldCost(partner),
                    "A second visitor starts with independent practice settings");
            practice.setMana(0);
            SkillSystem.cast(actor, guard.slot());
            check.accept(practice.getBuff(guard.getId()) > 0 && practice.getCooldown(guard.getId()) == 0 && practice.getMana() == 0,
                    "Ordinary skill input spends neither mana nor cooldown only for its opted-in owner");
            practice.setGold(0);
            check.accept(FirstGroupGrowth.buy(actor, practice, "wisdom") && practice.getGold() == 0
                    && !FirstGroupGrowth.buy(partner, PlayerDataProvider.get(partner), "wisdom"),
                    "A free practice purchase cannot subsidize another player");

            var other = PlayerDataProvider.get(partner);
            var before = partner.getHealth();
            check.accept(!SkillTargets.enemy(actor, partner) && !SkillTargets.enemy(actor, official),
                    "Visitors start neutral and cannot target an official player in another dimension");
            partner.hurtServer(partner.level(), partner.damageSources().playerAttack(actor), 16);
            check.accept(partner.getHealth() == before, "The raw damage event rejects an unconsented vanilla attack");
            TrainingActions.apply(actor, "sparring", true);
            check.accept(!SkillTargets.enemy(actor, partner) && !SkillTargets.enemy(partner, actor), "One-sided consent is ineffective");
            check.accept(!preselectsAheadOfBlock(actor, partner),
                    "One-sided consent preserves the native block click even when the other player is in assist range");
            TrainingActions.apply(partner, "sparring", true);
            check.accept(SkillTargets.enemy(actor, partner) && SkillTargets.enemy(partner, actor)
                    && practice.getFaction() == other.getFaction(), "Mutual consent enables both directions, even for the same practice faction");
            check.accept(preselectsAheadOfBlock(actor, partner), "Mutual consent enables assist for a graze ahead of the block");
            partner.damageCooldownTime = 0;
            DamageCalc.dealSkillDamage(actor, partner, 35, DamageCalc.Kind.TRUE, 0);
            check.accept(partner.getHealth() < before, "Consensual skill damage reaches the other player's health pool");
            int previousKills = manager.killsOf(actor), previousDeaths = manager.deathsOf(partner);
            var spawn = TrainingGroundMap.SPAWN;
            partner.setPos(spawn.getX() + 8, spawn.getY(), spawn.getZ() + 8);
            partner.setHealth(1);
            other.setMana(0);
            check.accept(PlayerDataProvider.onLivingDeath(partner, partner.damageSources().playerAttack(actor))
                    && TrainingGround.isTraining(partner) && partner.blockPosition().equals(spawn)
                    && partner.getHealth() == partner.getMaxHealth() && other.getMana() > 0
                    && manager.killsOf(actor) == previousKills && manager.deathsOf(partner) == previousDeaths,
                    "Death returns a sparring player to the training spawn with full health, mana and unchanged official K/D");
            TrainingActions.apply(partner, "sparring", false);
            var after = partner.getHealth();
            partner.damageCooldownTime = 0;
            DamageCalc.dealSkillDamage(actor, partner, 35, DamageCalc.Kind.TRUE, 0);
            check.accept(partner.getHealth() == after && !SkillTargets.enemy(actor, partner), "Either player can revoke consent before impact");
            check.accept(!preselectsAheadOfBlock(actor, partner), "Revoked consent restores the native block click");
            check.accept(SkillTargets.enemy(actor, dummy) && !SkillTargets.enemy(partner, dummy),
                    "Only the dummy owner can practise damage against it");
            check.accept(preselectsAheadOfBlock(actor, dummy) && !preselectsAheadOfBlock(partner, dummy),
                    "An owned attack dummy can take over a grazing click and another owner's dummy preserves the block");
            TrainingActions.apply(partner, "remove_dummy", true);
            check.accept(dummy.isAlive(), "A visitor cannot remove another player's nearby dummy");

            manager.resetToLobby(server, true);
            check.accept(TrainingGround.isTraining(actor) && TrainingGround.isTraining(partner)
                    && TrainingMode.isNoCooldown(actor) && dummy.isAlive(), "Ending a match preserves unrelated practice sessions");
            check.accept(TrainingGround.leave(actor) && actor.level() == server.overworld() && !dummy.isAlive()
                    && PlayerDataProvider.get(actor).getGold() == 137 && !TrainingMode.isNoCooldown(actor),
                    "Leaving restores the original wallet and retires temporary options and dummies");
            check.accept(TrainingGround.isTraining(partner), "Another visitor remains in practice");
            TrainingActions.apply(partner, "gold", true);
            TrainingActions.apply(partner, "nomana", true);
            TrainingActions.apply(partner, "sparring", true);
            TrainingActions.apply(partner, "dummy", true);
            var partnerDummy = partner.level().getEntitiesOfClass(Villager.class, partner.getBoundingBox().inflate(6),
                    v -> JiarenManager.isJiaren(v.getUUID()) && JiarenManager.ownedBy(partner, v)).getFirst();
            PlayerDataProvider.onPlayerLoggedOut(partner);
            check.accept(!TrainingGround.isTraining(partner) && !partnerDummy.isAlive(), "Disconnect immediately removes session and owned dummy");
            PlayerDataProvider.onPlayerLoggedIn(partner);
            check.accept(partner.level() == server.overworld() && PlayerDataProvider.get(partner).getGold() == 213
                    && "shuo".equals(PlayerDataProvider.get(partner).getCharacterId())
                    && !TrainingMode.isNoMana(partner) && !TrainingGround.isSparring(partner),
                    "Login restores the pre-training role and purse in the lobby with personal options cleared");
        } finally {
            if (manager.getPhase() != GameManager.Phase.WAITING) manager.resetToLobby(server, false);
            for (var p : players) {
                if (TrainingGround.isTraining(p)) TrainingGround.leave(p);
                server.getPlayerList().getPlayers().remove(p);
                server.getPlayerList().getPlayersByUUID().remove(p.getUUID());
                p.level().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            }
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
            if (createdManager) GameManager.destroy();
            TrainingMode.setEnabled(legacy);
            TrainingMode.setNoCooldown(noCooldown);
            TrainingMode.setNoMana(noMana);
            TrainingMode.setNoGoldCost(noGold);
            ModState.setEnabled(enabled);
        }
        System.out.println("[MYRIAD-TRAINING-GROUND] PASS: active official roster, independent resources, mutual sparring, training respawn, owned dummies, exit and reconnect");
    }

    private static void enter(BiConsumer<Boolean, String> check, ServerPlayer player) {
        check.accept(TrainingGround.enter(player) && player.level().dimension().equals(TrainingGroundMap.KEY),
                "Player enters the generated practice dimension");
        player.hasChangedDimension();
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
    }

    private static boolean preselectsAheadOfBlock(ServerPlayer attacker, LivingEntity target) {
        var level = attacker.level();
        var origin = new Vec3(.5, 100, .5);
        var obstruction = new BlockPos(0, 101, 2);
        var originalBlock = level.getBlockState(obstruction);
        var oldAttackerPos = attacker.position();
        var oldTargetPos = target.position();
        float oldYaw = attacker.getYRot();
        float oldPitch = attacker.getXRot();
        try {
            attacker.setPos(origin);
            attacker.setYRot(0);
            attacker.setXRot(0);
            target.setPos(origin.add(target.getBbWidth() / 2 + .08, 0, 1.2));
            level.setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            if (attacker.pick(attacker.entityInteractionRange(), 1, false).getType() != HitResult.Type.BLOCK) {
                throw new IllegalStateException("Expected a native block behind the graze fixture");
            }
            return MeleeAssistTargeting.find(attacker,
                    candidate -> TrainingGround.mayPreselectPracticeTarget(attacker, candidate)) == target;
        } finally {
            level.setBlockAndUpdate(obstruction, originalBlock);
            attacker.setPos(oldAttackerPos);
            attacker.setYRot(oldYaw);
            attacker.setXRot(oldPitch);
            target.setPos(oldTargetPos);
        }
    }

    private static ServerPlayer player(MinecraftServer server, List<EmbeddedChannel> channels, int index) {
        var profile = new GameProfile(UUID.randomUUID(), "TrainingTest" + index);
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channels.add(new EmbeddedChannel(connection));
        player.connection = new ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {}
        };
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.SURVIVAL);
        server.getPlayerList().getPlayers().add(player);
        server.getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
        server.overworld().addNewPlayer(player);
        return player;
    }
}
