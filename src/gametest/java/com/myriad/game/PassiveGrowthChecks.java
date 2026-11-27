package com.myriad.game;

import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.PlayerData;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;

final class PassiveGrowthChecks {
    static void verify(GameTestHelper h) {
        String id = FirstGroupSkills.node("luoxia", 0).getId();
        var data = new PlayerData();
        data.setCharacterId("luoxia");
        data.setSkillLevel(id, 1);
        for (int level = 1; level <= 20; level++) {
            data.setLevel(level);
            int expected = level < 4 ? 1 : level < 7 ? 2 : level < 10 ? 3 : level < 13 ? 4 : 5;
            h.assertTrue(data.skillLevel(id) == expected && data.spentSkillPoints() == 0, "Derived passive at " + level);
        }
        data.setLevel(6);
        data.resetForTrainingLevel();
        h.assertTrue(data.skillLevel(id) == 2 && data.getSkillPoints() == 6, "Training keeps choice and refunds actives");
        data.refundAllSkillPointsForTraining();
        h.assertTrue(data.skillLevel(id) == 0, "Role reset clears passive");
        h.assertTrue(FirstGroupSkills.learnError("luoxia", data.getSkillLevels(), 0, 7, id) == null,
                "Choose a passive with zero ordinary points");
        data.beginQueueRound(java.util.UUID.randomUUID());
        data.setLevel(7);
        data.setSkillLevel(id, 2);
        data.setSkillLevel(FirstGroupSkills.node("luoxia", 2).getId(), 3);
        data.setSkillPoints(2);
        var json = JsonParser.parseString(PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsString()).getAsJsonObject();
        json.remove("skillProgressVersion");
        var saved = new com.google.gson.JsonPrimitive(json.toString());
        var migrated = PlayerData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        h.assertTrue(migrated.skillLevel(id) == 3 && migrated.getSkillPoints() == 4, "Reconcile seven-point legacy budget");
        var again = PlayerData.CODEC.parse(JsonOps.INSTANCE, PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, migrated).getOrThrow()).getOrThrow();
        h.assertTrue(again.getSkillPoints() == 4, "Reload never refunds twice");
        json.remove("queueRound");
        var unknown = PlayerData.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(json.toString())).getOrThrow();
        unknown.ensureLevelRewards(7);
        h.assertTrue(!unknown.automaticPassives() && unknown.skillLevel(id) == 2 && unknown.getSkillPoints() == 2,
                "Unattributed old save keeps its round and points");
    }
}
