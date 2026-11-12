package com.myriad;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 全局配置：玩法参数可在 config/myriad-common.toml 中调整。
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---- 汲取与运输 ----
    public static final ModConfigSpec.IntValue CHANNEL_SECONDS = BUILDER
            .comment("单次汲取所需时间（秒）")
            .defineInRange("channelSeconds", 3, 1, 60);

    public static final ModConfigSpec.IntValue SPIRIT_PER_EXTRACT = BUILDER
            .comment("单次汲取获得的灵源点数")
            .defineInRange("spiritPerExtract", 25, 1, 500);

    // ---- 成长 ----
    public static final ModConfigSpec.IntValue SECT_MAX_LEVEL = BUILDER
            .comment("双方宗门统一等级上限")
            .defineInRange("sectMaxLevel", 15, 1, 99);

    public enum EffectQuality { HIGH, LOW }
    public static final ModConfigSpec.EnumValue<EffectQuality> FEIYU_EFFECT_QUALITY = BUILDER
            .comment("Feiyu VFX profile: HIGH = full ornaments, LOW = reduced geometry and bursts; gameplay unchanged")
            .defineEnum("feiyuEffectQuality", EffectQuality.HIGH);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
