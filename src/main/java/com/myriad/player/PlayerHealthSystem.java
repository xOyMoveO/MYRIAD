package com.myriad.player;

import com.myriad.character.CharacterType;
import com.myriad.registry.EquipmentRegistry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** 玩家模组生命上限：绕过原版 MAX_HEALTH 的 1024 硬上限。 */
public final class PlayerHealthSystem {
    private PlayerHealthSystem() {}

    /** 按角色、等级和装备重新计算模组最大生命。 */
    public static void recalculate(ServerPlayer player) {
        PlayerData data = PlayerDataProvider.get(player);
        CharacterType type = CharacterType.byId(data.getCharacterId());
        if (type == null) {
            data.setModMaxHealth(20.0F);
            return;
        }

        double base = CharacterType.balancedInitialHp(type);
        double growth = type.growthHp() * Math.max(0, data.getLevel() - 1);
        boolean reworked = com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId());
        if (reworked) {
            base = com.myriad.character.rework.FirstGroupGrowth.health(data.getCharacterId(), data.getLevel());
            growth = 0;
        }
        double additive = 0.0;
        double multiplier = 0.0;
        String healthId = Attributes.MAX_HEALTH.value().getDescriptionId();
        for (String equipmentId : data.getEquipmentSlots()) {
            if (reworked) break;
            EquipmentRegistry.EquipDef def = EquipmentRegistry.get(equipmentId);
            if (def == null) continue;
            for (var entry : def.stats().entrySet()) {
                if (!healthId.equals(entry.getKey().value().getDescriptionId())) continue;
                if (entry.getValue().multiply()) multiplier += entry.getValue().amount();
                else additive += entry.getValue().amount();
            }
        }
        float oldMax = data.getModMaxHealth();
        float newMax = (float)Math.max(1.0, base + growth + additive + base * multiplier);
        data.setModMaxHealth(newMax);

        if (!data.isModHealthInitialized()) {
            data.setModHealthInitialized(true);
            player.setHealth(newMax);
        } else if (player.getHealth() > newMax) {
            player.setHealth(newMax);
        } else if (oldMax <= 0.0F) {
            player.setHealth(newMax);
        }
    }

    /** 选角或重生时回满模组生命；升级只重算上限。 */
    public static void fill(ServerPlayer player) {
        recalculate(player);
        player.setHealth(PlayerDataProvider.get(player).getModMaxHealth());
    }
}
