package com.myriad.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import com.myriad.Myriad;

/**
 * 独立装备系统（类似英雄联盟，与原版装备体系无关）。
 * 每名玩家拥有 6 个独立装备槽，装备通过商店（金币）购买获得，
 * 属性以 AttributeModifier 直接加成到玩家属性上（每次应用时新建 modifier 实例，避免跨实体复用）。
 * 当前实现凡品/仙品/极品共 74 件；靴子类仅能拥有一件。
 */
public final class EquipmentRegistry {
    private EquipmentRegistry() {}

    /** 单项属性加成描述 */
    public record Stat(double amount, boolean multiply) {
        public AttributeModifier.Operation operation() {
            return multiply ? AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    : AttributeModifier.Operation.ADD_VALUE;
        }
    }

    /** 装备定义 */
    public record EquipDef(String id, String nameKey, int tier, int price,
            String[] ingredients, Map<Holder<Attribute>, Stat> stats) {
        /** price 直接表示完整购买价；拥有组件时由购买逻辑按组件完整价格抵扣。 */
        public int totalPrice() {
            return price;
        }

        /** 该装备某属性的 modifier id（每次应用时用新 id 重建） */
        public String modifierId(Holder<Attribute> attr) {
            String path = attr.value().getDescriptionId().replace('.', '_');
            return Identifier.fromNamespaceAndPath(Myriad.MODID, "eq_" + id + "_" + path).toString();
        }
    }

    /** 品级：1=凡品 2=仙品 3=极品 */
    public static final int TIER_COMMON = 1;
    public static final int TIER_FINE = 2;
    public static final int TIER_LEGEND = 3;
    public static final int LEGEND_MINIMUM_COMBINE_COST = 1200;

    private static final Map<String, EquipDef> DEFS = new LinkedHashMap<>();

    private static void def(String id, String nameKey, int tier, int price, Object... stats) {
        def(id, nameKey, tier, price, new String[0], stats);
    }

    /** 定义带合成配方的装备（仙品/极品：由 1-3 件低阶装备合成） */
    private static void def(String id, String nameKey, int tier, int price, String[] ingredients, Object... stats) {
        Map<Holder<Attribute>, Stat> map = new LinkedHashMap<>();
        for (int i = 0; i + 2 < stats.length; i += 3) {
            Holder<Attribute> attr = (Holder<Attribute>) stats[i];
            map.put(attr, new Stat((Double) stats[i + 1], (Boolean) stats[i + 2]));
        }
        DEFS.put(id, new EquipDef(id, nameKey, tier, price, ingredients, map));
    }

