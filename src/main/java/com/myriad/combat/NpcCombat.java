package com.myriad.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Difficulty-independent damage with ordinary shields, defenses and death hooks. */
public final class NpcCombat {
    private NpcCombat() {}
    private static final ResourceKey<DamageType> ATTACK = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath("myriad", "npc_attack"));
    private static final ResourceKey<DamageType> MAGIC = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath("myriad", "npc_magic"));

    public static boolean isMagic(DamageSource source) { return source.is(MAGIC); }

    public static boolean canTarget(LivingEntity target) {
        return target.isAlive() && !(target instanceof Player p && (p.isSpectator() || p.isCreative()));
    }

    public static void hit(LivingEntity target, LivingEntity attacker, float base, DamageCalc.Kind kind) {
        if (!(target.level() instanceof ServerLevel level) || !canTarget(target)) return;
        float damage = DamageCalc.applyMitigation(attacker, target, base, kind);
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(kind == DamageCalc.Kind.MAGIC ? MAGIC : ATTACK), attacker);
        target.hurt(source, damage);
    }

    /** 防御塔基础伤害由防御塔自身数值决定，最大生命百分比附伤只计算一次。 */
    public static void towerHit(LivingEntity target, LivingEntity attacker, float base,
            float maxHealthPercent, DamageCalc.Kind kind) {
        if (!(target.level() instanceof ServerLevel level) || !canTarget(target)) return;
        float raw = base + target.getMaxHealth() * Math.clamp(maxHealthPercent, 0.0F, 1.0F);
        float damage = DamageCalc.applyMitigation(attacker, target, raw, kind);
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(kind == DamageCalc.Kind.MAGIC ? MAGIC : ATTACK), attacker);
        target.hurt(source, damage);
    }

    /** Damage without crediting a player, used by enemy fountains. */
    public static void environmentHit(LivingEntity target, float base, DamageCalc.Kind kind) {
        if (!(target.level() instanceof ServerLevel level) || !canTarget(target)) return;
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(kind == DamageCalc.Kind.MAGIC ? MAGIC : ATTACK));
        DamageCalc.dealDamageTo(target, source, base, kind);
    }
}
