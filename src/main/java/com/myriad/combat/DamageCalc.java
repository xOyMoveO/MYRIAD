package com.myriad.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.myriad.registry.ModAttributes;

/**
 * 策划案第十二节伤害公式：
 * <pre>
 * 有效护甲 = 目标护甲 x (1 - 百分比物穿) - 固定物穿
 * 护甲减免系数 = 100 / (100 + max(有效护甲, 0))
 * 最终伤害 = 基础伤害 x [1 + (暴击判定 ? 暴击伤害倍率 - 1 : 0)] x 护甲减免系数
 * </pre>
 * 穿透顺序：百分比穿透 -> 固定穿透。法术伤害同理以魔抗/法穿计算。
 */
public final class DamageCalc {
    private DamageCalc() {}
    private static final ThreadLocal<Integer> SOURCE_SLOT = ThreadLocal.withInitial(() -> -1);

    /** 同步伤害调用的一次性技能上下文；hurt 被无敌帧提前拒绝时由调用方立即清理，不能污染下一击。 */
    private static final java.util.Map<java.util.UUID, SkillHitContext> SKILL_HITS =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 自定义普攻上下文：同时携带75%提前出手的伤害倍率。 */
    private static final java.util.Map<java.util.UUID, BasicAttackContext> BASIC_ATTACK_HITS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 标记该目标本次受伤来自模组技能（伤害事件中避免被平砍重写覆盖） */
    public static void markSkillHit(LivingEntity target, boolean crit, Kind kind) {
        SKILL_HITS.put(target.getUUID(), new SkillHitContext(crit, kind, SOURCE_SLOT.get()));
    }

    public static void markSkillHit(LivingEntity target, boolean crit, Kind kind, int slot) {
        SKILL_HITS.put(target.getUUID(), new SkillHitContext(crit, kind, slot));
    }

    /** Explicit origin survives delayed projectiles; slot 4 denotes passive/secondary damage. */
    public static Roll dealSkillDamage(LivingEntity attacker, LivingEntity target, float base, Kind kind, int slot) {
        int previous = SOURCE_SLOT.get();
        SOURCE_SLOT.set(slot);
        try { return dealDamage(attacker, target, base, kind); }
        finally { SOURCE_SLOT.set(previous); }
    }

    /**
     * 百分比生命伤害已经是最终设计数值，不能再乘全局技能倍率。
     * percent 使用 0~1 小数（例如 0.04F 表示 4% 最大生命）。
     */
    public static Roll dealPercentMaxHealthDamage(LivingEntity attacker, LivingEntity target,
            float percent, Kind kind, int slot) {
        int previous = SOURCE_SLOT.get();
        SOURCE_SLOT.set(slot);
        try {
            float ratio = Mth.clamp(percent, 0.0F, 1.0F);
            return dealDamage(attacker, target, target.getMaxHealth() * ratio, kind);
        } finally {
            SOURCE_SLOT.set(previous);
        }
    }

    /** 消费技能上下文；Incoming 事件同步调用，返回 null 表示非模组技能伤害。 */
    public static SkillHitContext consumeSkillHit(LivingEntity target) {
        return SKILL_HITS.remove(target.getUUID());
    }
    public static SkillHitContext peekSkillHit(LivingEntity target) { return SKILL_HITS.get(target.getUUID()); }

    public static boolean isSkillHit(LivingEntity target) {
        return SKILL_HITS.containsKey(target.getUUID()) && !BASIC_ATTACK_HITS.containsKey(target.getUUID());
    }

    /** hurt 在进入 Incoming 前被拒绝时清理尚未消费的技能上下文。 */
    public static void clearSkillHit(LivingEntity target) {
        SKILL_HITS.remove(target.getUUID());
    }

    public static void markBasicAttack(LivingEntity attacker, LivingEntity target) {
        markBasicAttack(attacker, target, 1.0F);
    }

    public static void markBasicAttack(LivingEntity attacker, LivingEntity target, float damageMultiplier) {
        BASIC_ATTACK_HITS.put(target.getUUID(), new BasicAttackContext(attacker.getUUID(),
                Math.clamp(damageMultiplier, 0.0F, 1.0F)));
    }

    /** 返回0表示不是该攻击者的模组普攻，否则返回本次普攻的最终节奏倍率。 */
    public static float consumeBasicAttackMultiplier(LivingEntity attacker, LivingEntity target) {
        BasicAttackContext context = BASIC_ATTACK_HITS.remove(target.getUUID());
        return context != null && attacker.getUUID().equals(context.attacker()) ? context.damageMultiplier() : 0.0F;
    }