    static {
        DEFS.put("jungle_blade", new EquipDef("jungle_blade", "equipment.myriad.jungle_blade", TIER_COMMON, 450, new String[0], Map.of()));
        // ================= 凡品 =================
        def("qingfeng", "equipment.myriad.qingfeng", TIER_COMMON, 220,
                ModAttributes.PHYSICAL_ATTACK, 15.75, false);
        def("cuifeng", "equipment.myriad.cuifeng", TIER_COMMON, 220,
                ModAttributes.PHYSICAL_ATTACK, 6.3, false,
                ModAttributes.CRIT_CHANCE, 3.0, false);
        def("duangong", "equipment.myriad.duangong", TIER_COMMON, 220,
                ModAttributes.ATTACK_SPEED, 0.075, true);
        def("xuantie", "equipment.myriad.xuantie", TIER_COMMON, 260,
                ModAttributes.PHYSICAL_DEFENSE, 30.0, false);
        def("lingwen", "equipment.myriad.lingwen", TIER_COMMON, 260,
                ModAttributes.MAGIC_RESISTANCE, 30.0, false);
        def("tayun", "equipment.myriad.tayun", TIER_COMMON, 220,
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.08, true);
        def("ningxue", "equipment.myriad.ningxue", TIER_COMMON, 220,
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 144.0, false);
        def("juling", "equipment.myriad.juling", TIER_COMMON, 220,
                ModAttributes.MAX_MANA, 280.0, false);
        def("qingyu", "equipment.myriad.qingyu", TIER_COMMON, 220,
                ModAttributes.HEALTH_REGEN, 3.5, false);
        def("powang", "equipment.myriad.powang", TIER_COMMON, 220,
                ModAttributes.CRIT_DAMAGE, 8.0, false);
        def("xiaoxuelian", "equipment.myriad.xiaoxuelian", TIER_COMMON, 450,
                ModAttributes.PHYSICAL_LIFESTEAL, 4.0, false);
        def("fashudianji", "equipment.myriad.fashudianji", TIER_COMMON, 260,
                ModAttributes.SPELL_POWER, 18.0, false);
        def("wushuzhiye", "equipment.myriad.wushuzhiye", TIER_COMMON, 220,
                ModAttributes.COOLDOWN_REDUCTION, 3.0, false);

        // ================= 仙品（凡品合成，1-3 件） =================
        def("pojun", "equipment.myriad.pojun", TIER_FINE, 600,
                new String[] { "qingfeng", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 50.4, false);
        def("yingsha", "equipment.myriad.yingsha", TIER_FINE, 980,
                new String[] { "cuifeng", "powang" },
                ModAttributes.PHYSICAL_ATTACK, 31.5, false,
                ModAttributes.CRIT_CHANCE, 15.0, false);
        def("budong", "equipment.myriad.budong", TIER_FINE, 980,
                new String[] { "xuantie", "ningxue" },
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 384.0, false,
                ModAttributes.PHYSICAL_DEFENSE, 50.0, false,
                ModAttributes.TENACITY, 15.0, false);
        def("fengyu", "equipment.myriad.fengyu", TIER_FINE, 920,
                new String[] { "lingwen", "juling" },
                ModAttributes.MAX_MANA, 560.0, false,
                ModAttributes.MAGIC_RESISTANCE, 50.0, false,
                ModAttributes.FLAT_MAGIC_PEN, 12.0, false);
        def("zhuifeng", "equipment.myriad.zhuifeng", TIER_FINE, 660,
                new String[] { "lingwen" },
                ModAttributes.MAGIC_RESISTANCE, 20.0, false,
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.18, true,
                ModAttributes.TENACITY, 10.0, false);
        def("chuanyun", "equipment.myriad.chuanyun", TIER_FINE, 560,
                new String[] { "qingfeng", "cuifeng" },
                ModAttributes.PHYSICAL_ATTACK, 18.9, false,
                ModAttributes.FLAT_ARMOR_PEN, 8.0, false);
        def("xuesha", "equipment.myriad.xuesha", TIER_FINE, 720,
                new String[] { "ningxue", "xiaoxuelian" },
                ModAttributes.SPELL_POWER, 11.25, false,
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 144.0, false,
                ModAttributes.SPELL_LIFESTEAL, 8.0, false);
        def("qingzhu", "equipment.myriad.qingzhu", TIER_FINE, 560,
                new String[] { "ningxue", "qingyu" },
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 96.0, false,
                ModAttributes.HEALTH_REGEN, 7.0, false);
        def("jisuxue", "equipment.myriad.jisuxue", TIER_FINE, 620,
                new String[] { "duangong" },
                ModAttributes.ATTACK_SPEED, 0.13, true,
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.18, true);
        def("chuantouxue", "equipment.myriad.chuantouxue", TIER_FINE, 600,
                new String[] { "cuifeng" },
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.18, true,
                ModAttributes.FLAT_ARMOR_PEN, 5.0, false,
                ModAttributes.FLAT_MAGIC_PEN, 5.0, false);
        def("jifengxue", "equipment.myriad.jifengxue", TIER_FINE, 560,
                new String[] { "ningxue", "qingyu" },
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.2, true);
        def("shoupizu", "equipment.myriad.shoupizu", TIER_FINE, 560,
                new String[] { "xuantie" },
                ModAttributes.PHYSICAL_DEFENSE, 20.0, false,
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.18, true);
        def("piaomiaoxue", "equipment.myriad.piaomiaoxue", TIER_FINE, 560,
                new String[] { "wushuzhiye" },
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.13, true,
                ModAttributes.COOLDOWN_REDUCTION, 15.0, false);
        def("fengbaodabang", "equipment.myriad.fengbaodabang", TIER_FINE, 620,
                new String[] { "fashudianji", "fashudianji" },
                ModAttributes.SPELL_POWER, 45.0, false);
        def("wushudianji", "equipment.myriad.wushudianji", TIER_FINE, 580,
                new String[] { "fashudianji", "wushuzhiye" },
                ModAttributes.SPELL_POWER, 33.75, false,
                ModAttributes.COOLDOWN_REDUCTION, 5.0, false);
        def("powangzhijian", "equipment.myriad.powangzhijian", TIER_FINE, 560,
                new String[] { "powang", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 23.625, false,
                ModAttributes.CRIT_DAMAGE, 20.0, false);
        def("zhanfu", "equipment.myriad.zhanfu", TIER_FINE, 740,
                new String[] { "ningxue", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 31.5, false,
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 336.0, false);
        def("sujiqiang", "equipment.myriad.sujiqiang", TIER_FINE, 560,
                new String[] { "duangong", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 12.6, false,
                ModAttributes.ATTACK_SPEED, 0.2, true);
        def("baojidao", "equipment.myriad.baojidao", TIER_FINE, 640,
                new String[] { "duangong", "cuifeng" },
                ModAttributes.ATTACK_SPEED, 0.16, true,
                ModAttributes.CRIT_CHANCE, 10.0, false);
        def("lanshibao", "equipment.myriad.lanshibao", TIER_FINE, 560,
                new String[] { "juling", "juling" },
                ModAttributes.MAX_MANA, 560.0, false);
        def("yuansushouzhuo", "equipment.myriad.yuansushouzhuo", TIER_FINE, 560,
                new String[] { "wushuzhiye", "juling" },
                ModAttributes.MANA_REGEN, 35.0, false,
                ModAttributes.COOLDOWN_REDUCTION, 10.0, false);
        def("manaoshouzhuo", "equipment.myriad.manaoshouzhuo", TIER_FINE, 560,
                new String[] { "qingyu", "ningxue" },
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 192.0, false,
                ModAttributes.HEALTH_REGEN, 10.5, false);
        def("jisuzhikai", "equipment.myriad.jisuzhikai", TIER_FINE, 560,
                new String[] { "wushuzhiye", "ningxue" },
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 480.0, false,
                ModAttributes.COOLDOWN_REDUCTION, 10.0, false);
        def("liliangyaodai", "equipment.myriad.liliangyaodai", TIER_FINE, 800,
                new String[] { "ningxue", "ningxue", "ningxue" },
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 720.0, false);
        def("maoxianhubi", "equipment.myriad.maoxianhubi", TIER_FINE, 840,
                new String[] { "xuantie", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 31.5, false,
                ModAttributes.PHYSICAL_DEFENSE, 60.0, false);

        // 新增仙品过渡装备：每条新增极品路线均有明确中期出装。
        def("polingzhui", "equipment.myriad.polingzhui", TIER_FINE, 700,
                new String[] { "fashudianji", "lingwen" },
                ModAttributes.SPELL_POWER, 33.75, false,
                ModAttributes.FLAT_MAGIC_PEN, 12.0, false);
        def("lingyuanren", "equipment.myriad.lingyuanren", TIER_FINE, 620,
                new String[] { "qingfeng", "juling" },
                ModAttributes.PHYSICAL_ATTACK, 28.35, false,
                ModAttributes.MAX_MANA, 560.0, false);
        def("xuanshuigui", "equipment.myriad.xuanshuigui", TIER_FINE, 780,
                new String[] { "xuantie", "juling" },
                ModAttributes.PHYSICAL_DEFENSE, 50.0, false,
                ModAttributes.MANA_REGEN, 42.0, false);
        def("lingxihu", "equipment.myriad.lingxihu", TIER_FINE, 780,
                new String[] { "lingwen", "juling" },
                ModAttributes.MAGIC_RESISTANCE, 50.0, false,
                ModAttributes.MANA_REGEN, 42.0, false);
        def("xinghuiyulu", "equipment.myriad.xinghuiyulu", TIER_FINE, 580,
                new String[] { "fashudianji", "wushuzhiye" },
                ModAttributes.SPELL_POWER, 40.5, false,
                ModAttributes.COOLDOWN_REDUCTION, 8.0, false);
        def("jifengren", "equipment.myriad.jifengren", TIER_FINE, 560,
                new String[] { "qingfeng", "wushuzhiye" },
                ModAttributes.PHYSICAL_ATTACK, 28.35, false,
                ModAttributes.COOLDOWN_REDUCTION, 8.0, false);
        def("bixialingjing", "equipment.myriad.bixialingjing", TIER_FINE, 980,
                new String[] { "lingwen", "ningxue" },
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 384.0, false,
                ModAttributes.MAGIC_RESISTANCE, 70.0, false);
        def("zhenlingfaqi", "equipment.myriad.zhenlingfaqi", TIER_FINE, 580,
                new String[] { "lingwen", "wushuzhiye" },
                ModAttributes.MAGIC_RESISTANCE, 70.0, false,
                ModAttributes.COOLDOWN_REDUCTION, 8.0, false);
        def("shixueyaodao", "equipment.myriad.shixueyaodao", TIER_FINE, 740,
                new String[] { "xiaoxuelian", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 28.35, false,
                ModAttributes.PHYSICAL_LIFESTEAL, 10.0, false);

        // ================= 极品（仙品 + 凡品，1-3 件） =================
        def("zhuxian", "equipment.myriad.zhuxian", TIER_LEGEND, 2440,
                new String[] { "pojun", "duangong" },
                ModAttributes.PHYSICAL_ATTACK, 128.5, false,
                ModAttributes.ATTACK_SPEED, 0.27, true,
                ModAttributes.FLAT_ARMOR_PEN, 20.0, false);
        def("youming", "equipment.myriad.youming", TIER_LEGEND, 3000,
                new String[] { "yingsha", "powang" },
                ModAttributes.PHYSICAL_ATTACK, 78.5, false,
                ModAttributes.CRIT_CHANCE, 37.0, false,
                ModAttributes.CRIT_DAMAGE, 36.0, false);
        def("hunyuan", "equipment.myriad.hunyuan", TIER_LEGEND, 3000,
                new String[] { "budong", "lingwen" },
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 900.0, false,
                ModAttributes.PHYSICAL_DEFENSE, 125.0, false,
                ModAttributes.MAGIC_RESISTANCE, 100.0, false,
                ModAttributes.TENACITY, 15.0, false);
        def("jiutian", "equipment.myriad.jiutian", TIER_LEGEND, 3000,
                new String[] { "fengyu", "fashudianji" },
                ModAttributes.SPELL_POWER, 95.0, false,
                ModAttributes.MAX_MANA, 1170.0, false,
                ModAttributes.MAGIC_RESISTANCE, 50.0, false,
                ModAttributes.FLAT_MAGIC_PEN, 20.0, false,
                ModAttributes.PERCENT_MAGIC_PEN, 11.0, false);
        def("wuji", "equipment.myriad.wuji", TIER_LEGEND, 2000,
                new String[] { "zhuifeng" },
                ModAttributes.MAGIC_RESISTANCE, 45.0, false,
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.41, true,
                ModAttributes.TENACITY, 34.0, false);
        def("suixing", "equipment.myriad.suixing", TIER_LEGEND, 2000,
                new String[] { "chuanyun", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 71.5, false,
                ModAttributes.FLAT_ARMOR_PEN, 30.0, false,
                ModAttributes.PERCENT_ARMOR_PEN, 16.0, false);
        def("tiangang", "equipment.myriad.tiangang", TIER_LEGEND, 2880,
                new String[] { "fengyu", "ningxue" },
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 1100.0, false,
                ModAttributes.MAX_MANA, 560.0, false,
                ModAttributes.MAGIC_RESISTANCE, 130.0, false,
                ModAttributes.FLAT_MAGIC_PEN, 12.0, false);
        def("piaomiaozhijian", "equipment.myriad.piaomiaozhijian", TIER_LEGEND, 3000,
                new String[] { "yingsha", "cuifeng" },
                ModAttributes.PHYSICAL_ATTACK, 74.0, false,
                ModAttributes.CRIT_CHANCE, 51.0, false);
        def("shihunshu", "equipment.myriad.shihunshu", TIER_LEGEND, 3000,
                new String[] { "xuesha", "fashudianji" },
                ModAttributes.SPELL_POWER, 117.5, false,
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 144.0, false,
                ModAttributes.SPELL_LIFESTEAL, 28.0, false);
        def("shengshengzhibi", "equipment.myriad.shengshengzhibi", TIER_LEGEND, 2740,
                new String[] { "baojidao", "powang" },
                ModAttributes.ATTACK_SPEED, 0.46, true,
                ModAttributes.CRIT_CHANCE, 35.0, false,
                ModAttributes.CRIT_DAMAGE, 38.0, false);
        def("kuangbaozhigong", "equipment.myriad.kuangbaozhigong", TIER_LEGEND, 2200,
                new String[] { "sujiqiang", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 105.0, false,
                ModAttributes.ATTACK_SPEED, 0.53, true);
        def("changfengzhiyi", "equipment.myriad.changfengzhiyi", TIER_LEGEND, 2000,
                new String[] { "sujiqiang", "ningxue" },
                ModAttributes.PHYSICAL_ATTACK, 13.5, false,
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 670.0, false,
                ModAttributes.ATTACK_SPEED, 0.2, true);
        def("mimizhidian", "equipment.myriad.mimizhidian", TIER_LEGEND, 3000,
                new String[] { "wushudianji", "powang" },
                ModAttributes.SPELL_POWER, 118.5, false,
                ModAttributes.CRIT_CHANCE, 19.0, false,
                ModAttributes.CRIT_DAMAGE, 36.0, false,
                ModAttributes.COOLDOWN_REDUCTION, 5.0, false);
        def("fengmingzhijie", "equipment.myriad.fengmingzhijie", TIER_LEGEND, 2000,
                new String[] { "yuansushouzhuo", "juling" },
                ModAttributes.MAX_MANA, 1810.0, false,
                ModAttributes.MANA_REGEN, 115.0, false,
                ModAttributes.COOLDOWN_REDUCTION, 11.0, false);
        def("jijia", "equipment.myriad.jijia", TIER_LEGEND, 2020,
                new String[] { "jisuzhikai", "xuantie" },
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 1030.0, false,
                ModAttributes.PHYSICAL_DEFENSE, 95.0, false,
                ModAttributes.COOLDOWN_REDUCTION, 27.0, false);
        def("jihandunjian", "equipment.myriad.jihandunjian", TIER_LEGEND, 3000,
                new String[] { "zhanfu", "xuantie" },
                ModAttributes.PHYSICAL_ATTACK, 48.5, false,
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 1370.0, false,
                ModAttributes.PHYSICAL_DEFENSE, 100.0, false);
        def("bawangkaijia", "equipment.myriad.bawangkaijia", TIER_LEGEND, 3000,
                new String[] { "liliangyaodai", "ningxue" },
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 2610.0, false);
        def("baoliehufu", "equipment.myriad.baoliehufu", TIER_LEGEND, 2680,
                new String[] { "maoxianhubi", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 110.0, false,
                ModAttributes.PHYSICAL_DEFENSE, 160.0, false);
        def("jidaopojun", "equipment.myriad.jidaopojun", TIER_LEGEND, 2360,
                new String[] { "pojun", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 192.5, false);

        // 新增极品装备：全部由上方仙品过渡件继续合成。
        def("taixupolingzhang", "equipment.myriad.taixupolingzhang", TIER_LEGEND, 3000,
                new String[] { "polingzhui", "fashudianji" },
                ModAttributes.SPELL_POWER, 141.5, false,
                ModAttributes.FLAT_MAGIC_PEN, 30.0, false,
                ModAttributes.PERCENT_MAGIC_PEN, 16.0, false);
        def("canglanzhanren", "equipment.myriad.canglanzhanren", TIER_LEGEND, 2600,
                new String[] { "lingyuanren", "juling" },
                ModAttributes.PHYSICAL_ATTACK, 115.5, false,
                ModAttributes.MAX_MANA, 2250.0, false);
        def("liangyihudun", "equipment.myriad.liangyihudun", TIER_LEGEND, 3000,
                new String[] { "xuanshuigui", "lingxihu" },
                ModAttributes.PHYSICAL_DEFENSE, 140.0, false,
                ModAttributes.MAGIC_RESISTANCE, 140.0, false,
                ModAttributes.MANA_REGEN, 84.0, false);
        def("tianyandaodian", "equipment.myriad.tianyandaodian", TIER_LEGEND, 2780,
                new String[] { "xinghuiyulu", "fashudianji" },
                ModAttributes.SPELL_POWER, 194.0, false,
                ModAttributes.COOLDOWN_REDUCTION, 20.0, false);
        def("wuxiangjianxia", "equipment.myriad.wuxiangjianxia", TIER_LEGEND, 2000,
                new String[] { "jifengren", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 149.5, false,
                ModAttributes.COOLDOWN_REDUCTION, 22.0, false);
        def("wanfaguixinpao", "equipment.myriad.wanfaguixinpao", TIER_LEGEND, 3000,
                new String[] { "bixialingjing", "lingwen" },
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 1150.0, false,
                ModAttributes.MAGIC_RESISTANCE, 190.0, false,
                ModAttributes.TENACITY, 11.0, false);
        def("taixuzhenlingfan", "equipment.myriad.taixuzhenlingfan", TIER_LEGEND, 3000,
                new String[] { "zhenlingfaqi", "lingwen" },
                ModAttributes.SPELL_POWER, 63.0, false,
                ModAttributes.MAGIC_RESISTANCE, 255.0, false,
                ModAttributes.COOLDOWN_REDUCTION, 26.0, false);
        def("xueyuhunren", "equipment.myriad.xueyuhunren", TIER_LEGEND, 2940,
                new String[] { "shixueyaodao", "qingfeng" },
                ModAttributes.PHYSICAL_ATTACK, 131.0, false,
                ModAttributes.PHYSICAL_LIFESTEAL, 32.0, false);

        validateLegendTransitions();
    }

    /** 极品至少包含一件仙品组件，防止后期出装出现无法平滑花钱的断层。 */
    private static void validateLegendTransitions() {
        for (EquipDef def : DEFS.values()) {
            if (def.tier() != TIER_LEGEND) continue;
            boolean hasFine = false;
            int componentTotal = 0;
            for (String ingredient : def.ingredients()) {
                EquipDef component = get(ingredient);
                if (component != null) componentTotal += component.totalPrice();
                if (component != null && component.tier() == TIER_FINE) {
                    hasFine = true;
                }
            }
            if (def.totalPrice() - componentTotal < LEGEND_MINIMUM_COMBINE_COST) {
                throw new IllegalStateException("Legend equipment combine cost below minimum: " + def.id());
            }
            if (!hasFine) {
                throw new IllegalStateException("Legend equipment lacks fine transition: " + def.id());
            }
        }
    }

    /** 装备槽数量 */
    public static final int SLOT_COUNT = 6;

    public static EquipDef get(String id) {
        return DEFS.get(id);
    }

    public static java.util.Collection<EquipDef> all() {
        return DEFS.values();
    }

    /** 按品级筛选装备 */
    public static java.util.List<EquipDef> byTier(int tier) {
        java.util.List<EquipDef> result = new java.util.ArrayList<>();
        for (EquipDef def : DEFS.values()) {
            if (def.tier() == tier) {
                result.add(def);
            }
        }
        return result;
    }

    /** 全部装备可能影响的属性集合（清空装备时也需遍历还原旧加成） */
    public static java.util.Set<Holder<Attribute>> allStatsAttrs() {
        java.util.Set<Holder<Attribute>> result = new java.util.LinkedHashSet<>();
        for (EquipDef def : DEFS.values()) {
            result.addAll(def.stats().keySet());
        }
        return result;
    }

    /** 靴子类装备（同一时间仅能拥有一件） */
    private static final java.util.Set<String> BOOTS = java.util.Set.of(
            "tayun", "zhuifeng", "jisuxue", "chuantouxue", "jifengxue",
            "shoupizu", "piaomiaoxue", "wuji");

    public static boolean isBoots(String equipId) {
        return BOOTS.contains(equipId);
    }
}
