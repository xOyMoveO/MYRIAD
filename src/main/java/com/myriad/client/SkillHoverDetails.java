package com.myriad.client;

import com.myriad.character.rework.AttributeSpecializations;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityFormulaText;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.yike.RainbowShell;
import com.myriad.character.skills.yike.YikeStance.ShotForm;
import com.myriad.network.NetworkSetup.PlayerDataPayload;
import com.myriad.registry.ModAttributes;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** Short, localized learning previews. All live inputs belong to the panel owner's snapshot. */
public record SkillHoverDetails(String name, String group, String rank, boolean locked, String summary,
        String costs, List<String> metrics, String condition, String usual) {
    private static final String PREFIX = "screen.myriad.hover.";
    private static final String[] GROUPS = {"passive", "regular", "main", "ultimate"};
    private record Metric(Integer damage, Integer support, List<List<String>> values) {}
    private static final Map<String, List<Metric>> METRICS = readMetrics();

    private static Map<String, List<Metric>> readMetrics() {
        var stream = SkillHoverDetails.class.getResourceAsStream("/assets/myriad/skill_choice/metrics.json");
        try (var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, new TypeToken<Map<String, List<Metric>>>() {});
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String label(String key, Object... args) {
        return text(PREFIX + key, args);
    }

    private static String seconds(int ticks) { return AbilityFormulaText.amount(ticks / 20.0); }

    public static String passiveStatus(int rank, int level) {
        if (rank == 0) return label("passive_offer", FirstGroupSkills.passiveRank(level));
        if (rank >= 5) return label("max", rank);
        return label("passive_next", rank, rank * 3 + 1);
    }

    public static SkillHoverDetails describe(AbilitySkill skill, PlayerDataPayload data) {
        String id = skill.getId();
        String prefix = "skill.myriad." + id + ".hover.";
        var ranks = data.skillLevels();
        int paid = ranks.getOrDefault(id, 0);
        int opposite = FirstGroupSkills.opposite(skill.index());
        boolean locked = opposite >= 0 && ranks.getOrDefault(FirstGroupSkills.node(skill.role(), opposite).getId(), 0) > 0;
        boolean preview = !skill.baseAvailable() && paid == 0;
        boolean automatic = skill.isPassive() && !data.buffs().containsKey("g1_legacy_skill_budget");
        int rank = preview ? (automatic ? FirstGroupSkills.passiveRank(data.level()) : 1) : paid;
        String status;
        if (locked) status = label("locked");
        else if (automatic) status = passiveStatus(paid, data.level());
        else if (paid >= skill.maxRank()) status = label("max", paid);
        else if (skill.baseAvailable()) status = text("screen.myriad.rivet.anchor_rank", paid, skill.maxRank());
        else status = preview ? label("preview") : label("rank", paid, skill.maxRank());

        String group = text("screen.myriad.g1." + GROUPS[skill.index() / 2]);
        if (opposite >= 0) group += (skill.index() % 2 == 0 ? " A" : " B");
        String normal = text(prefix + "summary");
        String condition = Language.getInstance().has(prefix + "limit")
                ? SkillTooltipDescriptions.fieldText(skill, prefix + "limit", rank) : "";
        boolean assigned = !skill.isPassive()
                && FirstGroupSkills.resolve(CharacterType.byId(skill.role()), skill.slot(), ranks) == skill;
        if (preview && assigned && data.training() && CombatHudSkillRules.learned(skill, paid, true))
            status += " · " + label("training_ready");

        var player = Minecraft.getInstance().player;
        var yike = !preview && skill.role().equals("yike") && player != null ? YikeClientState.get(player.getId()) : null;
        int recast = assigned && !preview ? CombatHudState.recastTicks(skill, yike, ControlStatusOverlay.current()) : 0;
        var shotForm = yike == null ? ShotForm.NORMAL : switch (yike.mode()) {
            case 1 -> ShotForm.FIXED;
            case 2 -> ShotForm.MOBILE;
            default -> ShotForm.NORMAL;
        };
        boolean shuo = !preview && !skill.isPassive() && skill.role().equals("shuo");
        String variant = "";
        if (recast > 0) variant = "recast";
        else if (shuo) variant = ClientDataHolder.syncedCounter("g1_shuo_drawn") > 0 ? "drawn" : "sheathed";
        String summary = variant.isEmpty() ? normal : text(prefix + variant);
        String costs = "";
        List<String> metrics;
        if (skill instanceof RainbowShell shell && shotForm != ShotForm.NORMAL) {
            summary = label("mode" + yike.mode());
            costs = mana(skill, data, rank, preview) + " · " + label("next_shot", seconds(yike.nextShot()));
            metrics = List.of(label("shells", yike.shells()), SkillTooltipDescriptions.rainbowArtilleryLine(shotForm));
        } else {
            String metricId = shuo ? id + "." + variant : id;
            var rows = new ArrayList<String>();
            if (recast > 0) {
                costs = label("second") + " · " + label("recast_time", seconds(recast));
            } else if (!skill.isPassive()) {
                costs = mana(skill, data, rank, preview) + " · " + cooldown(skill, data, rank, preview, assigned);
            }
            if (recast == 0 || shuo) {
                if (Language.getInstance().has(prefix + "fields")) {
                    rows.add(SkillTooltipDescriptions.fieldText(skill, prefix + "fields", rank));
                }
                if (skill instanceof RainbowShell) {
                    rows.add(SkillTooltipDescriptions.rainbowArtilleryLine(ShotForm.FIXED));
                    rows.add(SkillTooltipDescriptions.rainbowArtilleryLine(ShotForm.MOBILE));
                }
                var fields = METRICS.getOrDefault(metricId, List.of());
                PlayerDataPayload live = skill.role().equals(data.characterId()) ? data : null;
                for (int index = 0; index < fields.size(); index++) {
                    var metric = fields.get(index);
                    String value;
                    if (metric.damage != null) {
                        value = SkillTooltipDescriptions.hoverDamage(skill, metric.damage, rank, live);
                    } else if (metric.support != null) {
                        continue; // All support amounts are rendered from the combat definition below.
                    } else {
                        var args = metric.values.stream().map(values -> values.get(Math.clamp(rank - 1, 0, values.size() - 1))).toArray();
                        value = text("skill.myriad." + metricId + ".hover.stat" + index, args);
                    }
                    if (!value.isEmpty()) rows.add(value);
                }
                var attrs = live == null ? Map.<String, Double>of() : live.attrs();
                double power = attrs.getOrDefault(ModAttributes.SPELL_POWER.value().getDescriptionId(),
                        FirstGroupGrowth.baselineSpellPower());
                var role = CharacterType.byId(skill.role());
                double attack = attrs.getOrDefault(ModAttributes.PHYSICAL_ATTACK.value().getDescriptionId(),
                        FirstGroupGrowth.baselinePhysicalAttack(role));
                double bonusPhysical = FirstGroupGrowth.bonusPhysicalAttack(role, attack);
                for (var amount : skill.supportAmounts()) {
                    if (skill.getId().equals("shuo_qiaofan") && variant.equals("drawn")) continue;
                    rows.add(SkillTooltipDescriptions.supportLine(amount, rank, power, bonusPhysical));
                }
                for (int i = 0; i < skill.damageFormulas().size(); i++) {
                    final int index = i;
                    if (fields.stream().anyMatch(metric -> java.util.Objects.equals(metric.damage, index))) continue;
                    if (shuo && !combatFormSegment(skill.getId(), i, variant)) continue;
                    String line = SkillTooltipDescriptions.hoverDamage(skill, i, rank, live);
                    if (!line.isEmpty()) rows.add(line);
                }
            }
            metrics = rows;
        }
        return new SkillHoverDetails(SkillTooltipDescriptions.abilityName(skill), group, status, locked, summary,
                costs, metrics, condition, summary.equals(normal) ? "" : normal);
    }

    private static boolean combatFormSegment(String id, int index, String variant) {
        boolean drawn = variant.equals("drawn");
        return switch (id) {
            case "shuo_nadao" -> drawn;
            case "shuo_qiaofan", "shuo_yishan", "shuo_duanliu", "shuo_wusheng", "shuo_canyue" -> drawn == (index == 0);
            default -> true;
        };
    }

    private static String mana(AbilitySkill skill, PlayerDataPayload data,int rank,boolean preview) {
        if (skill.manaCost(rank) == 0) return label("free");
        String cost = label("mana", AbilityFormulaText.amount(skill.manaCost(rank)));
        if (!preview && rank<skill.maxRank()) cost += " → " + AbilityFormulaText.amount(skill.manaCost(rank+1));
        if (data.training() && data.noMana()) return cost + " · " + label("train_mana");
        if (CombatHudSkillRules.freePreparation(skill, data.combatPreparing())) return cost + " · " + label("prep_mana");
        return cost;
    }

    private static String cooldown(AbilitySkill skill, PlayerDataPayload data, int rank, boolean preview, boolean assigned) {
        if (data.training() && data.noCooldown()) return label("train_cd");
        int ticks = skill instanceof RainbowShell shell ? shell.cooldownTicks(rank, false) : skill.getCooldownTicks(rank);
        if (skill.role().equals(data.characterId())) {
            double haste = AttributeSpecializations.skillHaste(data.buffs().getOrDefault("g1_attribute_cooldown", 0),
                    data.buffs().getOrDefault(AttributeSpecializations.COUNTER_PREFIX + "cooldown", 0));
            double reduction = ClientDataHolder.attrValue(ModAttributes.COOLDOWN_REDUCTION.value().getDescriptionId());
            ticks = AbilityRuntime.effectiveCooldownTicks(ticks, haste, reduction);
        }
        String value = label(preview ? "preview_cd" : "cd", seconds(ticks));
        int remaining = !preview && assigned ? ClientDataHolder.cooldownTicks(skill.slot()) : 0;
        return remaining > 0 ? value + " · " + label("left", seconds(remaining)) : value;
    }
}
