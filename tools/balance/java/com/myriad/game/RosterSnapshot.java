package com.myriad.game;

import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.gap;
import static com.myriad.game.SnapshotJson.json;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityDamageFormula;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.luoxia.LastSun;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.character.skills.rivet.TacticalAnchor;
import com.myriad.character.skills.yike.RainbowShell;
import com.myriad.character.skills.yike.YikeBasicAttack;
import com.myriad.character.skills.yike.YikeStance;
import com.myriad.combat.MagicBullet;
import com.myriad.combat.VanillaMeleeAttack;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class RosterSnapshot {
    private static final String NO_CLOCK = "not_timed";
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";

    private RosterSnapshot() {}

    static JsonArray roles() {
        JsonArray result = new JsonArray();
        Arrays.stream(CharacterType.playableValues()).filter(role -> FirstGroupSkills.active(role.getId()))
                .sorted(Comparator.comparing(CharacterType::getId)).forEach(role -> result.add(role(role)));
        return result;
    }

    private static JsonObject role(CharacterType role) {
        String id = role.getId();
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        var attributes = new JsonObject();
        result.add("attributes", attributes);
        attributes.add("health", growth("hp", "health", IntStream.rangeClosed(1, 20)
                .mapToObj(level -> FirstGroupGrowth.health(id, level)).toList()));
        attributes.add("mana", growth("mana", "maxMana", IntStream.rangeClosed(1, 20)
                .mapToObj(level -> FirstGroupGrowth.maxMana(role, level)).toList()));
        attributes.add("physicalAttack", innate(FirstGroupGrowth.baselinePhysicalAttack(role), "physical_attack", "baselinePhysicalAttack"));
        attributes.add("spellPower", innate(FirstGroupGrowth.baselineSpellPower(), "spell_power", "baselineSpellPower"));
        attributes.add("attackSpeed", innate(FirstGroupGrowth.BASE_ATTACK_SPEED, "attacks_per_second", "BASE_ATTACK_SPEED"));
        attributes.add("movementSpeed", innate(FirstGroupGrowth.BASE_MOVEMENT_SPEED, "movement_attribute", "BASE_MOVEMENT_SPEED"));
        attributes.add("physicalDefense", innate(FirstGroupGrowth.BASE_DEFENSE, "defense_points", "BASE_DEFENSE"));
        attributes.add("magicResistance", innate(FirstGroupGrowth.BASE_DEFENSE, "defense_points", "BASE_DEFENSE"));
        attributes.add("healthRegen", innate(FirstGroupGrowth.BASE_HEALTH_REGEN, "hp_per_second", "BASE_HEALTH_REGEN"));
        attributes.add("manaRegen", innate(FirstGroupGrowth.BASE_MANA_REGEN, "mana_per_second", "BASE_MANA_REGEN"));
        attributes.add("critDamage", innate(FirstGroupGrowth.BASE_CRIT_DAMAGE, "percent", "BASE_CRIT_DAMAGE"));
        result.add("basicAttack", basicAttack(role));
        List<AbilitySkill> skills = FirstGroupSkills.candidates(id);
        result.add("legalBranches", branches(skills));
        var candidates = new JsonArray();
        skills.forEach(skill -> candidates.add(skill(skill)));
        result.add("skills", candidates);
        return result;
    }

    private static JsonObject growth(String unit, String method, List<Double> values) {
        var series = field(values, unit, source(FirstGroupGrowth.class, method),
                "无购买、装备或临时增益", "设置英雄等级后", NO_CLOCK);
        series.addProperty("levelStart", 1);
        series.addProperty("levelEnd", values.size());
        series.addProperty("levelOne", values.getFirst());
        series.addProperty("firstLevelIncrease", values.get(1) - values.getFirst());
        return series;
    }

    private static JsonObject innate(double value, String unit, String member) {
        var series = field(value, unit, source(FirstGroupGrowth.class, member),
                "1–20 级同值，无购买或临时增益", "初始化及按等级重算后", NO_CLOCK);
        series.addProperty("levelStart", 1);
        series.addProperty("levelEnd", 20);
        series.addProperty("perLevelIncrease", 0);
        return series;
    }

    private static JsonObject basicAttack(CharacterType role) {
        var result = new JsonObject();
        double physical = FirstGroupGrowth.baselinePhysicalAttack(role);
        double attackSpeed = FirstGroupGrowth.BASE_ATTACK_SPEED;
        if (VanillaMeleeAttack.usesVanilla(role)) {
            result.addProperty("id", "vanilla_melee");
            result.add("rawDamage", field(physical, "hp", source(FirstGroupGrowth.class, "baselinePhysicalAttack"),
                    "无购买、无增益且满恢复的近战普攻物理基础值，技能与暴击修饰另算", "命中时", NO_CLOCK));
            result.add("recovery", field(20.0 / attackSpeed, "ticks", source(VanillaMeleeAttack.class, "fullyRecovered"),
                    "原版攻速已同步且无临时增益；原版按已恢复的强度判定能否出手", "完全恢复时", ACTION));
            result.add("range", gap("missing_input", "blocks", source(VanillaMeleeAttack.class, "canSubmitIntent"),
                    "当前原版实体交互距离、攻击物品和合法攻击对象", "普攻判定时", NO_CLOCK, "需要实际交互属性与目标碰撞体"));
        } else if (role == CharacterType.YIKE) {
            result.addProperty("id", "cannon");
            result.add("rawDamage", field(physical, "hp", source(YikeBasicAttack.class, "tryFire"),
                    "普通形态，无购买或临时增益，物理", "出膛", NO_CLOCK));
            result.add("range", field(YikeBasicAttack.RANGE, "blocks", source(YikeBasicAttack.class, "RANGE"),
                    "普通形态且持虹裂，炮台姿态禁止普攻；墙体能截断弹道", "发射", NO_CLOCK));
            result.add("recovery", field(YikeBasicAttack.recoveryTicks(attackSpeed), "ticks", source(YikeBasicAttack.class, "recoveryTicks"),
                    "无购买或临时增益", "出膛后", ACTION));
        } else if (role == CharacterType.LUOXIA) {
            result.addProperty("id", "bow");
            result.add("rawDamage", field(physical, "hp", source(LuoxiaBow.class, "input"),
                    "普通弓、无购买或临时增益，物理，快弓及满弓按被动修饰", "放弓", NO_CLOCK));
            result.add("drawWork", field(LuoxiaBow.NORMAL_WORK, "ticks", source(LuoxiaBow.class, "NORMAL_WORK"),
                    "无快弓、满弓与攻速增益", "开始拉弓后", ACTION));
            result.add("drawRange", field(LuoxiaBow.NORMAL_RANGE, "blocks", source(LuoxiaBow.class, "NORMAL_RANGE"),
                    "普通／快弓，命中实体或墙体可提前截断", "放弓", NO_CLOCK));
        } else {
            result.addProperty("id", "magic_bullet");
            result.add("rawDamage", field(MagicBullet.rawDamage(role, FirstGroupGrowth.baselineSpellPower()), "hp",
                    source(MagicBullet.class, "rawDamage"), "无购买或增益的魔法普攻", "发射", NO_CLOCK));
            result.add("spellScaling", field(MagicBullet.spellPowerRatio(role), "hp_per_spell_power",
                    source(MagicBullet.class, "spellPowerRatio"), "每点相对固有法强的增量", "发射", NO_CLOCK));
            result.add("range", field(MagicBullet.MAX_RANGE, "blocks", source(MagicBullet.class, "MAX_RANGE"),
                    "持初始武器，障碍物可提前截断", "发射", NO_CLOCK));
            result.add("recovery", field(MagicBullet.recoveryTicks(attackSpeed), "ticks", source(MagicBullet.class, "recoveryTicks"),
                    "无购买或临时增益", "发射后", ACTION));
        }
        return result;
    }

    private static JsonObject branches(List<AbilitySkill> skills) {
        var pairs = new JsonObject();
        for (AbilitySkill skill : skills) {
            int other = FirstGroupSkills.opposite(skill.index());
            if (other > skill.index()) {
                pairs.add(category(skill), json(List.of(skill.getId(), skills.get(other).getId())));
            }
        }
        var common = skills.stream().filter(skill -> FirstGroupSkills.opposite(skill.index()) < 0)
                .map(AbilitySkill::getId).toList();
        var combinations = new JsonArray();
        for (var passive : pairs.getAsJsonArray("passive")) {
            for (var major : pairs.getAsJsonArray("e")) {
                for (var ultimate : pairs.getAsJsonArray("r")) {
                    combinations.add(json(List.of(passive.getAsString(), common.get(0), common.get(1),
                            major.getAsString(), ultimate.getAsString())));
                }
            }
        }
        var data = new JsonObject();
        data.add("common", json(common));
        data.add("exclusive", pairs);
        data.add("terminalCombinations", combinations);
        data.addProperty("source", source(FirstGroupSkills.class, "opposite"));
        return data;
    }

    private static String category(AbilitySkill skill) {
        return switch (skill.slot()) {
            case -1 -> "passive";
            case 0 -> "q";
            case 1 -> "w";
            case 2 -> "e";
            case 3 -> "r";
            default -> throw new IllegalStateException("Unexpected skill slot " + skill.getId());
        };
    }

    private static JsonObject skill(AbilitySkill skill) {
        var result = new JsonObject();
        String resource = "src/main/resources/" + FirstGroupSkills.definitionResource(skill.getId()) + "#skills/" + skill.getId();
        result.addProperty("id", skill.getId());
        result.addProperty("index", skill.index());
        result.addProperty("category", category(skill));
        result.addProperty("implementation", source(skill.getClass(), "release"));
        result.add("rank", field(skill.maxRank(), "rank", resource + "/maxRank", "实际注册该候选", "学习时", NO_CLOCK));
        result.add("availability", field(skill.baseAvailable() ? "unlearned_base" : "learned", "availability",
                skill.baseAvailable() ? source(skill.getClass(), "baseAvailable") : source(AbilityRuntime.class, "learned"),
                "正式模式；被动采用自动阶数", "技能入口验证", NO_CLOCK));
        var ranks = new JsonArray();
        for (int rank = skill.baseAvailable() ? 0 : 1; rank <= skill.maxRank(); rank++) {
            ranks.add(rank(skill, resource, rank));
        }
        result.add("ranks", ranks);
        result.add("effects", effects(skill));
        return result;
    }

    private static JsonObject rank(AbilitySkill skill, String resource, int rank) {
        var result = new JsonObject();
        result.addProperty("rank", rank);
        var earliestLevel = IntStream.rangeClosed(1, 20).filter(level ->
                skill.isPassive() ? FirstGroupSkills.passiveRank(level) >= rank
                        : rank == 0 || FirstGroupSkills.learnError(skill.role(), Map.of(skill.getId(), rank - 1),
                                20, level, skill.getId()) == null).findFirst();
        if (earliestLevel.isPresent()) {
            result.add("earliestHeroLevel", field(earliestLevel.getAsInt(), "level",
                    source(FirstGroupSkills.class, skill.isPassive() ? "passiveRank" : "learnError"),
                    "合法所选分支且点数充足，基础可用技能允许零付费阶", "学习或自动升级", NO_CLOCK));
        }
        if (skill.isPassive()) {
            result.add("mana", gap("not_applicable", "mana", resource + "/mana", "被动", "on_cast_commit",
                    NO_CLOCK, "被动候选不作为主动技能支付"));
            result.add("cooldown", gap("not_applicable", "ticks", resource + "/cooldowns", "被动", "on_cast_commit",
                    REAL, "主动施法冷却不适用；被动触发可有独立恢复"));
        } else {
            result.add("mana", field(skill.manaCost(rank), "mana", resource + "/mana", "正常施法流程，按阶换算",
                    "on_cast_commit", NO_CLOCK));
            result.add("cooldown", field(skill.getCooldownTicks(rank), "ticks",
                    skill instanceof TacticalAnchor ? source(TacticalAnchor.class, "getCooldownTicks") : resource + "/cooldowns",
                    "常规形态的基础冷却；未应用购点缩减或训练免冷却", "on_cast_commit", REAL));
        }
        return result;
    }

    static JsonObject commonSkillRules() {
        var rules = new JsonObject();
        rules.add("skillHaste", field(Map.of("perPurchase", FirstGroupGrowth.HASTE_PER_POINT,
                        "denominatorBase", AbilityRuntime.SKILL_HASTE_REFERENCE,
                        "minPositiveTicks", AbilityRuntime.MIN_POSITIVE_COOLDOWN, "rounding", "floor"), "rule_set",
                source(AbilityRuntime.class, "effectiveCooldownTicks"),
                "购买急速只作用于受益正冷却。其他来源百分比冷却缩减仍独立乘算。炮台 Q 当发为零，射击间隔以真实刻独立推进",
                "on_cast_commit_or_yike_stance_exit", REAL));
        rules.add("legacyCooldownReductionCap", field(ModAttributes.MAX_COOLDOWN_REDUCTION, "percent",
                source(ModAttributes.class, "MAX_COOLDOWN_REDUCTION"), "装备、灵石灌顶等独立百分比来源", "on_cast_commit", REAL));
        rules.add("bonusPhysicalAttack", field("effective_minus_innate", "operator",
                source(FirstGroupGrowth.class, "bonusPhysicalAttack"), "当前英雄与等级；计入购点及有效临时修饰",
                "on_cast_context", NO_CLOCK));
        rules.add("azureHornPhysicalAttack", field(PlayerDataProvider.AZURE_HORN_ATTACK_BONUS, "added_total_ratio",
                source(PlayerDataProvider.class, "applyBossBuffs"), "已有凛锋且尚未到期", "on_player_tick", REAL));
        rules.add("shieldResolution", field("same_source_max_refresh", "rule", source(AbilityRuntime.class, "shield"),
                "合法同阵营单位，实际新增盾触发支援", "on_shield_grant", REAL));
        rules.add("healResolution", field("clamp_to_missing_health", "rule", source(AbilityRuntime.class, "heal"),
                "存活、同世界、合法友方且名义治疗量大于零", "on_heal", NO_CLOCK));
        return rules;
    }

    static JsonObject attributePurchases() {
        var rules = new JsonObject();
        rules.add("pricing", field(Map.of("startingGold", FirstGroupGrowth.STARTING_PRICE,
                        "stepNumerator", FirstGroupGrowth.PRICE_STEP_DIVISOR + 1, "stepDenominator", FirstGroupGrowth.PRICE_STEP_DIVISOR,
                        "rounding", "nearest_gold_each_step", "freeTrainingCounts", true), "rule_set",
                source(FirstGroupGrowth.class, "price"), "按本项已购点数计档", "on_purchase", NO_CLOCK));
        rules.add("permanentGain", field(Map.of("physicalAttackPerStrength", FirstGroupGrowth.purchasedPhysicalAttack(1),
                        "spellPowerPerWisdom", FirstGroupGrowth.purchasedSpellPower(1),
                        "defensePerPoint", FirstGroupGrowth.DEFENSE_GAIN, "attackSpeedPerPoint", FirstGroupGrowth.SPEED_ATTACK_GAIN,
                        "movementSpeedPerPoint", FirstGroupGrowth.SPEED_MOVEMENT_GAIN,
                        "attackSpeedMax", ModAttributes.MAX_ATTACK_SPEED,
                        "movementSpeedMax", Attributes.MOVEMENT_SPEED.value().sanitizeValue(Double.MAX_VALUE)), "rule_set",
                source(FirstGroupGrowth.class, "purchaseError"),
                "按固有值和已有购点判断。至少一项永久分量有增益才按当前档全价成交，训练免金仍记点",
                "before_purchase", NO_CLOCK));
        return rules;
    }

    private static JsonArray effects(AbilitySkill skill) {
        var result = new JsonArray();
        var damage = skill.damageFormulas();
        var ids = new HashSet<String>();
        for (AbilityDamageFormula formula : damage) {
            String id = formula.labelKey().isEmpty() ? "damage" : formula.labelKey().substring("skill.myriad.damage.".length());
            if (!ids.add(id)) throw new IllegalStateException("Duplicate damage effect: " + skill.getId() + "." + id);
            var values = new LinkedHashMap<String, Object>();
            values.put("kind", formula.kind().name().toLowerCase(Locale.ROOT));
            values.put("base", formula.base());
            values.put("rankGrowth", formula.rankGrowth());
            values.put("scale", formula.scale());
            values.put("scaleGrowth", formula.scaleGrowth());
            values.put("scaling", formula.scaling().name().toLowerCase(Locale.ROOT));
            values.put("weight", formula.weight());
            values.put("maxHitsPerTarget", formula.maxHits());
            var item = effect(skill, id, "damage", field(values, "hp_before_mitigation",
                    source(skill.getClass(), "damageFormulas"), SkillEffects.damageCondition(skill, id),
                    "技能结算的原始段值（外部乘区先查条件与覆盖）", NO_CLOCK));
            result.add(item);
        }
        for (var support : skill.supportAmounts()) {
            String id = support.effect().id();
            result.add(effect(skill, id, id, field(Map.of(
                    "base", support.base(), "rankGrowth", support.rankGrowth(), "scale", support.scale(),
                    "scaling", support.scaling().name().toLowerCase(Locale.ROOT)),
                    "hp_raw", source(skill.getClass(), "supportAmounts"), SkillEffects.supportCondition(skill),
                    "支援应用前，读取施法上下文记录的指定属性", NO_CLOCK)));
        }
        if (!skill.isPassive() && skill.commitWindupTicks() > 0) {
            result.add(effect(skill, "commit_windup", "windup", field(skill.commitWindupTicks(), "ticks",
                    source(skill.getClass(), "commitWindupTicks"), "通过施放检查后的提交前起手", "on_cast_request", ACTION)));
        }
        if (skill instanceof LastSun) {
            result.add(effect(skill, "arrow_range", "range", field(LastSun.RANGE, "blocks", source(LastSun.class, "RANGE"),
                    "重箭出手；首个合法敌人或地形可截断", "on_projectile_launch", NO_CLOCK)));
            result.add(effect(skill, "arrow_speed", "projectile_speed", field(LastSun.speedBlocksPerActionTick(),
                    "blocks_per_tick", source(LastSun.class, "speedBlocksPerActionTick"),
                    "乘全局英雄投射物速度系数；局部缓时影响推进", "on_projectile_step", ACTION)));
            result.add(effect(skill, "arrow_entity_padding", "projectile_padding", field(LastSun.entityPaddingBlocks(),
                    "blocks", source(LastSun.class, "entityPaddingBlocks"),
                    "碰撞候选箱向外扩张；射程仍核对实体本体箱", "on_projectile_step", NO_CLOCK)));
        }
        if (skill instanceof RainbowShell rainbowShell) yikeForms(result, rainbowShell);
        SkillEffects.add(result, skill);
        return result;
    }

    static JsonObject effect(AbilitySkill skill, String id, String type, JsonObject value) {
        var result = new JsonObject();
        result.addProperty("id", skill.getId() + "." + id);
        result.addProperty("type", type);
        result.addProperty("minRank", skill.baseAvailable() ? 0 : 1);
        result.addProperty("maxRank", skill.maxRank());
        result.add("rule", value);
        return result;
    }

    private static void yikeForms(JsonArray result, RainbowShell skill) {
        for (var form : YikeStance.ShotForm.values()) {
            boolean artillery = form != YikeStance.ShotForm.NORMAL;
            String id = form.name().toLowerCase(Locale.ROOT);
            String condition = switch (form) {
                case NORMAL -> "普通形态且 Q 已学习";
                case FIXED -> "定轨炮台；Q 已学习、有弹有蓝且射击时点已到";
                case MOBILE -> "游走阵列；Q 已学习、有弹有蓝且射击时点已到";
            };
            String formSource = source(YikeStance.class, "ShotForm." + form.name());
            var fields = new JsonObject();
            var cooldowns = new JsonArray();
            for (int rank = 1; rank <= skill.maxRank(); rank++) {
                var value = field(skill.cooldownTicks(rank, artillery), "ticks", artillery
                        ? source(RainbowShell.class, "cooldownTicks") : "src/main/resources/"
                        + FirstGroupSkills.definitionResource(skill.getId()) + "#skills/" + skill.getId() + "/cooldowns",
                        condition, "on_projectile_launch", REAL);
                value.addProperty("rank", rank);
                cooldowns.add(value);
            }
            fields.add("skillCooldown", cooldowns);
            fields.add("range", field(form.range, "blocks", formSource, condition, "on_projectile_launch", NO_CLOCK));
            fields.add("speed", field(form.speed, "blocks_per_second", formSource, condition, "on_projectile_launch", ACTION));
            fields.add("radius", field(form.radius, "blocks", formSource, condition, "on_projectile_launch", NO_CLOCK));
            fields.add("damageMultiplier", field(Map.of("base", form.baseMultiplier, "growthPerStanceRank", form.multiplierPerRank),
                    "ratio", formSource, condition, "on_projectile_launch", NO_CLOCK));
            if (artillery) {
                fields.add("shots", field(form.shells, "shell", formSource, condition, "on_stance_deploy", NO_CLOCK));
                fields.add("duration", field(YikeStance.DURATION_TICKS, "ticks", source(YikeStance.class, "DURATION_TICKS"),
                        condition, "on_stance_deploy", REAL));
                fields.add("nextShot", field(form.nextShotTicks, "ticks", formSource,
                        condition + "；从上一发出膛计，原 Q 冷却挡首发，W 暂停可以推迟下一发", "on_projectile_launch", REAL));
                fields.add("shotRequestBuffer", field(YikeStance.SHOT_REQUEST_BUFFER_TICKS, "ticks",
                        source(YikeStance.class, "SHOT_REQUEST_BUFFER_TICKS"),
                        condition + "；只允许上膛后提前起手，出膛仍要等真实截止刻", "on_cast_request", REAL));
                fields.add("pauseForW", field(YikeStance.PAUSE_FOR_W_TICKS, "ticks", source(YikeStance.class, "PAUSE_FOR_W_TICKS"),
                        condition + "且 W 已完成合法预警提交", "on_w_commit", REAL));
                fields.add("exitCooldown", field("max_of_existing_and_haste_adjusted_normal", "rule",
                        source(YikeStance.class, "finish"), "本次炮台至少出膛一次且未启用训练免冷却", "on_stance_exit", REAL));
            } else {
                fields.add("shots", gap("not_applicable", "shell", formSource, condition, "on_projectile_launch",
                        NO_CLOCK, "离开炮台姿态没有弹仓，仅看共用蓝耗与冷却"));
            }
            result.add(effect(skill, "form_" + id, "form", field(fields, "rule_set", formSource, condition,
                    "on_projectile_launch", NO_CLOCK)));
        }
    }
}
