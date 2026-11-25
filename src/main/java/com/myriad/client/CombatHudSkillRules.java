package com.myriad.client;

import com.myriad.character.Skill;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.yike.RainbowShell;
import com.myriad.client.CombatHudState.SkillStatus;
import java.util.Map;

/** Shared with the headless HUD regression. These are display rules; the server owns casts. */
public final class CombatHudSkillRules {
    private CombatHudSkillRules() {}

    public static boolean learned(AbilitySkill skill, int rank, boolean training) {
        // Rainbow Shell requires a paid rank at release even in training.
        return rank > 0 || skill.baseAvailable() || training && !(skill instanceof RainbowShell);
    }

    public static boolean freePreparation(AbilitySkill skill, boolean preparing) {
        return preparing && skill.getId().equals("rivet_tactical_anchor");
    }

    public static boolean enoughMana(Skill skill, int mana, boolean unlimited, boolean freePreparation, int recastTicks) {
        // Present registered forms use ability definition costs. Recasts and preparation anchor are free.
        return unlimited || freePreparation || recastTicks > 0 || mana >= skill.getManaCost();
    }

    public static int blockingCooldown(int baseCooldown, boolean noCooldown, int recastTicks,
            int charges, int chargeRemaining) {
        if (recastTicks > 0 || charges > 0) return 0;
        int cooldown = noCooldown ? 0 : baseCooldown;
        return charges == 0 ? Math.max(cooldown, chargeRemaining) : cooldown;
    }

    public static SkillStatus status(boolean learned, boolean inPhase, int cooldown, int recastTicks,
            int charges, boolean enoughMana, boolean controlled) {
        if (!learned) return SkillStatus.LOCKED;
        if (!inPhase) return SkillStatus.UNAVAILABLE;
        if (cooldown > 0) return SkillStatus.COOLDOWN;
        if (charges == 0 && recastTicks == 0) return SkillStatus.UNAVAILABLE;
        if (!enoughMana) return SkillStatus.NO_MANA;
        return controlled ? SkillStatus.UNAVAILABLE : SkillStatus.READY;
    }

    public static AbilitySkill selectedPassive(String role, Map<String, Integer> ranks) {
        var passive = FirstGroupSkills.selected(role, 0, ranks);
        return passive != null && ranks.getOrDefault(passive.getId(), 0) > 0 ? passive : null;
    }

    public static String cooldownText(int ticks) {
        if (ticks >= 200) return Integer.toString(Math.ceilDiv(ticks, 20));
        int tenths = Math.ceilDiv(Math.max(0, ticks), 2);
        return tenths / 10 + "." + tenths % 10;
    }
}
