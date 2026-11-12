package com.myriad.item;
import net.minecraft.world.item.Item;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
public final class ShuoSwordItem extends Item {
    public ShuoSwordItem(Properties p){super(p);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND||!com.myriad.game.ModState.isEnabled())return InteractionResult.PASS;
        if(player instanceof net.minecraft.server.level.ServerPlayer p)com.myriad.character.skills.shuo.ShuoCombat.requestSheathe(p);
        return InteractionResult.SUCCESS;
    }
}
