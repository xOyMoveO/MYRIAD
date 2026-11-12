package com.myriad.client.gui;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import com.myriad.client.SkillIcon;
import com.myriad.registry.EquipmentRegistry.EquipDef;

/** 属性面板与装备商店共用的 16×16 属性图标及筛选定义。 */
public final class AttributeIcons {
    private AttributeIcons() {}

    public record Filter(String id, String labelKey) {
        public boolean matches(EquipDef def) {
            return def.stats().keySet().stream().anyMatch(attr -> matchesDescription(id,
                    attr.value().getDescriptionId()));
        }
    }

    public static final List<Filter> FILTERS = List.of(
            new Filter("max_health", "screen.myriad.attr.max_health"),
            new Filter("physical_defense", "attribute.myriad.physical_defense"),
            new Filter("physical_attack", "attribute.myriad.physical_attack"),
            new Filter("attack_speed", "attribute.myriad.attack_speed"),
            new Filter("movement_speed", "screen.myriad.attr.movement_speed"),
            new Filter("spell_power", "attribute.myriad.spell_power"),
            new Filter("magic_resistance", "attribute.myriad.magic_resistance"),
            new Filter("crit_chance", "attribute.myriad.crit_chance"),
            new Filter("crit_damage", "attribute.myriad.crit_damage"),
            new Filter("physical_lifesteal", "attribute.myriad.physical_lifesteal"),
            new Filter("spell_lifesteal", "attribute.myriad.spell_lifesteal"),
            new Filter("tenacity", "attribute.myriad.tenacity"),
            new Filter("armor_pen", "screen.myriad.attr.armor_pen"),
            new Filter("magic_pen", "screen.myriad.attr.magic_pen"),
            new Filter("max_mana", "attribute.myriad.max_mana"),
            new Filter("mana_regen", "attribute.myriad.mana_regen"),
            new Filter("cooldown_reduction", "attribute.myriad.cooldown_reduction"),
            new Filter("health_regen", "attribute.myriad.health_regen")
    );

    public static boolean matchesDescription(String iconId, String descriptionId) {
        if ("armor_pen".equals(iconId)) return descriptionId.contains("armor_pen");
        if ("magic_pen".equals(iconId)) return descriptionId.contains("magic_pen");
        return descriptionId.endsWith("." + iconId) || descriptionId.endsWith("_" + iconId);
    }

    public static void draw(GuiGraphicsExtractor gui, int x, int y, String iconId) {
        if (SkillIcon.hasResource("textures/gui/sprites/attributes/" + iconId + ".png")) {
            gui.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    Identifier.fromNamespaceAndPath("myriad", "attributes/" + iconId),
                    x, y, 16, 16);
        }
    }
}
