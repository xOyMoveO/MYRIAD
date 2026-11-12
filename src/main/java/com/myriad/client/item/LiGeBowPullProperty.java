package com.myriad.client.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.myriad.registry.ModAttributes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** 供影弦弓物品模型读取的、已经乘上模组攻速的拉弓进度。 */
public record LiGeBowPullProperty(boolean unused) implements RangeSelectItemModelProperty {
    public static final MapCodec<LiGeBowPullProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.BOOL.optionalFieldOf("unused", false).forGetter(LiGeBowPullProperty::unused))
            .apply(instance, LiGeBowPullProperty::new));

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        LivingEntity entity = owner == null ? null : owner.asLivingEntity();
        if (entity == null || entity.getUseItem() != stack) return 0.0F;
        double attackSpeed = Math.max(0.1D, entity.getAttributeValue(ModAttributes.ATTACK_SPEED));
        return Mth.clamp((float)(UseDuration.useDuration(stack, entity) * attackSpeed / 20.0D), 0.0F, 1.0F);
    }

    @Override
    public MapCodec<LiGeBowPullProperty> type() {
        return MAP_CODEC;
    }
}