    /** hurt 被无敌帧提前拒绝时，仅清理由该攻击者留下的普攻占位标记。 */
    public static void clearBasicAttack(LivingEntity attacker, LivingEntity target) {
        BASIC_ATTACK_HITS.computeIfPresent(target.getUUID(), (id, context) ->
                attacker.getUUID().equals(context.attacker()) ? null : context);
    }

    private record BasicAttackContext(java.util.UUID attacker, float damageMultiplier) { }

    /** 伤害类型 */
    public enum Kind {
        /** 物理伤害：受护甲减免 */
        PHYSICAL,
        /** 法术伤害：受魔法抗性减免 */
        MAGIC,
        /** 真实伤害：无视任何减免 */
        TRUE
    }

    /** 暴击率逻辑上限 */
    public static final double CRIT_CHANCE_CAP = 100.0;
    /** 暴击伤害硬上限。 */
    public static final double CRIT_DAMAGE_CAP = 200.0;
    /** 吸血逻辑上限 */
    public static final double LIFESTEAL_CAP = 20.0;
    /** 百分比穿透逻辑上限 */
    public static final double PERCENT_PEN_CAP = 60.0;

    /**
     * 计算最终伤害（不实际扣血）。
     *
     * @return 暴击判定结果，供调用方决定是否展示特效
     */
    private static final ThreadLocal<Boolean> NO_CRIT = ThreadLocal.withInitial(() -> false);
    public static Roll dealUncriticalSkillDamage(LivingEntity attacker, LivingEntity target, float base, Kind kind, int slot) {
        boolean previous = NO_CRIT.get();
        NO_CRIT.set(true);
        try { return dealSkillDamage(attacker, target, base, kind, slot); }
        finally { NO_CRIT.set(previous); }
    }
    public static Roll rollDamage(LivingEntity attacker, LivingEntity target, float base, Kind kind) {
        // 技能默认不暴击；明确规定可暴击的强化普攻仍走自身的普攻判定。
        float damage = (float) (base * mitigation(attacker, target, kind));
        return new Roll(damage, false);
    }

    /**
     * 对目标造成伤害，并结算吸血。
     * 技能与攻击逻辑统一通过本方法走自定义伤害公式。
     */
    public static Roll dealDamage(LivingEntity attacker, LivingEntity target, float base, Kind kind) {
        if (target instanceof com.myriad.game.DefenseTowerEntity) return new Roll(0, false);
        return dealTypedDamage(attacker, target, base, kind, false);
    }

    /** Typed mage auto-attacks are still auto-attacks and may hit buildings. */
    public static Roll dealBasicDamage(LivingEntity attacker, LivingEntity target, float base, Kind kind) {
        return dealTypedDamage(attacker, target, base, kind, true);
    }

    private static Roll dealTypedDamage(LivingEntity attacker, LivingEntity target, float base, Kind kind, boolean basic) {
        if (attacker instanceof net.minecraft.server.level.ServerPlayer player
                && com.myriad.combat.SkillTargets.ally(player, target)) {
            return new Roll(0.0F, false);
        }
        Roll roll = rollDamage(attacker, target, base, kind);
        DamageSource source = attacker instanceof Player player
                ? target.damageSources().playerAttack(player)
                : target.damageSources().mobAttack(attacker);
        // Continuous beams have their own five-tick cadence, not vanilla's hurt cooldown.
        // Other invulnerability checks and the normal damage event pipeline remain intact.
        if (NO_CRIT.get()) {
            source = new DamageSource(source.typeHolder(), attacker) {
                @Override public boolean is(net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> tag) {
                    return tag == net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN || super.is(tag);
                }
            };
        }
        markSkillHit(target, roll.crit(), kind);
        if (basic) markBasicAttack(attacker, target);
        target.hurt(source, roll.damage());
        clearSkillHit(target);
        if (basic) clearBasicAttack(attacker, target);
        return roll;
    }

    /** 纯数值伤害（无攻击者，如环境伤害），不触发暴击与吸血 */
    public static float dealDamageTo(LivingEntity target, DamageSource source, float base, Kind kind) {
        if (target instanceof com.myriad.game.DefenseTowerEntity) return 0;
        float damage = base * (float) mitigation(null, target, kind);
        target.hurt(source, damage);
        return damage;
    }

