package com.myriad.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Click-edge aim input and left-click fire use dedicated packets, never vanilla hold-to-use repeats. */
public final class ShenLiGunItem extends Item {
    public ShenLiGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.FAIL;
    }
}
