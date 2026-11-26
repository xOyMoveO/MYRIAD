package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.combat.BasicAttackAssist;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.MagicBullet;
import com.myriad.combat.WindWallSystem;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class ProjectileBasicAssistChecks {
    private ProjectileBasicAssistChecks() {}

    static void run(GameTestHelper h) {
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        var level = h.getLevel();
        var origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 3, 4)));
        var player = caster(h, origin);
        var wallOwner = caster(h, origin.add(0, 0, 1.35));
        var pig = h.spawn(EntityTypes.PIG, new BlockPos(4, 3, 6));
        immobilize(pig);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        JiarenManager.create(player);
        var dummy = level.getEntitiesOfClass(Villager.class, new AABB(origin, origin.add(0, 2, 5)).inflate(1),
                target -> JiarenManager.isJiaren(target.getUUID())).getFirst();
        immobilize(dummy);
        var cover = BlockPos.containing(origin.add(0, 1, 1));
        var muzzleCover = cover.north();
        var previous = level.getBlockState(cover);
        var previousMuzzleCover = level.getBlockState(muzzleCover);
        Runnable cleanup = () -> {
            WindWallSystem.clearFor(wallOwner.getUUID());
            AbilityRuntime.clear(player);
            level.setBlockAndUpdate(cover, previous);
            level.setBlockAndUpdate(muzzleCover, previousMuzzleCover);
            dummy.discard();
            pig.discard();
            player.discard();
            wallOwner.discard();
            TrainingMode.setEnabled(training);
            ModState.setEnabled(enabled);
        };
        try {
            dummy.setPos(origin.add(4, 1, 2));
            for (var role : new CharacterType[]{CharacterType.XUANCHEN, CharacterType.HANZE,
                    CharacterType.QINGLUO, CharacterType.YUNSHENG}) {
                PlayerDataProvider.chooseCharacter(player, role);
                pig.setPos(origin.add(.75, 1, 1.7));
                shootMage(player, pig);
                h.assertTrue(pig.getHealth() < 1000, "Keeps the mage's existing off-center hit: " + role);

                pig.setPos(origin.add(1.45, 1, 1.7));
                shootMage(player, pig);
                h.assertTrue(pig.getHealth() == 1000, "15% does not add a box corner outside the mage's sphere: " + role);

                pig.setPos(origin.add(0, 1, 1.7));
                level.setBlockAndUpdate(cover, Blocks.GLASS_PANE.defaultBlockState());
                shootMage(player, pig);
                h.assertTrue(pig.getHealth() == 1000, "A pane stops the mage before a same-step target: " + role);
                level.setBlockAndUpdate(cover, previous);

                player.setPos(origin.add(0, 0, -.25));
                level.setBlockAndUpdate(muzzleCover, Blocks.GLASS_PANE.defaultBlockState());
                shootMage(player, pig);
                h.assertTrue(pig.getHealth() == 1000, "The mage's muzzle offset does not skip a pane: " + role);
                level.setBlockAndUpdate(muzzleCover, previousMuzzleCover);
                player.setPos(origin);

                WindWallSystem.createWall(wallOwner, PlayerDataProvider.get(wallOwner), 20, false);
                shootMage(player, pig);
                h.assertTrue(pig.getHealth() == 1000, "Enemy wind wall stops a same-step target: " + role);
                pig.setPos(origin.add(0, 1, .85));
                shootMage(player, pig);
                h.assertTrue(pig.getHealth() == 1000, "The mage's existing wind-wall priority applies to the whole step: " + role);
                WindWallSystem.clearFor(wallOwner.getUUID());
            }
            PlayerDataProvider.chooseCharacter(player, CharacterType.YIKE);
            pig.setPos(origin.add(4, 1, 2));
            lower(dummy, player, .18, 2.5);
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }

        float[] before = {JiarenManager.hp(dummy.getUUID())};
        boolean[] done = {false};
        for (int tick = 1; tick <= 42; tick++) {
            int current = tick;
            h.runAfterDelay(tick, () -> {
                if (done[0]) return;
                try {
                    if (current == 1 || current == 9) BasicAttackSystem.tryAttack(player, 0);
                    if (current == 7) {
                        assertGrazing(h, dummy, before[0], "Yike");
                        AbilityRuntime.clear(player);
                        before[0] = JiarenManager.hp(dummy.getUUID());
                        lower(dummy, player, .18, 1.1);
                        pig.setPos(origin.add(0, 1, 1.65));
                        pig.setHealth(1000);
                        pig.damageCooldownTime = 0;
                    }
                    if (current == 13) {
                        h.assertTrue(pig.getHealth() < 1000 && JiarenManager.hp(dummy.getUUID()) == before[0],
                                "A later baseline contact beats an earlier assist contact in the same step");
                        PlayerDataProvider.chooseCharacter(player, CharacterType.LUOXIA);
                        var data = PlayerDataProvider.get(player);
                        data.setSkillLevel(FirstGroupSkills.node("luoxia", 2).getId(), 1);
                        data.setMana(700);
                        pig.discard();
                        lower(dummy, player, .30, 4);
                        SkillSystem.cast(player, CharacterType.SLOT_Q);
                    }
                    if (current == 23) {
                        h.assertTrue(JiarenManager.hp(dummy.getUUID()) == before[0], "Luoxia's skill arrow keeps its .09 radius");
                        BasicAttackSystem.tryAttack(player, LuoxiaBow.PRESS);
                    }
                    if (current == 34) BasicAttackSystem.tryAttack(player, LuoxiaBow.RELEASE);
                    AbilityRuntime.tick(player);
                    if (current == 42) {
                        assertGrazing(h, dummy, before[0], "Luoxia");
                        done[0] = true;
                        cleanup.run();
                        h.succeed();
                    }
                } catch (RuntimeException | Error failure) {
                    done[0] = true;
                    cleanup.run();
                    throw failure;
                }
            });
        }
    }

    private static ServerPlayer caster(GameTestHelper h, Vec3 origin) {
        var player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        player.setPos(origin);
        player.setYRot(0);
        player.setXRot(0);
        player.setNoGravity(true);
        return player;
    }

    private static void immobilize(Mob target) {
        target.setNoAi(true);
        target.setNoGravity(true);
        target.noPhysics = true;
    }

    private static void shootMage(ServerPlayer player, Mob target) {
        MagicBullet.clearFor(player.getUUID());
        target.setHealth(1000);
        target.damageCooldownTime = 0;
        MagicBullet.tryFire(player, player.getMainHandItem());
        MagicBullet.tickBullets(player, PlayerDataProvider.get(player));
    }

    private static void lower(Mob target, ServerPlayer caster, double gap, double forward) {
        var eye = caster.getEyePosition();
        target.setPos(eye.x, eye.y - target.getBbHeight() - gap, eye.z + forward);
    }

    private static void assertGrazing(GameTestHelper h, Mob dummy, float before, String role) {
        float actual = JiarenManager.hp(dummy.getUUID());
        h.assertTrue(BasicAttackAssist.enabled() ? actual < before : actual == before,
                role + " basic grazing contact matches the configured padding");
    }
}
