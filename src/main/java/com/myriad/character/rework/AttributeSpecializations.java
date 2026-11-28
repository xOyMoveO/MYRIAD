package com.myriad.character.rework;

import com.myriad.combat.DamageCalc;
import com.myriad.combat.SkillTargets;
import com.myriad.game.BaseRules;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** The ten independent branches of the five purchase tracks. */
public final class AttributeSpecializations {
    public static final int UNLOCK_RANK = 4;
    public static final int UPGRADE_RANK = 8;
    public static final int PURSUIT_HITS = 3;
    public static final int PURSUIT_COOLDOWN_TICKS = 6 * 20;
    public static final String COUNTER_PREFIX = "g1_attribute_specialization_";
    public static final String ACTION_PREFIX = "specialization_";

    public enum Option {
        ARMOR_PIERCE("strength", "armor_pierce", 1, 6, 10),
        PURSUIT("strength", "pursuit", 2, 20, 32),
        MAGIC_PIERCE("wisdom", "magic_pierce", 1, 6, 10),
        BLESSING("wisdom", "blessing", 2, 6, 10),
        FLURRY("speed", "flurry", 1, .08, .12),
        LIGHTFOOT("speed", "lightfoot", 2, .003, .005),
        STEELBONE("defense", "steelbone", 1, 10, 16),
        SPIRIT_ARMOR("defense", "spirit_armor", 2, 10, 16),
        COMBO("cooldown", "combo", 1, 10, 16),
        BREATH("cooldown", "breath", 2, 3, 5);

        public final String attribute;
        public final String id;
        public final int choice;
        public final double initial;
        public final double improved;

        Option(String attribute, String id, int choice, double initial, double improved) {
            this.attribute = attribute;
            this.id = id;
            this.choice = choice;
            this.initial = initial;
            this.improved = improved;
        }

        public String nameKey() { return "screen.myriad.specialization." + id; }
        public String actionId() { return ACTION_PREFIX + id; }
        public double value(int rank, int selected) {
            if (selected != choice || rank < UNLOCK_RANK) return 0;
            return rank >= UPGRADE_RANK ? improved : initial;
        }
        public double value(PlayerData data) {
            return value(data.firstGroupAttribute(attribute), data.specialization(attribute));
        }
    }

    private static final List<Option> OPTIONS = List.of(Option.values());

    private AttributeSpecializations() {}

    public static List<Option> choices(String attribute) {
        return OPTIONS.stream().filter(option -> option.attribute.equals(attribute)).toList();
    }

    public static Option fromAction(String id) {
        if (!id.startsWith(ACTION_PREFIX)) return null;
        return OPTIONS.stream().filter(option -> option.actionId().equals(id)).findFirst().orElse(null);
    }

    public static String choiceError(int rank, int selected) {
        if (rank < UNLOCK_RANK) return "screen.myriad.specialization.locked";
        return selected == 0 ? null : "screen.myriad.specialization.chosen";
    }

    public static boolean choose(ServerPlayer player, PlayerData data, Option option, int expectedRank) {
        int rank = data.firstGroupAttribute(option.attribute);
        if (!BaseRules.canBuyAttributes(player) || rank != expectedRank
                || choiceError(rank, data.specialization(option.attribute)) != null) return false;
        data.setSpecialization(option.attribute, option.choice);
        FirstGroupGrowth.apply(player, data);
        PlayerDataProvider.syncToClient(player);
        return true;
    }

    public static double skillHaste(int rank, int selected) {
        return FirstGroupGrowth.skillHaste(rank) + Option.COMBO.value(rank, selected);
    }

    public static float allySupport(PlayerData data, ServerPlayer caster, LivingEntity target, float amount) {
        if (!(target instanceof ServerPlayer) || target == caster || !SkillTargets.ally(caster, target)) return amount;
        return amount * (1 + (float) Option.BLESSING.value(data) / 100);
    }

    /** Add a separately mitigated noncritical physical component to the accepted basic hit. */
    public static float pursuitDamage(ServerPlayer attacker, PlayerData data, LivingEntity target) {
        double base = Option.PURSUIT.value(data);
        if (base == 0) return 0;
        UUID hero = target instanceof ServerPlayer opponent && SkillTargets.enemy(attacker, target)
                && FirstGroupSkills.active(PlayerDataProvider.get(opponent).getCharacterId()) ? target.getUUID() : null;
        if (!data.trackPursuitHit(hero, attacker.level().getGameTime(),
                PURSUIT_HITS, PURSUIT_COOLDOWN_TICKS)) return 0;
        return DamageCalc.applyMitigation(attacker, target, (float) base, DamageCalc.Kind.PHYSICAL);
    }
}
