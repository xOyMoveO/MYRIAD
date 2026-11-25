package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;

public final class LuoxiaRework {
    public static final String ID = "luoxia";
    private LuoxiaRework() {}

    public static AbilitySkill bind(AbilitySkill.Definition definition) {
        return switch (definition.index()) {
            case 0 -> new ChaseDusk(definition);
            case 1 -> new SteadyDusk(definition);
            case 2 -> new DuskMark(definition);
            case 3 -> new SlantStep(definition);
            case 4 -> new FeatherBurst(definition);
            case 5 -> new LastSun(definition);
            case 6 -> new ThousandFeathers(definition);
            case 7 -> new SunsetLine(definition);
            default -> throw new IllegalArgumentException("Unknown Luoxia candidate " + definition.index());
        };
    }

    public static boolean holdsBow(ServerPlayer p) {
        return ID.equals(PlayerDataProvider.get(p).getCharacterId()) && p.getMainHandItem().is(ModItems.GUIXIA_BOW.get());
    }

    static int rank(ServerPlayer p, int index) {
        return PlayerDataProvider.get(p).skillLevel(FirstGroupSkills.node(ID, index).getId());
    }
}
