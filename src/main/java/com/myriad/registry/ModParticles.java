package com.myriad.registry;

import com.myriad.Myriad;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** 自定义粒子类型。 */
public final class ModParticles {
    private ModParticles() {
    }

    /** Forces synchronous registration of this class's static entries during mod initialization. */
    public static void initialize() { }
    private static java.util.function.Supplier<SimpleParticleType> register(String name, java.util.function.Supplier<SimpleParticleType> factory) {
        var particle = net.minecraft.core.Registry.register(BuiltInRegistries.PARTICLE_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath(Myriad.MODID, name), factory.get());
        return () -> particle;
    }
    public static final java.util.function.Supplier<SimpleParticleType> ABILITY_MESH =
            register("ability_mesh", () -> net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple());

    public static final java.util.function.Supplier<SimpleParticleType> SULAN_BLADE_ARC =
            register("sulan_blade_arc", () -> net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple());

    /**
     * 通用元素灵光。通过 count=0 的粒子包将 x/y/z 偏移解释为 RGB（0~1），
     * 供 {@code ParticleEffects} 组合成爆发、光环和轨迹。
     */
    public static final java.util.function.Supplier<SimpleParticleType> SPIRIT_MOTE =
            register("spirit_mote", () -> net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple());

    /** 由 Bedrock/Snowstorm 彩虹环效果转换而来的全亮彩虹灵屑。 */
    public static final java.util.function.Supplier<SimpleParticleType> RAINBOW_MOTE =
            register("rainbow_mote", () -> net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple());

    /** Wind1 Effekseer 风场的大型旋涡精灵。 */
    public static final java.util.function.Supplier<SimpleParticleType> WIND1_LARGE =
            register("wind1_large", () -> net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple());

    /** Wind1 Effekseer 风场的小型旋涡精灵。 */
    public static final java.util.function.Supplier<SimpleParticleType> WIND1_SMALL =
            register("wind1_small", () -> net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple());
}
