package com.myriad.client.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;

/** Native key identity, including mouse bindings, after vanilla updates mapping click queues. */
public final class KeyInput {
    private final int action, key;
    private final InputConstants.Key binding;
    public KeyInput(int action, KeyEvent event) {
        this.action=action; this.key=event.key(); this.binding=InputConstants.getKey(event);
    }
    public KeyInput(int action, int mouseButton) {
        this.action=action; this.key=-1; this.binding=InputConstants.Type.MOUSE.getOrCreate(mouseButton);
    }
    public int getAction() { return action; }
    public int getKey() { return key; }
    public boolean matches(KeyMapping mapping) { return KeyMappingHelper.getBoundKeyOf(mapping).equals(binding); }
}
