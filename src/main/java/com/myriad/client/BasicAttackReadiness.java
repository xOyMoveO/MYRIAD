package com.myriad.client;

import com.myriad.combat.BasicAttackSystem;
import net.minecraft.client.player.LocalPlayer;

/** 客户端普攻可用性提示；服务端仍会独立校验全部攻击条件。 */
public final class BasicAttackReadiness {
    private BasicAttackReadiness() { }

    public static boolean canAttack(LocalPlayer player) {
        var data = ClientDataHolder.get();
        if (!ClientDataHolder.modEnabled() || data == null) return true;
        if (!player.isAlive() || player.isSpectator()
                || ClientDataHolder.buffTicks("hero_respawn") > 0
                || ClientDataHolder.placementType() != 0) return false;
        String role = data.characterId();
        if (role == null || role.isBlank()) return false;
        // Keep vanilla visuals, but reject incomplete recovery instead of producing weak attacks.
        if(com.myriad.combat.VanillaMeleeAttack.usesVanilla(com.myriad.character.CharacterType.byId(role)))
            return player.getAttackStrengthScale(.5f)>=1&&!ClientDataHolder.isChanneling()&&ClientDataHolder.buffTicks("g1_stun")<=0;

        if (isMage(role)) {

            int magicTotal = Math.max(1, ClientDataHolder.magicBulletCooldownMax());
            boolean magicReady = ClientDataHolder.magicBulletCooldownNow()
                    <= BasicAttackSystem.earlyReadyRemaining(magicTotal);

            int physicalTotal = Math.max(1, data.basicAttackCooldownMax());
            boolean physicalReady = ClientDataHolder.basicAttackCooldownNow()
                    <= BasicAttackSystem.earlyReadyRemaining(physicalTotal);
            return magicReady || physicalReady;
        }

        int total = Math.max(1, data.basicAttackCooldownMax());
        return ClientDataHolder.basicAttackCooldownNow() <= BasicAttackSystem.earlyReadyRemaining(total);
    }

    private static boolean isMage(String role) {
        return switch (role) {
            case "xuanchen", "hanze", "qingluo", "yunsheng" -> true;
            default -> false;
        };
    }
}
