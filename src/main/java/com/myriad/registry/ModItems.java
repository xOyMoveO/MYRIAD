package com.myriad.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import com.myriad.Myriad;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 物品注册（武器、补给与玩法道具） */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Myriad.MODID);
    public static final DeferredItem<Item> MINION_MELEE_RED = ITEMS.registerSimpleItem("minion_melee_red");
    public static final DeferredItem<Item> MINION_MELEE_BLUE = ITEMS.registerSimpleItem("minion_melee_blue");
    public static final DeferredItem<Item> MINION_RANGED_RED = ITEMS.registerSimpleItem("minion_ranged_red");
    public static final DeferredItem<Item> MINION_RANGED_BLUE = ITEMS.registerSimpleItem("minion_ranged_blue");
    public static final DeferredItem<Item> MINION_MAGIC_RED = ITEMS.registerSimpleItem("minion_magic_red");
    public static final DeferredItem<Item> MINION_MAGIC_BLUE = ITEMS.registerSimpleItem("minion_magic_blue");

    public static Item minion(com.myriad.game.Faction faction, int kind) {
        boolean red = faction == com.myriad.game.Faction.XUANXIAO;
        return switch (kind) {
            case 2 -> (red ? MINION_MAGIC_RED : MINION_MAGIC_BLUE).get();
            case 1 -> (red ? MINION_RANGED_RED : MINION_RANGED_BLUE).get();
            default -> (red ? MINION_MELEE_RED : MINION_MELEE_BLUE).get();
        };
    }

    /** 灵源水晶：灵源能量的具象化物品（阶段2接入炼器/装备合成） */
    public static final DeferredItem<Item> SPIRIT_CRYSTAL = ITEMS.registerSimpleItem("spirit_crystal");

    /** 凌渊初始武器：使用 Durandal 材质模型，不带原版剑属性。 */
    public static final DeferredItem<Item> LINGYUAN_SWORD = ITEMS.registerSimpleItem("lingyuan_sword");

    /** 六名既有角色的专属初始武器；战斗属性仍由模组角色系统接管。 */
    public static final DeferredItem<Item> YINGCI = ITEMS.registerSimpleItem("yingci");
    public static final DeferredItem<Item> LEITINGJIAN = ITEMS.registerSimpleItem("leitingjian");
    public static final DeferredItem<com.myriad.item.ShuoSwordItem> SHUO_SWORD=ITEMS.registerItem("shuo_sword",com.myriad.item.ShuoSwordItem::new);
    public static final DeferredItem<Item> SHUO_SCABBARD=ITEMS.registerSimpleItem("shuo_scabbard");
    public static final DeferredItem<Item> LIEYANJIAN = ITEMS.registerSimpleItem("lieyanjian");
    public static final DeferredItem<Item> ZHENQI = ITEMS.registerSimpleItem("zhenqi");
    public static final DeferredItem<Item> SHUANGBING = ITEMS.registerSimpleItem("shuangbing");
    public static final DeferredItem<Item> FENGREN = ITEMS.registerSimpleItem("fengren");
    /** 丹心·青萝初始法器：青萝丹杖。 */
    public static final DeferredItem<Item> QINGLUO_STAFF = ITEMS.registerSimpleItem("qingluo_staff");
    /** 破军·赤霄初始武器：赤霄战刃。 */
    public static final DeferredItem<Item> CHIXIAO_BLADE = ITEMS.registerSimpleItem("chixiao_blade");
    /** 灵契·云笙初始法器：灵契玉铃。 */
    public static final DeferredItem<Item> YUNSHENG_BELL = ITEMS.registerSimpleItem("yunsheng_bell");
    /** 寒泽冰锥术的展示专用物品，仅由飞行弹体的 ItemDisplay 使用。 */
    public static final DeferredItem<Item> HANZE_ICE_SPIKE = ITEMS.registerSimpleItem("hanze_ice_spike");

    public static final DeferredItem<Item> TOWER_01_BLUE = ITEMS.registerSimpleItem("tower_01_qingque");
    public static final DeferredItem<Item> TOWER_02_BLUE = ITEMS.registerSimpleItem("tower_02_yunlei");
    public static final DeferredItem<Item> TOWER_03_BLUE = ITEMS.registerSimpleItem("tower_03_tianque");
    public static final DeferredItem<Item> TOWER_01_RED = ITEMS.registerSimpleItem("tower_01_qingque_red");
    public static final DeferredItem<Item> TOWER_02_RED = ITEMS.registerSimpleItem("tower_02_yunlei_red");
    public static final DeferredItem<Item> TOWER_03_RED = ITEMS.registerSimpleItem("tower_03_tianque_red");
    public static final DeferredItem<Item> CRYSTAL_BLUE = ITEMS.registerSimpleItem("crystal_lingyuan");
    public static final DeferredItem<Item> CRYSTAL_RED = ITEMS.registerSimpleItem("crystal_lingyuan_red");

    public static Item defenseTower(com.myriad.game.Faction faction, int tier) {
        boolean red = faction == com.myriad.game.Faction.XUANXIAO;
        return switch (tier) {
            case 1 -> (red ? TOWER_01_RED : TOWER_01_BLUE).get();
            case 2 -> (red ? TOWER_02_RED : TOWER_02_BLUE).get();
            case 3 -> (red ? TOWER_03_RED : TOWER_03_BLUE).get();
            default -> throw new IllegalArgumentException("Invalid tower tier: " + tier);
        };
    }

    /** 对局战术补给与视野饰品。 */
    public static final DeferredItem<Item> BLOOD_PILL = ITEMS.registerSimpleItem(
            "blood_pill", properties -> properties.stacksTo(16));
    public static final DeferredItem<Item> MANA_PILL = ITEMS.registerSimpleItem(
            "mana_pill", properties -> properties.stacksTo(16));
    public static final DeferredItem<Item> SPIRIT_SNARE = ITEMS.registerSimpleItem(
            "spirit_snare", properties -> properties.stacksTo(8));
    public static final DeferredItem<Item> SCOUT_TOTEM = ITEMS.registerSimpleItem(
            "scout_totem", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> TRUE_SIGHT_CHARM = ITEMS.registerSimpleItem(
            "true_sight_charm", properties -> properties.stacksTo(1));
    /** 手持时展开轻量战术示意图，不属于消耗品。 */
    public static final DeferredItem<Item> TACTICAL_MAP = ITEMS.registerSimpleItem(
            "tactical_map", properties -> properties.stacksTo(1));

    /** 角色 ID 对应的专属初始武器物品，客户端与服务端共同使用，避免锁定逻辑产生不同物品。 */
    public static Item initialWeaponItem(String characterId) {
        if (characterId == null) {
            return net.minecraft.world.item.Items.AIR;
        }
        return switch (characterId.toLowerCase(java.util.Locale.ROOT)) {
            case "yingxiao" -> YINGCI.get();
            case "xuanchen" -> ZHENQI.get();
            case "hanze" -> SHUANGBING.get();
            case "tianxing" -> LEITINGJIAN.get();
            case "shuo" -> SHUO_SWORD.get();
            case "fenjue" -> LIEYANJIAN.get();
            case "sulan" -> FENGREN.get();
            case "lingyuan" -> LINGYUAN_SWORD.get();
            case "qingluo" -> QINGLUO_STAFF.get();
            case "chixiao" -> CHIXIAO_BLADE.get();
            case "yunsheng" -> YUNSHENG_BELL.get();
            default -> net.minecraft.world.item.Items.AIR;
        };
    }

    public static boolean isInitialWeaponItem(ItemStack stack) {
        return stack.is(YINGCI.get())
                || stack.is(SHUO_SWORD.get())
                || stack.is(LEITINGJIAN.get())
                || stack.is(LIEYANJIAN.get())
                || stack.is(ZHENQI.get())
                || stack.is(SHUANGBING.get())
                || stack.is(FENGREN.get())
                || stack.is(QINGLUO_STAFF.get())
                || stack.is(CHIXIAO_BLADE.get())
                || stack.is(YUNSHENG_BELL.get())
                || stack.is(net.minecraft.world.item.Items.DIAMOND_SWORD)
                || stack.is(LINGYUAN_SWORD.get());
    }

    public static final DeferredItem<BlockItem> SPIRIT_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("spirit_core", ModBlocks.SPIRIT_CORE);

    public static final DeferredItem<BlockItem> DELIVERY_PORTAL_ITEM =
            ITEMS.registerSimpleBlockItem("delivery_portal", ModBlocks.DELIVERY_PORTAL);
}
