package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.LevelSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

final class BattleTuningChecks {
    private BattleTuningChecks() {}

    static void run(GameTestHelper h) {
        CombatHudStateChecks.run(h);
        for (float base : new float[]{665, 455, 595, 2240}) {
            h.assertTrue(LaneSystem.scaledMinionHealth(base, 0, false) == base, "Opening minion HP is preserved");
            h.assertTrue(Math.abs(LaneSystem.scaledMinionHealth(base, 5, false) - base * 1.3F) < .01F,
                    "Minion removes 60% of five growth stages");
            h.assertTrue(Math.abs(LaneSystem.scaledMinionHealth(base, 5, true) - base * 2) < .01F,
                    "Minion also removes 60% of extra HP from the late multiplier");
        }
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        var p = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        try {
            verifyRanksAndMana(h, p);
            verifyExactCost(h, p);
            verifySupplies(h, p);
            h.succeed();
        } finally {
            TacticalSupplySystem.clearPlayer(p);
            p.discard();
            TrainingMode.setEnabled(training);
            ModState.setEnabled(enabled);
        }
    }

    private static void verifyExactCost(GameTestHelper h, ServerPlayer p) {
        boolean unlimited = TrainingMode.isNoMana(), noCooldown = TrainingMode.isNoCooldown();
        TrainingMode.setNoMana(false);
        TrainingMode.setNoCooldown(false);
        try {
            PlayerDataProvider.chooseCharacter(p, CharacterType.KEHENG);
            var data = PlayerDataProvider.get(p);
            var skill = FirstGroupSkills.node("keheng", 2);
            data.setSkillLevel(skill.getId(), 2);
            data.setMana(37.999F);
            SkillSystem.cast(p, CharacterType.SLOT_Q);
            h.assertTrue(data.getMana() == 37.999F && data.getCooldown(skill.getId()) == 0,
                    "A fraction below the rank cost rejects without payment or cooldown");
            data.setMana(38);
            SkillSystem.cast(p, CharacterType.SLOT_Q);
            h.assertTrue(data.getMana() == 0 && data.getCooldown(skill.getId()) > 0,
                    "Exact rank cost succeeds and spends only the discounted amount");
        } finally {
            AbilityRuntime.clear(p);
            TrainingMode.setNoMana(unlimited);
            TrainingMode.setNoCooldown(noCooldown);
        }
    }

    private static void verifyRanksAndMana(GameTestHelper h, ServerPlayer p) {
        var data = PlayerDataProvider.get(p);
        int count = 0;
        for (var role : CharacterType.playableValues()) {
            data.setCharacterId(role.getId());
            data.setMana(123.25F);
            for (int level : new int[]{1, 7, 13, 20}) {
                data.setLevel(level);
                LevelSystem.applyLevelAttributes(p, level);
                h.assertTrue(p.getAttributeValue(ModAttributes.MAX_MANA) == FirstGroupGrowth.maxMana(role, level)
                        && data.getMana() == 123.25F, "Mana growth is applied without refilling " + role);
            }
            data.setMana(900);
            data.setLevel(1);
            LevelSystem.applyLevelAttributes(p, 1);
            h.assertTrue(data.getMana() == 700, "Downgrading clamps current mana " + role);
            for (var skill : FirstGroupSkills.candidates(role.getId())) {
                if (!skill.isPassive()) count++;
                float base = skill.getManaCost();
                h.assertTrue(skill.manaCost(0) == base && skill.manaCost(1) == base, "Original first-rank cost: " + skill.getId());
                for (int rank = 2; rank <= skill.maxRank(); rank++) {
                    float expected = base * (1 - .05F * (rank - 1));
                    h.assertTrue(Math.abs(skill.manaCost(rank) - expected) < .001F
                            && (base == 0 ? skill.manaCost(rank) == 0 : skill.manaCost(rank) > 0
                                    && skill.manaCost(rank) < skill.manaCost(rank - 1)),
                            "Every rank uses a lower positive cost or stays free: " + skill.getId());
                }
            }
        }
        h.assertTrue(count == 90, "All 90 active branches have rank costs checked");
    }

    private static void verifySupplies(GameTestHelper h, ServerPlayer p) {
        var data = PlayerDataProvider.get(p);
        data.setModMaxHealth(2000);
        p.setHealth(1000);
        data.setMana(100);
        p.getAttribute(ModAttributes.MAX_MANA).setBaseValue(1000);
        var blood = new ItemStack(ModItems.BLOOD_PILL.get(), 2);
        var mana = new ItemStack(ModItems.MANA_PILL.get(), 2);
        long start = p.level().getGameTime();
        TacticalSupplySystem.tryUse(p, blood);
        TacticalSupplySystem.tryUse(p, mana);
        TacticalSupplySystem.tryUse(p, blood);
        TacticalSupplySystem.tryUse(p, mana);
        h.assertTrue(blood.getCount() == 1 && mana.getCount() == 1, "Same-type supplies never stack");
        for (long age = 0; age < 20; age++) TacticalSupplySystem.tickRegeneration(p, start + age);
        h.assertTrue(p.getHealth() == 1000 && data.getMana() == 100, "Supplies wait a full second before paying");
        TacticalSupplySystem.tickRegeneration(p, start + 20);
        h.assertTrue(p.getHealth() == 1009 && data.getMana() == 130, "Population effects use current max HP and mana");
        data.setModMaxHealth(3000);
        p.getAttribute(ModAttributes.MAX_MANA).setBaseValue(1200);
        for (long age = 21; age <= 180; age++) TacticalSupplySystem.tickRegeneration(p, start + age);
        h.assertTrue(p.getHealth() == 1093 && data.getMana() == 266
                && data.getBuff("blood_pill") == 0 && data.getBuff("mana_pill") == 0,
                "Only eight and five pulses occur, and later pulses use the changed maxima");
        p.setHealth(p.getMaxHealth());
        data.setMana(1200);
        TacticalSupplySystem.tryUse(p, blood);
        TacticalSupplySystem.tryUse(p, mana);
        h.assertTrue(blood.getCount() == 1 && mana.getCount() == 1, "Full supplies are not consumed");
    }
}
