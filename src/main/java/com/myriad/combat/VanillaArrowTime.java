package com.myriad.combat;

import com.myriad.Myriad;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.*;

/** The current flight rate travels with vanilla entity tracking, including newly tracking observers. */
public final class VanillaArrowTime {
    private VanillaArrowTime() {}
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS=
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,Myriad.MODID);
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<Double>> RATE=
            ATTACHMENTS.register("arrow_action_rate",()->AttachmentType.builder(()->1.0).sync(ByteBufCodecs.DOUBLE).build());
    // Explicit MYRIAD airborne budgets. Vanilla only has an unrelated 1200-tick in-ground life.
    public static final int FLIGHT_TICKS=1200, REAL_FLIGHT_TICKS=2400;
}
