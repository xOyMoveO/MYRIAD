package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.combat.BasicAttackAssist;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.InputFailure;
import com.myriad.combat.MeleeInputWindow;
import com.myriad.combat.VanillaMeleeAttack;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class MeleeAssistChecks {
    private MeleeAssistChecks() {}

    static void run(GameTestHelper helper) {
        geometry(helper);
        inputWindows(helper);
        boolean wasEnabled = ModState.isEnabled();
        boolean wasTraining = TrainingMode.isEnabled();
        var player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        var hints = new java.util.ArrayList<InputFailure>();
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener) {
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket message
                        && message.overlay() && message.content().getContents() instanceof
                        net.minecraft.network.chat.contents.TranslatableContents key) {
                    var reason = InputFailure.forKey(key.getKey());
                    if (reason != null) hints.add(reason);
                }
            }
        };
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        BlockPos base = helper.absolutePos(new BlockPos(3, 6, 3));
        Vec3 origin = Vec3.atBottomCenterOf(base);
        BlockPos wall = base.offset(0, 1, 2);
        var level = helper.getLevel();
        try {
            ModState.setEnabled(true);
            TrainingMode.setEnabled(true);
            var data = PlayerDataProvider.get(player);
            data.setCharacterId("lingyuan");
            CharacterType.applyInitialAttributes(player, CharacterType.LINGYUAN);
            FirstGroupGrowth.initialize(player, data);
            var weapon = new ItemStack(ModItems.LINGYUAN_SWORD.get());
            weapon.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            player.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            player.getAttribute(ModAttributes.CRIT_CHANCE).removeModifiers();
            player.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
            player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).removeModifiers();
            player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(2);
            player.setPos(origin);
            player.setXRot(0);
            player.setYRot(0);
            player.setNoGravity(true);

            JiarenManager.create(player);
            JiarenManager.createFriendly(player);
            var dummies = level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(5),
                    target -> JiarenManager.isJiaren(target.getUUID()));
            var target = dummies.stream().filter(e -> !JiarenManager.isFriendlyDummy(player, e)).findFirst().orElseThrow();
            var ally = dummies.stream().filter(e -> JiarenManager.isFriendlyDummy(player, e)).findFirst().orElseThrow();
            var ambient = helper.spawn(EntityTypes.VILLAGER, new BlockPos(4, 6, 3));
            var strength = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            strength.setAccessible(true);
            float fullHealth = JiarenManager.hp(target.getUUID());

            place(ambient, origin, .03, 1.2);
            strength.setInt(player, 200);
            VanillaMeleeAttack.tryAssist(player);
            helper.assertTrue(ambient.getHealth() == ambient.getMaxHealth(), "Ambient villagers receive no assist");
            ambient.discard();

            place(target, origin, .10, 1.2);
            VanillaMeleeAttack.tryAssist(player);
            helper.assertTrue(JiarenManager.hp(target.getUUID()) == fullHealth, "A .10-wide miss stays empty");

            place(ally, origin, .03, .8);
            VanillaMeleeAttack.tryAssist(player);
            helper.assertTrue(JiarenManager.hp(ally.getUUID()) == fullHealth, "Friendly dummies receive no assist");
            ally.setPos(origin.add(0, 0, .8));
            place(target, origin, .08, 1.2);
            VanillaMeleeAttack.tryAssist(player);
            helper.assertTrue(JiarenManager.hp(target.getUUID()) == fullHealth,
                    "An exact invalid target keeps priority over an assisted enemy");
            ally.setPos(origin.add(3, 0, 0));

            target.setInvisible(true);
            VanillaMeleeAttack.tryAssist(player);
            target.setInvisible(false);
            hints.clear();
            player.tickCount += 12;
            data.setBuff("g1_stun", 20);
            VanillaMeleeAttack.tryAssist(player);
            data.setBuff("g1_stun", 0);
            player.tickCount += 12;
            strength.setInt(player, 0);
            VanillaMeleeAttack.tryAssist(player);
            VanillaMeleeAttack.tryAssist(player);
            helper.assertTrue(hints.equals(java.util.List.of(InputFailure.STUN, InputFailure.RECOVERING)),
                    "Real authority sends one private action-bar reason per rejected state");
            helper.assertTrue(JiarenManager.hp(target.getUUID()) == fullHealth && strength.getInt(player) == 0,
                    "Invisible targets, stun and incomplete recovery cannot produce an assisted hit");

            strength.setInt(player, 200);
            place(target, origin, .08, 2.02 + target.getBbWidth() / 2);
            VanillaMeleeAttack.tryAssist(player);
            helper.assertTrue(JiarenManager.hp(target.getUUID()) == fullHealth, "The original box cannot exceed reach");

            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            place(target, origin, .08, 1.52 + target.getBbWidth() / 2);
            VanillaMeleeAttack.tryAssist(player);
            helper.assertTrue(JiarenManager.hp(target.getUUID()) == fullHealth,
                    "The part of the temporary box protruding through a wall cannot take damage");

            place(target, origin, .08, 1.2);
            VanillaMeleeAttack.tryAssist(player);
            float after = JiarenManager.hp(target.getUUID());
            helper.assertTrue(after < fullHealth && strength.getInt(player) == 0,
                    "A .08 graze before a wall uses the native hit and recovery chain");
            VanillaMeleeAttack.tryAssist(player);
            helper.assertTrue(JiarenManager.hp(target.getUUID()) == after,
                    "Repeated assist intents during recovery cannot deal a second hit");

            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            strength.setInt(player, 200);
            target.damageCooldownTime = 0;
            place(target, origin, .08, 1.98 + target.getBbWidth() / 2);
            VanillaMeleeAttack.tryAssist(player);
            helper.assertTrue(JiarenManager.hp(target.getUUID()) < after,
                    "A graze whose original contact is still inside native reach remains available");
            bufferedAuthority(helper, player, target, origin, strength);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        } finally {
            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            JiarenManager.removeNearby(player);
            AbilityRuntime.clear(player);
            BasicAttackSystem.clearFor(player.getUUID());
            player.discard();
            ModState.setEnabled(wasEnabled);
            TrainingMode.setEnabled(wasTraining);
        }
        helper.succeed();
    }

    private static void bufferedAuthority(GameTestHelper helper, ServerPlayer player, LivingEntity target,
                                          Vec3 origin, java.lang.reflect.Field strength) throws IllegalAccessException {
        var data = PlayerDataProvider.get(player);
        var weapon = player.getMainHandItem();
        int oneEarly = (int) Math.ceil(player.getCurrentItemAttackStrengthDelay() - .5) - 1;
        strength.setInt(player, oneEarly);
        helper.assertTrue(player.getAttackStrengthScale(.5f) < 1 && player.getAttackStrengthScale(1.5f) >= 1,
                "Fixture is exactly one vanilla recovery step early");

        target.setPos(origin.add(0, 0, 1.2));
        target.damageCooldownTime = 0;
        float hp = JiarenManager.hp(target.getUUID());
        helper.assertTrue(!VanillaMeleeAttack.prepare(player, target), "Immediate native input stays strict");
        place(target, origin, .08, 1.2);
        VanillaMeleeAttack.tryAssist(player);
        player.tickCount++;
        strength.setInt(player, oneEarly + 1);
        VanillaMeleeAttack.tickBuffered(player);
        helper.assertTrue(JiarenManager.hp(target.getUUID()) == hp, "Immediate grazes never enter the server buffer");

        strength.setInt(player, oneEarly);
        target.setPos(origin.add(0, 0, 1.2));
        data.setBuff("g1_root", 20);
        VanillaMeleeAttack.tryBuffered(player);
        VanillaMeleeAttack.tryBuffered(player);
        VanillaMeleeAttack.tickBuffered(player);
        helper.assertTrue(JiarenManager.hp(target.getUUID()) == hp, "Rooted buffered input never hits early");
        // A different, grazing opponent is under the new aim when the next server tick arrives.
        JiarenManager.create(player);
        var other = helper.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(5),
                e -> e != target && JiarenManager.isJiaren(e.getUUID()) && !JiarenManager.isFriendlyDummy(player, e))
                .getFirst();
        target.setPos(origin.add(3, 0, 0));
        place(other, origin, .08, 1.2);
        float otherHp = JiarenManager.hp(other.getUUID());
        data.setBuff("zhongshengguizong", 20);
        player.tickCount++;
        strength.setInt(player, oneEarly + 1);
        VanillaMeleeAttack.tickBuffered(player);
        float struck = JiarenManager.hp(other.getUUID());
        helper.assertTrue(JiarenManager.hp(target.getUUID()) == hp && struck < otherHp && strength.getInt(player) == 0,
                "Root and silence preserve the press; the current grazing target alone takes a full attack");
        player.tickCount++;
        other.damageCooldownTime = 0;
        strength.setInt(player, 200);
        VanillaMeleeAttack.tickBuffered(player);
        helper.assertTrue(JiarenManager.hp(other.getUUID()) == struck, "Duplicate requests cannot yield a second hit");
        data.setBuff("g1_root", 0);
        data.setBuff("zhongshengguizong", 0);

        strength.setInt(player, oneEarly);
        VanillaMeleeAttack.tryBuffered(player);
        other.setPos(origin.add(0, 0, 4));
        strength.setInt(player, oneEarly + 1);
        player.tickCount++;
        VanillaMeleeAttack.tickBuffered(player);
        helper.assertTrue(JiarenManager.hp(other.getUUID()) == struck, "Leaving reach before the next tick cancels");
        other.setPos(origin.add(0, 0, 1.2));
        strength.setInt(player, oneEarly);
        VanillaMeleeAttack.tryBuffered(player);
        data.setBuff("g1_stun", 20);
        strength.setInt(player, oneEarly + 1);
        player.tickCount++;
        VanillaMeleeAttack.tickBuffered(player);
        data.setBuff("g1_stun", 0);
        helper.assertTrue(JiarenManager.hp(other.getUUID()) == struck, "New stun cancels the reserved attack");
        strength.setInt(player, oneEarly);
        VanillaMeleeAttack.tryBuffered(player);
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon.copy());
        strength.setInt(player, oneEarly + 1);
        player.tickCount++;
        VanillaMeleeAttack.tickBuffered(player);
        helper.assertTrue(JiarenManager.hp(other.getUUID()) == struck, "Replacing the reserved weapon cancels");
        strength.setInt(player, oneEarly);
        VanillaMeleeAttack.tryBuffered(player);
        player.tickCount++;
        VanillaMeleeAttack.tickBuffered(player);
        strength.setInt(player, 200);
        player.tickCount++;
        VanillaMeleeAttack.tickBuffered(player);
        helper.assertTrue(JiarenManager.hp(other.getUUID()) == struck, "Slow recovery cannot extend the one-tick grace");
        VanillaMeleeAttack.tryBuffered(player);
        helper.assertTrue(JiarenManager.hp(other.getUUID()) < struck && strength.getInt(player) == 0,
                "Already recovered buffered input uses the exact native target immediately");
    }

    private static void inputWindows(GameTestHelper helper) {
        var buffer = new MeleeInputWindow();
        helper.assertTrue(!buffer.offer(10, 1, 0) && !buffer.offer(10, 3, 2), "Disabled or >2t never queues");
        helper.assertTrue(buffer.offer(10, 2, 2) && !buffer.consume(11, false)
                && !buffer.offer(11, 2, 2) && buffer.consume(12, true) && !buffer.consume(12, true),
                "A two-tick press cannot renew or fire twice");
        helper.assertTrue(buffer.offer(20, 1, 2) && buffer.consume(21, true), "One-tick press fires when ready");
        helper.assertTrue(buffer.offer(30, 1, 2) && !buffer.consume(31, false) && !buffer.consume(32, true),
                "A slowed recovery cannot extend its original deadline");
        buffer.offer(40, 2, 2);
        buffer.clear();
        helper.assertTrue(!buffer.consume(42, true), "A cancelled press cannot produce damage");
        var hints = new InputFailure.Window();
        helper.assertTrue(hints.allow(InputFailure.RECOVERING, 0) && !hints.allow(InputFailure.RECOVERING, 1)
                && hints.allow(InputFailure.STUN, 1) && !hints.allow(InputFailure.MANA, 12)
                && hints.allow(InputFailure.MANA, 13), "Duplicate hints are quiet and controls take precedence");
    }

    private static void geometry(GameTestHelper helper) {
        var original = new AABB(.08, 0, 1, .68, 1.8, 1.6);
        var start = new Vec3(0, .9, 0);
        var end = new Vec3(0, .9, 3);
        var first = BasicAttackAssist.contact(start, end, false, 3, original, 0, .15);
        helper.assertTrue(first != null && first.originalPoint().distanceToSqr(new Vec3(.08, .9, 1)) < 1e-12,
                "Return the nearest contact on the original box, separately from the expanded hit");
        helper.assertTrue(BasicAttackAssist.contact(start, end, false, 3, original, 0, 0) == null,
                "0% restores the native hit boundaries");
        helper.assertTrue(BasicAttackAssist.contact(start, first.point(), true, 3, original, 0, .15) == null,
                "A solid obstruction wins at the same contact point");
        var nearer = BasicAttackAssist.contact(start, end, false, 3, original.move(-.04, 0, .3), 0, .15);
        var earlier = BasicAttackAssist.contact(start, end, false, 3, original.move(0, 0, -.3), 0, .15);
        helper.assertTrue(nearer != null && earlier != null && BasicAttackAssist.precedes(nearer, 10, first, 1)
                && BasicAttackAssist.precedes(earlier, 10, first, 1) && BasicAttackAssist.precedes(first, 1, first, 2),
                "Assist tie-breaking uses lateral offset, contact distance and stable entity ID in order");
    }

    private static void place(LivingEntity entity, Vec3 origin, double gap, double depth) {
        entity.setPos(origin.add(entity.getBbWidth() / 2 + gap, 0, depth));
    }
}
