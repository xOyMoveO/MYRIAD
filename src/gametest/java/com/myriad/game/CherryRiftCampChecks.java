package com.myriad.game;

import com.myriad.combat.DamageCalc;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

final class CherryRiftCampChecks {
    private CherryRiftCampChecks() {}

    static void run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var config = MatchArena.configPath(server, "myriad_monster_spawns.json");
        var originalMonsters = MonsterManager.snapshot();
        var originalMatch = GameManager.snapshot();
        boolean enabled = ModState.isEnabled();
        boolean training = TrainingMode.isEnabled();
        byte[] originalConfig = null;
        ServerPlayer player = null;
        try {
            if (Files.exists(config)) originalConfig = Files.readAllBytes(config);
            Files.writeString(config, "{\"points\":[]}");
            MonsterManager.reloadArena(server);
            var types = Arrays.stream(MonsterType.values()).filter(type -> type.cherryModel() != null).toList();
            helper.assertTrue(types.size() == 6, "All six map models have live variants");
            helper.assertTrue(!MonsterManager.setSpawnPoint("legacy_boss", BlockPos.ZERO,
                    MonsterType.AZURE_HORN_GUARDIAN, false), "Legacy bosses remain random only");
            BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
            for (int i = 0; i < types.size(); i++) {
                helper.assertTrue(MonsterManager.setSpawnPoint("camp_" + i, origin.offset(i * 8, 0, 0),
                        types.get(i), false), "Each variant can be placed");
            }
            MonsterManager.reloadArena(server);
            var points = MonsterManager.spawnPointInfos();
            helper.assertTrue(points.size() == 6 && points.stream().filter(MonsterManager.SpawnPointInfo::boss)
                    .map(MonsterManager.SpawnPointInfo::typeId).toList().equals(List.of("cherry_rift_crab")),
                    "Fixed boss persists and carries a boss minimap marker");
            var bounds = new AABB(origin).inflate(50);
            var spawnByTime = MonsterManager.class.getDeclaredMethod("spawnInitialConfiguredMonsters",
                    ServerLevel.class, long.class);
            spawnByTime.setAccessible(true);
            for (long[] step : new long[][]{{599, 0}, {600, 3}, {1200, 5}, {7199, 5}, {7200, 6}}) {
                spawnByTime.invoke(null, level, step[0]);
                helper.assertTrue(mobs(level, bounds).size() == step[1], "Correct first spawn at " + step[0]);
            }
            MonsterManager.reloadArena(server);
            var cherryMatch = originalMatch.deepCopy().getAsJsonObject();
            cherryMatch.addProperty("phase", "PLAYING");
            cherryMatch.addProperty("tempo", "CHERRY_V1");
            GameManager.restore(cherryMatch, server);
            for (long[] step : new long[][]{{399,0},{400,3},{799,3},{800,5},{4799,5},{4800,6},{4801,6}}) {
                spawnByTime.invoke(null, level, step[0]);
                helper.assertTrue(mobs(level, bounds).size() == step[1], "Cherry camp population at " + step[0]);
            }
            var cherryMobs = mobs(level, bounds);
            var small = find(cherryMobs, MonsterType.CHERRY_DUSK_FOX);
            var medium = find(cherryMobs, MonsterType.CHERRY_JADE_BEAST);
            MonsterManager.onDeath(small, null);
            MonsterManager.onDeath(medium, null);
            var deadlines = new java.util.HashSet<Long>();
            for (var row : MonsterManager.snapshot().getAsJsonArray("respawns"))
                deadlines.add(row.getAsJsonObject().get("at").getAsLong() - level.getGameTime());
            helper.assertTrue(deadlines.equals(java.util.Set.of(1500L, 2000L))
                    && MatchTempo.CHERRY_V1.respawnSeconds(MonsterType.GOLD_PIG) == 0,
                    "Small and medium death timers and zero-respawn camp");
            small.discard();
            medium.discard();
            MonsterManager.reloadArena(server);
            GameManager.restore(originalMatch, server);
            ModState.setEnabled(true);
            TrainingMode.setEnabled(false);
            helper.assertTrue(MonsterManager.previewFixedSpawnPoints(level) == 0, "Training preview is gated");
            TrainingMode.setEnabled(true);
            helper.assertTrue(MonsterManager.previewFixedSpawnPoints(level) == 6
                    && MonsterManager.previewFixedSpawnPoints(level) == 0, "Preview does not duplicate live camps");
            var live = mobs(level, bounds);
            for (var mob : live) {
                var type = mob.variant();
                helper.assertTrue(mob.isPickable() && mob.isNoAi() && mob.getHealth() > 0
                        && MonsterManager.modHealth(mob) == type.getHealth()
                        && mob.getAttributeValue(Attributes.ATTACK_DAMAGE) == type.getAttack()
                        && mob.getAttributeValue(ModAttributes.PHYSICAL_DEFENSE) == type.getPhysicalDefense()
                        && mob.getBbWidth() == type.cherryModel().width() && mob.getBbHeight() == type.cherryModel().height(),
                        "Model hitbox and live stats for " + type);
            }
            var fox = find(live, MonsterType.CHERRY_DUSK_FOX);
            player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND),
                    player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {}
            };
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            player.setPos(fox.position().add(0, 0, 1));
            var data = PlayerDataProvider.get(player);
            data.setCharacterId("shuo");
            data.setLevel(1);
            int beforeGold = data.getGold();
            DamageCalc.dealDamage(player, fox, 120, DamageCalc.Kind.TRUE);
            float damagedHealth = MonsterManager.modHealth(fox);
            helper.assertTrue(damagedHealth > 0 && damagedHealth < fox.variant().getHealth(), "Actual hit consumes mod health");
            var saved = MonsterManager.snapshot();
            MonsterManager.restore(level, saved);
            fox = find(mobs(level, bounds), MonsterType.CHERRY_DUSK_FOX);
            helper.assertTrue(Math.abs(MonsterManager.modHealth(fox) - damagedHealth) < .01F,
                    "Restore recreates the same model and damaged health");
            DamageCalc.dealDamage(player, fox, 3000, DamageCalc.Kind.TRUE);
            helper.assertTrue(!fox.isAlive() && data.getGold() - beforeGold == GlobalBalance.goldIncome(32),
                    "Real kill awards configured gold once, vanilla health=" + fox.getHealth()
                            + ", mod health=" + MonsterManager.modHealth(fox) + ", gold=" + (data.getGold() - beforeGold));
            var pending = MonsterManager.snapshot().getAsJsonArray("respawns");
            helper.assertTrue(pending.size() == 1 && pending.get(0).getAsJsonObject().get("at").getAsLong()
                    == level.getGameTime() + 90 * 20L && MonsterManager.previewFixedSpawnPoints(level) == 0,
                    "Killed camp schedules a respawn and preview respects the timer");
            System.out.println("[MYRIAD-CHERRY-RIFT] PASS: model variants, fixed camps, timing, training preview, real damage, restore and rewards");
            helper.succeed();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            if (player != null) player.discard();
            try {
                if (originalConfig == null) Files.deleteIfExists(config); else Files.write(config, originalConfig);
                MonsterManager.reloadArena(server);
                GameManager.restore(originalMatch, server);
                MonsterManager.restore(level, originalMonsters);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            } finally {
                TrainingMode.setEnabled(training);
                ModState.setEnabled(enabled);
            }
        }
    }

    private static List<CherryRiftMonsterEntity> mobs(ServerLevel level, AABB area) {
        return level.getEntitiesOfClass(CherryRiftMonsterEntity.class, area, mob -> mob.isAlive());
    }

    private static CherryRiftMonsterEntity find(List<CherryRiftMonsterEntity> mobs, MonsterType type) {
        return mobs.stream().filter(mob -> mob.variant() == type).findFirst().orElseThrow();
    }
}
