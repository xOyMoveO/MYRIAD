package com.myriad.player;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 装备特殊机制效果引擎（服务端）：
 * 疾风靴（脱战额外 +5% 移速）、兽皮足（6% 物理伤害减免）。
 */
public final class EquipmentEffects {
    private EquipmentEffects() {}

    /** 疾风靴脱战移速 modifier id */
    private static final Identifier JIFENG_ID = Identifier.parse("myriad:eq_jifeng_outofcombat");

    /** 每 tick：疾风靴脱战额外移速（脱战 = 5 秒未进战斗） */
    public static void tick(ServerPlayer player) {
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null) {
            return;
        }
        boolean hasJifeng = !com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId()) && hasEquip(data, "jifengxue");
        AttributeInstance ms = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms == null) {
            return;
        }
        if (hasJifeng) {
            boolean outOfCombat = data.getLastCombatGameTime() < 0
                    || player.level().getGameTime() - data.getLastCombatGameTime() > 100;
            if (outOfCombat) {
                if (!ms.hasModifier(JIFENG_ID)) {
                    ms.addTransientModifier(new AttributeModifier(
                            JIFENG_ID, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            } else if (ms.hasModifier(JIFENG_ID)) {
                ms.removeModifier(JIFENG_ID);
            }
        } else if (ms.hasModifier(JIFENG_ID)) {
            ms.removeModifier(JIFENG_ID);
        }
    }

    /** 受伤事件：兽皮足物理伤害减免 6% */
    public static void onIncomingDamage(ServerPlayer victim, LivingIncomingDamageEvent event) {
        PlayerData data = PlayerDataProvider.get(victim);
        if (data != null && com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())) return;
        if (data == null || !hasEquip(data, "shoupizu") || event.getAmount() <= 0) {
            return;
        }
        // 物理伤害（非魔法/真实标记外均按物理处理）
        if (!com.myriad.combat.NpcCombat.isMagic(event.getSource())
                && !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                && !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)) {
            event.getContainer().setNewDamage(event.getAmount() * 0.94f);
        }
    }

    private static boolean hasEquip(PlayerData data, String equipId) {
        for (String id : data.getEquipmentSlots()) {
            if (equipId.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
