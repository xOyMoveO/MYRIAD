package com.myriad.registry;

import com.myriad.Myriad;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 自定义属性体系（策划案第十二节）。
 * <p>
 * 覆盖：法术强度、魔法抗性、暴击率、暴击伤害、物理吸血、法术吸血、韧性、双固定穿透、双百分比穿透、
 * 法力上限、法力回复。
 * <p>
 * 暴击率/暴击伤害/物理吸血/法术吸血/韧性/百分比穿透的逻辑约束在伤害层处理；
 * 暴击伤害初始为 130%，最终硬上限为 200%。
 * 上限在属性层钳制，暴击伤害还会在伤害层再次钳制，防止装备或其他临时 modifier 绕过。
 * <p>
 * 全部属性设为 {@code syncable}（原版默认 false）：AttributeMap#getSyncableAttributes 只返回
 * syncable 属性，EquipmentSystem 的重算依赖它来清除旧 modifier，否则重复装配会抛
 * "Modifier is already applied"。
 */
public final class ModAttributes {
    private ModAttributes() {}

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, Myriad.MODID);

    /** 创建可同步的自定义属性 */
    private static RangedAttribute ranged(String descriptionId, double defaultValue, double min, double max) {
        return (RangedAttribute) new RangedAttribute(descriptionId, defaultValue, min, max).setSyncable(true);
    }

    /** 物理攻击：独立于原版属性，平砍与物理技能的伤害基础 */
    public static final DeferredHolder<Attribute, RangedAttribute> PHYSICAL_ATTACK =
            ATTRIBUTES.register("physical_attack", () -> ranged("attribute.myriad.physical_attack", 10.0, 0.0, 10000.0));

    /** 法术强度：技能法术伤害的加成系数 */
    public static final DeferredHolder<Attribute, RangedAttribute> SPELL_POWER =
            ATTRIBUTES.register("spell_power", () -> ranged("attribute.myriad.spell_power", 0.0, 0.0, 10000.0));

    /** 魔法抗性：对等的法术减伤属性 */
    public static final DeferredHolder<Attribute, RangedAttribute> MAGIC_RESISTANCE =
            ATTRIBUTES.register("magic_resistance", () -> ranged("attribute.myriad.magic_resistance", 0.0, 0.0, 1000.0));

    /** 物理防御：独立于原版护甲（原版 ARMOR 上限 30，无法表达 32+），物理减伤属性 */
    public static final DeferredHolder<Attribute, RangedAttribute> PHYSICAL_DEFENSE =
            ATTRIBUTES.register("physical_defense", () -> ranged("attribute.myriad.physical_defense", 0.0, 0.0, 1000.0));

    /** 暴击率（百分比数值，0~100） */
    public static final DeferredHolder<Attribute, RangedAttribute> CRIT_CHANCE =
            ATTRIBUTES.register("crit_chance", () -> ranged("attribute.myriad.crit_chance", 0.0, 0.0, 100.0));

    /** 暴击伤害（百分比数值，默认 130，硬上限 200） */
    public static final DeferredHolder<Attribute, RangedAttribute> CRIT_DAMAGE =
            ATTRIBUTES.register("crit_damage", () -> ranged("attribute.myriad.crit_damage", 130.0, 100.0, 200.0));

    /** 物理吸血（百分比数值，逻辑上限40）：物理伤害（普攻与物理技能）按此比例吸血 */
    public static final DeferredHolder<Attribute, RangedAttribute> PHYSICAL_LIFESTEAL =
            ATTRIBUTES.register("physical_lifesteal", () -> ranged("attribute.myriad.physical_lifesteal", 0.0, 0.0, 100.0));

    /** 韧性（百分比数值，逻辑上限60）：减免控制时长 */
    public static final DeferredHolder<Attribute, RangedAttribute> TENACITY =
            ATTRIBUTES.register("tenacity", () -> ranged("attribute.myriad.tenacity", 0.0, 0.0, 100.0));

    /** 固定物理穿透 */
    public static final DeferredHolder<Attribute, RangedAttribute> FLAT_ARMOR_PEN =
            ATTRIBUTES.register("flat_armor_pen", () -> ranged("attribute.myriad.flat_armor_pen", 0.0, 0.0, 200.0));

    /** 固定法术穿透 */
    public static final DeferredHolder<Attribute, RangedAttribute> FLAT_MAGIC_PEN =
            ATTRIBUTES.register("flat_magic_pen", () -> ranged("attribute.myriad.flat_magic_pen", 0.0, 0.0, 200.0));

    /** 百分比物理穿透（百分比数值，逻辑上限60） */
    public static final DeferredHolder<Attribute, RangedAttribute> PERCENT_ARMOR_PEN =
            ATTRIBUTES.register("percent_armor_pen", () -> ranged("attribute.myriad.percent_armor_pen", 0.0, 0.0, 60.0));

    /** 百分比法术穿透（百分比数值，逻辑上限60） */
    public static final DeferredHolder<Attribute, RangedAttribute> PERCENT_MAGIC_PEN =
            ATTRIBUTES.register("percent_magic_pen", () -> ranged("attribute.myriad.percent_magic_pen", 0.0, 0.0, 60.0));

    /** 攻击速度：模组独立属性（不受手持物品影响）。 */
    public static final DeferredHolder<Attribute, RangedAttribute> ATTACK_SPEED =
            ATTRIBUTES.register("attack_speed", () -> ranged("attribute.myriad.attack_speed", 2.0, 0.0, 2.0));

    /** 法力上限 */
    public static final DeferredHolder<Attribute, RangedAttribute> MAX_MANA =
            ATTRIBUTES.register("max_mana", () -> ranged("attribute.myriad.max_mana", 50.0, 0.0, 10000.0));

    /** 法力回复（每秒恢复量） */
    public static final DeferredHolder<Attribute, RangedAttribute> MANA_REGEN =
            ATTRIBUTES.register("mana_regen", () -> ranged("attribute.myriad.mana_regen", 1.0, 0.0, 1000.0));

    /** 冷却缩减（百分比数值，逻辑上限 60）：按百分比缩短技能冷却 */
    public static final DeferredHolder<Attribute, RangedAttribute> COOLDOWN_REDUCTION =
            ATTRIBUTES.register("cooldown_reduction", () -> ranged("attribute.myriad.cooldown_reduction", 0.0, 0.0, 60.0));

    /** 生命回复内部速率（HP/s）；实际按整数5秒脉冲或不足1点时的换算间隔结算。 */
    public static final DeferredHolder<Attribute, RangedAttribute> HEALTH_REGEN =
            ATTRIBUTES.register("health_regen", () -> ranged("attribute.myriad.health_regen", 0.0, 0.0, 1000.0));

    /** 法术吸血（百分比数值，逻辑上限 40）：技能伤害按此比例吸血 */
    public static final DeferredHolder<Attribute, RangedAttribute> SPELL_LIFESTEAL =
            ATTRIBUTES.register("spell_lifesteal", () -> ranged("attribute.myriad.spell_lifesteal", 0.0, 0.0, 100.0));
}
