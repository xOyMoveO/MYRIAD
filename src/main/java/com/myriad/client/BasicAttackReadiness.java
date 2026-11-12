package com.myriad.client;

import com.myriad.combat.BasicAttackSystem;
import com.myriad.registry.ModAttributes;
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

        if ("shenli".equals(role)) {
            if (ClientDataHolder.buffTicks("shenli_forced_reload") > 0
                    || ClientDataHolder.buffTicks("shenli_w_time") > 0
                    || ClientDataHolder.syncedCounter("shenli_ammo") <= 0) return false;
            // 沈砺现在是即时出弹；该计时只表示发射后的恢复后摇，后摇期间不能再次开火。
            return ClientDataHolder.buffTicks("shenli_basic_windup") <= 0;
        }

        if ("lige".equals(role)) {
            if (!player.isUsingItem()) return true;
            double attackSpeed = Math.max(.1, player.getAttributeValue(ModAttributes.ATTACK_SPEED));
            return Math.floor(player.getTicksUsingItem() * attackSpeed) >= 20;
        }

        if (isMage(role)) {
            if ("mingzhu".equals(role) && ClientDataHolder.buffTicks("mingzhu_channel") > 0) return false;
            int magicTotal = Math.max(1, ClientDataHolder.magicBulletCooldownMax());
            boolean magicReady = ClientDataHolder.magicBulletCooldownNow()
                    <= BasicAttackSystem.earlyReadyRemaining(magicTotal);
            if ("mingzhu".equals(role)) return magicReady;
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
            case "xuanchen", "hanze", "qingluo", "yunsheng", "guanlan", "luoheng", "mingzhu" -> true;
            default -> false;
        };
    }
}
