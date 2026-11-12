package com.myriad.client;

import java.util.Map;

import com.myriad.character.Skill;

/** HUD 多充能技能显示约定：服务端同步 skill_charge_<技能ID> 计数器即可自动显示。 */
public final class SkillChargeDisplay {
    private SkillChargeDisplay() {}

    public static int chargeCount(Skill skill, Map<String, Integer> syncedCounters) {
        if (skill.getMaxCharges() <= 1) return -1;
        String key = "skill_charge_" + skill.getId();
        return Math.max(0, syncedCounters.getOrDefault(key, skill.getMaxCharges()));
    }
}
