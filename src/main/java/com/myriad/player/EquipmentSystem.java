package com.myriad.player;

import java.util.Arrays;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import com.myriad.registry.EquipmentRegistry;
import com.myriad.registry.EquipmentRegistry.EquipDef;

/**
 * 独立装备系统逻辑（类似英雄联盟）：
 * 购买（金币）入槽 -> 属性以 AttributeModifier 生效；槽位变化时整体重算。
 */
public final class EquipmentSystem {
    private EquipmentSystem() {}

    /** 购买/合成装备：拥有的低阶组件按原价抵消（消耗组件），支付剩余金额 */
    public static boolean buy(ServerPlayer player, PlayerData data, String equipId) {
        if (com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())) {
            if (equipId.startsWith("g1_attribute_"))
                return com.myriad.character.rework.FirstGroupGrowth.buy(player, data, equipId.substring(13));
            player.sendSystemMessage(Component.translatable("message.myriad.g1.equipment_retired"));
            return false;
        }
        if(!com.myriad.game.BaseRules.canShop(player)){
            com.myriad.game.MatchMessages.send(player,
                    com.myriad.game.MatchMessages.Tone.WARNING,
                    Component.translatable("message.myriad.shop_base_only"));return false;
        }
        EquipDef def = EquipmentRegistry.get(equipId);
        if (def == null) {
            return false;
        }
        // 统计已拥有组件（数量匹配，逐个抵消）
        java.util.Map<String, Integer> have = new java.util.HashMap<>();
        for (String id : data.getEquipmentSlots()) {
            if (!id.isEmpty()) {
                have.merge(id, 1, Integer::sum);
            }
        }
        java.util.Map<String, Integer> removeCount = new java.util.HashMap<>();
        int discount = 0;
        for (String ing : def.ingredients()) {
            int owned = have.getOrDefault(ing, 0);
            if (owned > 0) {
                have.put(ing, owned - 1);
                removeCount.merge(ing, 1, Integer::sum);
                EquipDef idDef = EquipmentRegistry.get(ing);
                if (idDef != null) {
                    discount += idDef.totalPrice();
                }
            }
        }
        // 靴子类唯一：排除将被消耗的组件（合成靴子时组件靴子不冲突）
        if (EquipmentRegistry.isBoots(equipId)) {
            for (String id : data.getEquipmentSlots()) {
                if (EquipmentRegistry.isBoots(id) && !removeCount.containsKey(id)) {
                    com.myriad.game.MatchMessages.send(player,
                            com.myriad.game.MatchMessages.Tone.WARNING,
                            Component.translatable("message.myriad.equip_boots_only_one"));
                    return false;
                }
            }
        }
        int pay = Math.max(0, def.totalPrice() - discount);
        // 训练模式无限金币：不扣金币
        boolean free = com.myriad.game.TrainingMode.isEnabled()
                && com.myriad.game.TrainingMode.isNoGoldCost();
        if (!free && data.getGold() < pay) {
            com.myriad.game.MatchMessages.send(player,
                    com.myriad.game.MatchMessages.Tone.WARNING,
                    Component.translatable("message.myriad.equip_no_gold"));
            return false;
        }
        // 移除被抵消的组件
        for (java.util.Map.Entry<String, Integer> e : removeCount.entrySet()) {
            int left = e.getValue();
            for (int i = 0; i < EquipmentRegistry.SLOT_COUNT && left > 0; i++) {
                if (e.getKey().equals(data.getEquipmentSlot(i))) {
                    data.setEquipmentSlot(i, "");
                    left--;
                }
            }
        }
        int slot = data.firstEmptySlot();
        if (slot < 0) {
            com.myriad.game.MatchMessages.send(player,
                    com.myriad.game.MatchMessages.Tone.WARNING,
                    Component.translatable("message.myriad.equip_full"));
            return false;
        }
        if (!free) {
            data.spendGold(pay);
        }
        data.setEquipmentSlot(slot, equipId);
        apply(player, data);
        com.myriad.game.MatchMessages.send(player,
                com.myriad.game.MatchMessages.Tone.SUCCESS, Component.translatable(
                removeCount.isEmpty() ? "message.myriad.equip_bought" : "message.myriad.craft_done",
                Component.translatable(def.nameKey())));
        return true;
    }

    /** 按当前槽位装备整体重算属性（直接修改 base 值：base 同步可靠，modifier 同步在 26.2 不可靠） */
    public static void apply(ServerPlayer player, PlayerData data) {
        if (com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())) {
            for (var attribute : EquipmentRegistry.allStatsAttrs()) {
                AttributeInstance inst = player.getAttribute(attribute);
                String key = attribute.value().getDescriptionId();
                if (inst != null) inst.setBaseValue(inst.getBaseValue() - data.getEquipApplied(key));
            }
            data.resetEquipApplied();
            com.myriad.character.rework.FirstGroupGrowth.apply(player, data);
            return;
        }
        // 1) 计算每属性的新总和：加法求和 / 乘算求和（乘算基于无装备基础值）
        java.util.Map<String, Double> addTotals = new java.util.HashMap<>();
        java.util.Map<String, Double> multTotals = new java.util.HashMap<>();
        for (String equipId : data.getEquipmentSlots()) {
            if (equipId == null || equipId.isEmpty()) {
                continue;
            }
            EquipDef def = EquipmentRegistry.get(equipId);
            if (def == null) {
                continue;
            }
            def.stats().forEach((attr, stat) -> {
                String key = attr.value().getDescriptionId();
                if (stat.multiply()) {
                    multTotals.merge(key, stat.amount(), Double::sum);
                } else {
                    addTotals.merge(key, stat.amount(), Double::sum);
                }
            });
        }
        // 2) 还原旧加成，应用新加成（差值法；遍历全部可能被装备影响的属性，清空装备时也能还原）
        java.util.Set<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>> involved =
                EquipmentRegistry.allStatsAttrs();
        boolean changed = false;
        for (net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr : involved) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) {
                continue;
            }
            String key = attr.value().getDescriptionId();
            double old = data.getEquipApplied(key);
            double baseNoEquip = inst.getBaseValue() - old;
            double addSum = addTotals.getOrDefault(key, 0.0);
            double multSum = multTotals.getOrDefault(key, 0.0);
            double newApplied = addSum + baseNoEquip * multSum;
            if (old != newApplied) {
                inst.setBaseValue(baseNoEquip + newApplied);
                data.setEquipApplied(key, newApplied);
                changed = true;
            }
        }
        // 3) 主动推送属性同步包（26.2 中 base 变化不保证自动同步）
        if (changed) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket(
                    player.getId(), player.getAttributes().getSyncableAttributes()));
        }
        PlayerHealthSystem.recalculate(player);
    }

    /** 出售槽位装备：返还总价 70% 并清空槽位 */
    public static boolean sell(ServerPlayer player, PlayerData data, int slot) {
        if (com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())) return false;
        String equipId = data.getEquipmentSlot(slot);
        EquipDef def = EquipmentRegistry.get(equipId);
        if (equipId.isEmpty() || def == null) {
            return false;
        }
        data.setEquipmentSlot(slot, "");
        data.addGold((int) Math.round(def.totalPrice() * 0.7));
        apply(player, data);
        com.myriad.game.MatchMessages.send(player,
                com.myriad.game.MatchMessages.Tone.SUCCESS,
                Component.translatable("message.myriad.equip_sold",
                        Component.translatable(def.nameKey()), (int) Math.round(def.totalPrice() * 0.7)));
        return true;
    }
}
