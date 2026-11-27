package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.client.CombatHudSkillRules;
import com.myriad.client.CombatHudState.SkillStatus;
import com.myriad.network.NetworkSetup.PlayerDataPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

final class CombatHudStateChecks {
    private CombatHudStateChecks() {}

    static void run(GameTestHelper h) {
        checkCooldownAndMana(h);
        checkTrainingAndBranches(h);
        checkPlayerSnapshotCodec(h);
        System.out.println("[MYRIAD-COMBAT-HUD-STATE] PASS: learned/base, prep anchor, mana/CD priority, recasts, charges, passive branch, formatted timers and owner snapshot codec");
    }

    private static void checkCooldownAndMana(GameTestHelper h) {
        var shell = FirstGroupSkills.node("yike", 2);
        boolean enoughMana = CombatHudSkillRules.enoughMana(shell, 34, false, false, 0);
        h.assertTrue(!enoughMana && CombatHudSkillRules.enoughMana(shell, 35, false, false, 0),
                "Shell uses its actual 35-mana cost");
        h.assertTrue(!CombatHudSkillRules.enoughMana(shell, 2, 33.249f, false, false, 0)
                        && CombatHudSkillRules.enoughMana(shell, 2, 33.25f, false, false, 0),
                "HUD and server use the same fractional rank-2 shell threshold");
        int cooldown = CombatHudSkillRules.blockingCooldown(40, false, 0, -1, 0);
        h.assertTrue(CombatHudSkillRules.status(true, true, cooldown, 0, -1, enoughMana, false)
                        == SkillStatus.COOLDOWN, "An active cooldown takes priority over insufficient mana");
        h.assertTrue(CombatHudSkillRules.status(true, true, 0, 0, -1, enoughMana, false)
                        == SkillStatus.NO_MANA, "A recovered skill still reports missing mana");
        h.assertTrue(CombatHudSkillRules.status(true, true, 0, 0, -1, true, true)
                        == SkillStatus.UNAVAILABLE, "Control does not create fake cooldown");

        h.assertTrue(CombatHudSkillRules.blockingCooldown(40, false, 23, -1, 0) == 0
                        && CombatHudSkillRules.enoughMana(shell, 0, false, false, 23),
                "An open, free recast is usable during the initial cast's cooldown");
        h.assertTrue(CombatHudSkillRules.blockingCooldown(40, false, 0, 1, 53) == 0
                        && CombatHudSkillRules.status(true, true, 0, 0, 1, true, false) == SkillStatus.READY,
                "Restoring the next charge cannot block an available charge");
        h.assertTrue(CombatHudSkillRules.blockingCooldown(0, false, 0, 0, 53) == 53
                        && CombatHudSkillRules.status(true, true, 53, 0, 0, true, false) == SkillStatus.COOLDOWN
                        && CombatHudSkillRules.status(true, true, 0, 0, 0, true, false) == SkillStatus.UNAVAILABLE,
                "An empty charge pool stays unavailable until the server confirms a restored charge");

        h.assertTrue(CombatHudSkillRules.cooldownText(1).equals("0.1")
                        && CombatHudSkillRules.cooldownText(83).equals("4.2")
                        && CombatHudSkillRules.cooldownText(198).equals("9.9")
                        && CombatHudSkillRules.cooldownText(200).equals("10")
                        && CombatHudSkillRules.cooldownText(201).equals("11"),
                "Sub-ten displays have one decimal and positive cooldowns never display zero");
    }

    private static void checkTrainingAndBranches(GameTestHelper h) {
        var anchor = (AbilitySkill) FirstGroupSkills.resolve(CharacterType.RIVET, CharacterType.SLOT_W, Map.of());
        var ordinary = FirstGroupSkills.node("rivet", 2);
        var shell = FirstGroupSkills.node("yike", 2);
        h.assertTrue(CombatHudSkillRules.learned(anchor, 0, false) && !CombatHudSkillRules.learned(ordinary, 0, false),
                "Untrained basic anchor is usable and other truly unlearned skills are locked");
        h.assertTrue(CombatHudSkillRules.learned(ordinary, 0, true) && !CombatHudSkillRules.learned(shell, 0, true)
                        && CombatHudSkillRules.blockingCooldown(20, true, 0, -1, 0) == 0
                        && CombatHudSkillRules.enoughMana(ordinary, 0, true, false, 0),
                "Training flags apply individually and the shell still requires a learned rank");
        h.assertTrue(CombatHudSkillRules.freePreparation(anchor, true)
                        && CombatHudSkillRules.enoughMana(anchor, 0, false, true, 0)
                        && !CombatHudSkillRules.freePreparation(ordinary, true)
                        && !CombatHudSkillRules.freePreparation(anchor, false),
                "Only the preparation anchor waives payment");
        var fixed = FirstGroupSkills.node("yike", 4);
        var mobile = FirstGroupSkills.node("yike", 5);
        h.assertTrue(FirstGroupSkills.resolve(CharacterType.YIKE, CharacterType.SLOT_E, Map.of(fixed.getId(), 2)) == fixed
                        && FirstGroupSkills.resolve(CharacterType.YIKE, CharacterType.SLOT_E, Map.of(mobile.getId(), 2)) == mobile,
                "The HUD's normal resolver uses the invested active branch");
        var firstPassive = FirstGroupSkills.node("yike", 0);
        var secondPassive = FirstGroupSkills.node("yike", 1);
        h.assertTrue(CombatHudSkillRules.selectedPassive("yike", Map.of()) == null
                        && CombatHudSkillRules.selectedPassive("yike", Map.of(firstPassive.getId(), 1)) == firstPassive
                        && CombatHudSkillRules.selectedPassive("yike", Map.of(secondPassive.getId(), 1)) == secondPassive,
                "The fifth slot is empty until a branch is selected, and shows the invested passive");
    }

    private static void checkPlayerSnapshotCodec(GameTestHelper h) {
        var dim = Identifier.parse("myriad:training");
        var payload = new PlayerDataPayload(6, 17, 61, 840, 196.25f, 0, 1, 3, 2, 0, "rivet",
                5, 9, 20, 0, 0, false, false, false, false, Map.of(), Map.of(),
                0, 0, Map.of("rivet_tactical_anchor", 2), false, 0, true, 180,
                List.of(), Map.of(), 0, 0, 3, 8, 842, 1200, 116, 20, 36, dim, false, true);

        var wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerDataPayload.STREAM_CODEC.encode(wire, payload);
            h.assertTrue(PlayerDataPayload.STREAM_CODEC.decode(wire).equals(payload) && !wire.isReadable(),
                    "Player-data wire codec preserves health, owner entity, dimension, cap and combat flags");
        } finally {
            wire.release();
        }
    }
}
