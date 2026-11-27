package com.myriad.client.firstperson;

import com.myriad.client.ClientDataHolder;
import com.myriad.client.RespawnSpectatorView;
import net.minecraft.client.Minecraft;

/** The no-arms build hides skin geometry only in the local player's own MYRIAD view. */
public final class FirstPersonArmVisibility {
    private FirstPersonArmVisibility() {}

    public static boolean hidePlayerArms() {
        Minecraft client = Minecraft.getInstance();
        return ClientDataHolder.modEnabled()
                && client.player != null
                && client.getCameraEntity() == client.player
                && client.options.getCameraType().isFirstPerson()
                && !client.player.isSpectator()
                && !RespawnSpectatorView.isActive();
    }
}