    /** 减免系数计算 */
    private static double mitigation(LivingEntity attacker, LivingEntity target, Kind kind) {
        if (kind == Kind.TRUE) {
            return 1.0;
        }
        double flatPen = 0;
        double percentPen = 0;
        if (attacker != null) {
            if (kind == Kind.PHYSICAL) {
                flatPen = attr(attacker, ModAttributes.FLAT_ARMOR_PEN);
                percentPen = Mth.clamp(attr(attacker, ModAttributes.PERCENT_ARMOR_PEN), 0, PERCENT_PEN_CAP);
            } else {
                flatPen = attr(attacker, ModAttributes.FLAT_MAGIC_PEN);
                percentPen = Mth.clamp(attr(attacker, ModAttributes.PERCENT_MAGIC_PEN), 0, PERCENT_PEN_CAP);
            }
        }
        double defense = kind == Kind.PHYSICAL
                ? attr(target, ModAttributes.PHYSICAL_DEFENSE)
                : attr(target, ModAttributes.MAGIC_RESISTANCE);
        double effectiveDefense = defense * (1.0 - percentPen / 100.0) - flatPen;
        return 100.0 / (100.0 + Math.max(effectiveDefense, 0.0));
    }

    /** 供自定义普攻复用与技能一致的“百分比穿透 → 固定穿透 → 防御减伤”流程。 */
    public static float applyMitigation(LivingEntity attacker, LivingEntity target, float base, Kind kind) {
        return base * (float) mitigation(attacker, target, kind);
    }

    /** 大型野怪受到暴击时额外减少 25% 本次暴击伤害。 */
    public static float applyMonsterCritReduction(LivingEntity target, float damage, boolean crit) {
        com.myriad.game.MonsterType type = com.myriad.game.MonsterManager.typeOf(target);
        return crit && type != null && type.isLargeBoss() ? damage * .75F : damage;
    }

    private static boolean isCrit(LivingEntity attacker) {
        if (attacker == null) {
            return false;
        }
        double chance = Mth.clamp(attr(attacker, ModAttributes.CRIT_CHANCE), 0, CRIT_CHANCE_CAP);
        if (attacker instanceof net.minecraft.server.level.ServerPlayer player)
            chance = Mth.clamp(chance
                   , 0, CRIT_CHANCE_CAP);
        return chance > 0 && attacker.getRandom().nextDouble() * 100.0 < chance;
    }

    public static double effectiveCritDamage(LivingEntity attacker) {
        if (attacker instanceof net.minecraft.server.level.ServerPlayer p
                && com.myriad.character.rework.FirstGroupSkills.active(com.myriad.player.PlayerDataProvider.get(p).getCharacterId())) return 150;
        return Mth.clamp(attr(attacker, ModAttributes.CRIT_DAMAGE), 100, CRIT_DAMAGE_CAP);
    }

    /** HUD 与目标同步使用的原始模组防御值。 */
    public static double defenseValue(LivingEntity target, Kind kind) {
        return kind == Kind.PHYSICAL
                ? attr(target, ModAttributes.PHYSICAL_DEFENSE)
                : attr(target, ModAttributes.MAGIC_RESISTANCE);
    }

    private static void healLifesteal(LivingEntity attacker, float damageDealt, Kind kind) {
        if (!(attacker instanceof LivingEntity living) || living.isDeadOrDying()) {
            return;
        }
        // 物理技能不吸血；普攻吸血和法术吸血仍由各自属性控制。
        if (kind == Kind.PHYSICAL) return;
        net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, ?>
                holder = kind == Kind.PHYSICAL ? ModAttributes.PHYSICAL_LIFESTEAL : ModAttributes.SPELL_LIFESTEAL;
        double lifesteal = Mth.clamp(attr(living, holder), 0, LIFESTEAL_CAP) / 100.0;
        if (lifesteal > 0 && living.getHealth() < living.getMaxHealth()) {
            float heal = damageDealt * (float) lifesteal;
            CombatTelemetry.heal(living, heal, "lifesteal");
            // 回复飘字（吸血来源为玩家时，仅实际回血）
            if (living instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        new com.myriad.network.NetworkSetup.HealFloatPayload(Math.max(1, Math.round(heal))));
            }
        }
    }

    /** 供自行完成暴击判定的技能复用统一技能吸血规则。 */
    public static void applySkillLifesteal(LivingEntity attacker, float damageDealt, Kind kind) {
        // Actual-damage event resolves all skill lifesteal, including custom projectiles.
    }

    /** 读取属性值；未挂载该属性的实体（野怪无模组属性）返回属性默认值，避免异常 */
    private static double attr(LivingEntity entity, net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, ?> holder) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance inst = entity.getAttribute(holder);
        return inst == null ? holder.value().getDefaultValue() : inst.getValue();
    }

    /** 一次伤害结算的结果 */
    public record Roll(float damage, boolean crit) {}

    /** 技能伤害的暴击与颜色类型必须原子消费，避免多段伤害发生字段错配。 */
    public record SkillHitContext(boolean crit, Kind kind, int slot) {}
}
