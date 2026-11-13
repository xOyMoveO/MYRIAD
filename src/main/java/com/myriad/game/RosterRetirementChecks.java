package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.network.NetworkSetup;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import java.util.Set;

/** No historical retired-player fixture: only ordinary input rejection and valid saves. */
final class RosterRetirementChecks {
    private static final Set<String> RETIRED = Set.of(
            "lige", "shenli", "mingzhu", "zhechuan", "guanlan", "luoheng", "xuanjia", "wuxiang", "feiyu", "wanyue");
    private static final Set<String> RETAINED = Set.of(
            "yingxiao", "tianxing", "fenjue", "xuanchen", "sulan", "lingyuan", "hanze", "qingluo", "chixiao", "yunsheng", "shuo");

    static void verify(GameTestHelper h, ServerPlayer player) {
        var data = PlayerDataProvider.get(player);
        h.assertTrue(Set.copyOf(java.util.Arrays.stream(CharacterType.playableValues()).map(CharacterType::getId).toList()).equals(RETAINED)
                && FirstGroupSkills.ROLES.containsAll(RETAINED),
                "Registration and the candidate graph contain exactly the retained eleven heroes");
        int candidates = 0;
        for (String id : RETAINED) {
            var role = CharacterType.byId(id);
            PlayerDataProvider.chooseCharacter(player, role);
            h.assertTrue(id.equals(data.getCharacterId()) && ModItems.initialWeaponItem(id) != Items.AIR,
                    "Normal retained selection grants the matching weapon: " + id);
            var skills = FirstGroupSkills.candidates(id);
            h.assertTrue(skills.size() == 8, "Retained hero still has all eight candidates: " + id);
            candidates += skills.size();
            data.setSkillLevel(skills.get(2).getId(), 1);
            data.setGold(523);
            data.setMana(321);
            var saved = PlayerData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, data).getOrThrow();
            var restored = PlayerData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow();
            var copied = new PlayerData();
            copied.copyStateFrom(restored);
            h.assertTrue(id.equals(copied.getCharacterId()) && copied.skillLevel(skills.get(2).getId()) == 1
                    && copied.getGold() == 523 && copied.getMana() == 321,
                    "Valid role, skill and ordinary resources survive save/copy: " + id);
        }
        h.assertTrue(candidates == 88, "The eleven-hero baseline contains eighty-eight candidates");
        verifyRetainedRangedBasics(h, player);
        PlayerDataProvider.chooseCharacter(player, CharacterType.YINGXIAO);
        data.refundAllSkillPointsForTraining();
        player.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(Items.DIAMOND_SWORD));
        float mana = data.getMana();
        var inventory = player.getInventory().getItem(0).copy();
        try {
            for (String id : RETIRED) {
                h.assertTrue(CharacterType.byId(id) == null && FirstGroupSkills.candidates(id).isEmpty()
                        && ModItems.initialWeaponItem(id) == Items.AIR,
                        "Retired ID has no registration, skills or weapon fallback: " + id);
                var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                try {
                    NetworkSetup.ChooseCharacterPayload.STREAM_CODEC.encode(buffer,
                            new NetworkSetup.ChooseCharacterPayload(id));
                    NetworkSetup.handleChooseCharacter(player, NetworkSetup.ChooseCharacterPayload.STREAM_CODEC.decode(buffer));
                } finally { buffer.release(); }
                h.assertTrue("yingxiao".equals(data.getCharacterId())
                        && net.minecraft.world.item.ItemStack.isSameItemSameComponents(inventory, player.getInventory().getItem(0)),
                        "Forged selection message cannot change a valid role or weapon: " + id);
                player.level().getServer().getCommands().getDispatcher().execute(
                        "myriad char " + id, player.createCommandSourceStack());
                h.assertTrue("yingxiao".equals(data.getCharacterId()), "Character command rejects retired ID: " + id);
                SkillSystem.cast(player, Integer.MIN_VALUE);
                SkillSystem.cast(player, Integer.MAX_VALUE);
                h.assertTrue(data.getMana() == mana && !AbilityRuntime.channeling(player)
                        && data.getPlacementType() == 0,
                        "Malformed skill messages after rejected selection cannot commit a retired cast: " + id);
            }
            for (String id : new String[]{"", "not_a_character"}) {
                NetworkSetup.handleChooseCharacter(player, new NetworkSetup.ChooseCharacterPayload(id));
                h.assertTrue("yingxiao".equals(data.getCharacterId()) && ModItems.initialWeaponItem(id) == Items.AIR,
                        "Ordinary invalid IDs are rejected without remapping");
            }
            h.assertTrue(player.getInventory().getItem(1).is(Items.DIAMOND_SWORD),
                    "Retirement never removes the vanilla diamond sword");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new IllegalStateException(error);
        } finally {
            PlayerDataProvider.resetForHostSession(player);
        }
        System.out.println("[MYRIAD-ROSTER-RETIREMENT] PASS: eleven heroes/eighty-eight candidates, valid save/copy, ten rejected wire IDs/commands, no weapon remapping");
    }
    private static void verifyRetainedRangedBasics(GameTestHelper h, ServerPlayer player) {
        var target = h.spawn(net.minecraft.world.entity.EntityTypes.PIG, new net.minecraft.core.BlockPos(2, 3, 2));
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        var previousPosition = player.position();
        float previousYaw = player.getYRot(), previousPitch = player.getXRot();
        // This synchronous contract exercises the first actual flight step; far-flight timing is
        // covered by HeroActionTimeChecks rather than pretending repeated calls are new ticks.
        player.setPos(target.position().add(0, 0, -1.5));
        player.setYRot(0);
        player.setXRot(0);
        try {
            for (String role : new String[]{"xuanchen", "hanze", "qingluo", "yunsheng"}) {
                PlayerDataProvider.chooseCharacter(player, CharacterType.byId(role));
                com.myriad.combat.MagicBullet.clearFor(player.getUUID());
                target.setHealth(1000);
                target.invulnerableTime = 0;
                var data = PlayerDataProvider.get(player);
                com.myriad.combat.BasicAttackSystem.tryAttack(player, target.getId());
                h.assertTrue(target.getHealth() == 1000,
                        "Target-ID attack messages cannot bypass a retained mage's projectile: " + role);
                com.myriad.combat.MagicBullet.tryFire(player, player.getInventory().getItem(0));
                for (int tick = 0; tick < 4; tick++) com.myriad.combat.MagicBullet.tickBullets(player, data);
                float hp = target.getHealth();
                h.assertTrue(hp < 1000, "Retained ranged basic projectile still deals actual damage: " + role);
                com.myriad.combat.MagicBullet.tryFire(player, player.getInventory().getItem(0));
                for (int tick = 0; tick < 4; tick++) com.myriad.combat.MagicBullet.tickBullets(player, data);
                h.assertTrue(target.getHealth() == hp, "Repeated input cannot bypass ranged recovery: " + role);
            }
        } finally {
            com.myriad.combat.MagicBullet.clearFor(player.getUUID());
            target.discard();
            player.setPos(previousPosition);
            player.setYRot(previousYaw);
            player.setXRot(previousPitch);
        }
    }

}
