package com.myriad.registry;

import com.myriad.Myriad;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 自定义粒子类型。 */
public final class ModParticles {
    private ModParticles() {
    }

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, Myriad.MODID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ABILITY_MESH =
            PARTICLE_TYPES.register("ability_mesh", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MINGZHU_RAY =
            PARTICLE_TYPES.register("mingzhu_ray", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MINGZHU_GIANT_RAY =
            PARTICLE_TYPES.register("mingzhu_giant_ray", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ZHECHUAN_BLADE =
            PARTICLE_TYPES.register("zhechuan_blade", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ZHECHUAN_RETURN =
            PARTICLE_TYPES.register("zhechuan_return", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SULAN_BLADE_ARC =
            PARTICLE_TYPES.register("sulan_blade_arc", () -> new SimpleParticleType(false));

    /**
     * 通用元素灵光。通过 count=0 的粒子包将 x/y/z 偏移解释为 RGB（0~1），
     * 供 {@code ParticleEffects} 组合成爆发、光环和轨迹。
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPIRIT_MOTE =
            PARTICLE_TYPES.register("spirit_mote", () -> new SimpleParticleType(false));

    /** 由 Bedrock/Snowstorm 彩虹环效果转换而来的全亮彩虹灵屑。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RAINBOW_MOTE =
            PARTICLE_TYPES.register("rainbow_mote", () -> new SimpleParticleType(false));

    /** Wind1 Effekseer 风场的大型旋涡精灵。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND1_LARGE =
            PARTICLE_TYPES.register("wind1_large", () -> new SimpleParticleType(false));

    /** Wind1 Effekseer 风场的小型旋涡精灵。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND1_SMALL =
            PARTICLE_TYPES.register("wind1_small", () -> new SimpleParticleType(false));
}
