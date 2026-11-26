package com.myriad.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;

/** Protocol 27 attribute-key dictionary. Zero carries a literal, including unknown future names. */
final class HudAttributeKeys {
    private static final List<String> KEYS = List.of(
            "attribute.name.max_health", "attribute.name.movement_speed",
            "attribute.myriad.physical_attack", "attribute.myriad.physical_defense",
            "attribute.myriad.magic_resistance", "attribute.myriad.attack_speed",
            "attribute.myriad.spell_power", "attribute.myriad.crit_chance",
            "attribute.myriad.crit_damage", "attribute.myriad.physical_lifesteal",
            "attribute.myriad.tenacity", "attribute.myriad.flat_armor_pen",
            "attribute.myriad.percent_armor_pen", "attribute.myriad.flat_magic_pen",
            "attribute.myriad.percent_magic_pen", "attribute.myriad.max_mana",
            "attribute.myriad.mana_regen", "attribute.myriad.cooldown_reduction",
            "attribute.myriad.health_regen", "attribute.myriad.spell_lifesteal");
    private static final Map<String,Integer> IDS = new HashMap<>();
    static {
        for (int i = 0; i < KEYS.size(); i++) IDS.put(KEYS.get(i), i + 1);
    }

    private HudAttributeKeys() {}

    static void write(FriendlyByteBuf buffer, String key) {
        int id = IDS.getOrDefault(key, 0);
        buffer.writeVarInt(id);
        if (id == 0) buffer.writeUtf(key);
    }

    static String read(FriendlyByteBuf buffer) {
        int id = buffer.readVarInt();
        if (id == 0) return buffer.readUtf();
        if (id < 0 || id > KEYS.size()) throw new IllegalArgumentException("Unknown HUD attribute index " + id);
        return KEYS.get(id - 1);
    }
}
