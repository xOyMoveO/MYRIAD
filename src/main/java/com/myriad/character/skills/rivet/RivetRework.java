package com.myriad.character.skills.rivet;

import com.myriad.character.ability.AbilitySkill;
import net.minecraft.server.level.ServerPlayer;

/** Candidate binding and source-owned lifecycle only. */
public final class RivetRework {
    private RivetRework() { }
    public static AbilitySkill bind(AbilitySkill.Definition definition) {
        return switch (definition.index()) {
            case 0 -> new StationArmor(definition);
            case 1 -> new BreachRhythm(definition);
            case 2 -> new ImpactHammer(definition);
            case 3 -> new TacticalAnchor(definition);
            case 4 -> new EmergencyStrut(definition);
            case 5 -> new TowCable(definition);
            case 6 -> new HoldProtocol(definition);
            case 7 -> new BreachCharge(definition);
            default -> throw new IllegalArgumentException("Invalid Rivet candidate");
        };
    }
    public static void tick(ServerPlayer player) {
        if (!"rivet".equals(com.myriad.player.PlayerDataProvider.get(player).getCharacterId())) return;
        TacticalAnchor.tick(player);
        HoldProtocol.tick(player);
    }
    public static void clear(ServerPlayer player) {
        TacticalAnchor.clear(player);
        com.myriad.player.PlayerDataProvider.get(player).setBuff("rivet_tow_cable_recast", 0);
        RivetPush.clear(player);
        StationArmor.clear(player);
        EmergencyStrut.clear(player);
        HoldProtocol.clear(player);
    }
}
