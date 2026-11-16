package com.myriad.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;

public final class PlayerNames {
    private PlayerNames(){ }
    public static void refresh(ServerPlayer player) {
        if(player.level().getServer()!=null)player.level().getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,player));
    }
}
