package com.myriad.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import com.myriad.Myriad;

/** 物品注册（武器、补给与玩法道具） */
public final class ModItems {
    private ModItems() {}

    /** Forces synchronous registration of this class's static entries during mod initialization. */
    public static void initialize() { }
    private static <T extends Item> java.util.function.Supplier<T> registerItem(String id, java.util.function.Function<Item.Properties, T> factory) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath(Myriad.MODID, id));
        T item = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
        return () -> item;
    }
    private static java.util.function.Supplier<Item> registerSimpleItem(String id) { return registerItem(id, Item::new); }
    private static java.util.function.Supplier<Item> registerSimpleItem(String id, java.util.function.UnaryOperator<Item.Properties> properties) { return registerItem(id, p -> new Item(properties.apply(p))); }
    private static java.util.function.Supplier<BlockItem> registerSimpleBlockItem(String id, java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> block) { return registerItem(id, p -> new BlockItem(block.get(), p)); }
    public static final java.util.function.Supplier<Item> MINION_MELEE_RED = registerSimpleItem("minion_melee_red");
    public static final java.util.function.Supplier<Item> MINION_MELEE_BLUE = registerSimpleItem("minion_melee_blue");
    public static final java.util.function.Supplier<Item> MINION_RANGED_RED = registerSimpleItem("minion_ranged_red");
    public static final java.util.function.Supplier<Item> MINION_RANGED_BLUE = registerSimpleItem("minion_ranged_blue");
    public static final java.util.function.Supplier<Item> MINION_MAGIC_RED = registerSimpleItem("minion_magic_red");
    public static final java.util.function.Supplier<Item> MINION_MAGIC_BLUE = registerSimpleItem("minion_magic_blue");

    public static Item minion(com.myriad.game.Faction faction, int kind) {
        boolean red = faction == com.myriad.game.Faction.DESTRUCTION;
        return switch (kind) {
            case 2 -> (red ? MINION_MAGIC_RED : MINION_MAGIC_BLUE).get();
            case 1 -> (red ? MINION_RANGED_RED : MINION_RANGED_BLUE).get();
            default -> (red ? MINION_MELEE_RED : MINION_MELEE_BLUE).get();
        };
    }

    /** 灵源水晶：灵源能量的具象化物品（阶段2接入炼器/装备合成） */
    public static final java.util.function.Supplier<Item> SPIRIT_CRYSTAL = registerSimpleItem("spirit_crystal");

    /** 凌渊初始武器：使用 Durandal 材质模型，不带原版剑属性。 */
    public static final java.util.function.Supplier<Item> LINGYUAN_SWORD = registerSimpleItem("lingyuan_sword");

    /** 六名既有角色的专属初始武器；战斗属性仍由模组角色系统接管。 */
    public static final java.util.function.Supplier<Item> RIVET_HAMMER=registerSimpleItem("rivet_hammer");
    public static final java.util.function.Supplier<Item> HENGKE=registerSimpleItem("hengke");
    public static final java.util.function.Supplier<Item> YINGCI = registerSimpleItem("yingci");
    public static final java.util.function.Supplier<Item> LEITINGJIAN = registerSimpleItem("leitingjian");
    public static final java.util.function.Supplier<com.myriad.item.ShuoSwordItem> SHUO_SWORD=registerItem("shuo_sword",com.myriad.item.ShuoSwordItem::new);
    public static final java.util.function.Supplier<Item> HONG_LIE_FRAME=registerSimpleItem("hong_lie_frame");
    public static final java.util.function.Supplier<Item> HONG_LIE_PETAL=registerSimpleItem("hong_lie_petal");
    public static final java.util.function.Supplier<Item> HONG_LIE_RING=registerSimpleItem("hong_lie_ring");
    public static final java.util.function.Supplier<Item> HONG_LIE=registerSimpleItem("hong_lie");
    public static final java.util.function.Supplier<Item> SHUO_SCABBARD=registerSimpleItem("shuo_scabbard");
    public static final java.util.function.Supplier<Item> LIEYANJIAN = registerSimpleItem("lieyanjian");
    public static final java.util.function.Supplier<Item> ZHENQI = registerSimpleItem("zhenqi");
    public static final java.util.function.Supplier<Item> SHUANGBING = registerSimpleItem("shuangbing");
    public static final java.util.function.Supplier<Item> FENGREN = registerSimpleItem("fengren");
    /** 丹心·青萝初始法器：青萝丹杖。 */
    public static final java.util.function.Supplier<Item> QINGLUO_STAFF = registerSimpleItem("qingluo_staff");
    /** 破军·赤霄初始武器：赤霄战刃。 */
    public static final java.util.function.Supplier<Item> CHIXIAO_BLADE = registerSimpleItem("chixiao_blade");
    /** 灵契·云笙初始法器：灵契玉铃。 */
    public static final java.util.function.Supplier<Item> YUNSHENG_BELL = registerSimpleItem("yunsheng_bell");
    /** 寒泽冰锥术的展示专用物品，仅由飞行弹体的 ItemDisplay 使用。 */
    public static final java.util.function.Supplier<Item> HANZE_ICE_SPIKE = registerSimpleItem("hanze_ice_spike");

    public static final java.util.function.Supplier<Item> TOWER_01_BLUE = registerSimpleItem("tower_01_qingque");
    public static final java.util.function.Supplier<Item> TOWER_02_BLUE = registerSimpleItem("tower_02_yunlei");
    public static final java.util.function.Supplier<Item> TOWER_03_BLUE = registerSimpleItem("tower_03_tianque");
    public static final java.util.function.Supplier<Item> TOWER_01_RED = registerSimpleItem("tower_01_qingque_red");
    public static final java.util.function.Supplier<Item> TOWER_02_RED = registerSimpleItem("tower_02_yunlei_red");
    public static final java.util.function.Supplier<Item> TOWER_03_RED = registerSimpleItem("tower_03_tianque_red");
    public static final java.util.function.Supplier<Item> CRYSTAL_BLUE = registerSimpleItem("crystal_lingyuan");
    public static final java.util.function.Supplier<Item> CRYSTAL_RED = registerSimpleItem("crystal_lingyuan_red");

    public static Item defenseTower(com.myriad.game.Faction faction, int tier) {
        boolean red = faction == com.myriad.game.Faction.DESTRUCTION;
        return switch (tier) {
            case 1 -> (red ? TOWER_01_RED : TOWER_01_BLUE).get();
            case 2 -> (red ? TOWER_02_RED : TOWER_02_BLUE).get();
            case 3 -> (red ? TOWER_03_RED : TOWER_03_BLUE).get();
            default -> throw new IllegalArgumentException("Invalid tower tier: " + tier);
        };
    }

    /** 对局战术补给与视野饰品。 */
    public static final java.util.function.Supplier<Item> BLOOD_PILL = registerSimpleItem(
            "blood_pill", properties -> properties.stacksTo(16));
    public static final java.util.function.Supplier<Item> MANA_PILL = registerSimpleItem(
            "mana_pill", properties -> properties.stacksTo(16));
    public static final java.util.function.Supplier<Item> SPIRIT_SNARE = registerSimpleItem(
            "spirit_snare", properties -> properties.stacksTo(8));
    public static final java.util.function.Supplier<Item> SCOUT_TOTEM = registerSimpleItem(
            "scout_totem", properties -> properties.stacksTo(1));
    public static final java.util.function.Supplier<Item> TRUE_SIGHT_CHARM = registerSimpleItem(
            "true_sight_charm", properties -> properties.stacksTo(1));
    /** 手持时展开轻量战术示意图，不属于消耗品。 */
    public static final java.util.function.Supplier<Item> TACTICAL_MAP = registerSimpleItem(
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
            case "rivet" -> RIVET_HAMMER.get();
            case "keheng" -> HENGKE.get();
            case "yike" -> HONG_LIE.get();
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
        return stack.is(RIVET_HAMMER.get()) || stack.is(HENGKE.get()) || stack.is(HONG_LIE.get()) || stack.is(YINGCI.get())
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

    public static final java.util.function.Supplier<BlockItem> SPIRIT_CORE_ITEM =
            registerSimpleBlockItem("spirit_core", ModBlocks.SPIRIT_CORE);

    public static final java.util.function.Supplier<BlockItem> DELIVERY_PORTAL_ITEM =
            registerSimpleBlockItem("delivery_portal", ModBlocks.DELIVERY_PORTAL);
}
