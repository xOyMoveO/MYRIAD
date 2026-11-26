package com.myriad.client;

import com.myriad.mixin.ClientInputAccessor;
import com.myriad.network.SkillChargePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.client.player.ClientInput;

/** Frame-interpolated bar; movement is suppressed after vanilla refreshes keyboard input. */
public final class SkillChargeClient {
    private static SkillChargePayload sample;
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static String role;

    private SkillChargeClient() {}
    public static void reset() { sample = null; level = null; role = null; }
    public static void receive(SkillChargePayload packet) {
        if (packet.ticks() <= 0) { reset(); return; }
        if (packet.ticks() > 1200 || packet.skill().isBlank() || !Float.isFinite(packet.walkFactor())
                || packet.walkFactor() < 0 || packet.walkFactor() > 1 || !Float.isFinite(packet.completed())
                || packet.completed() < 0 || packet.completed() > packet.ticks() || !Float.isFinite(packet.rate())
                || packet.rate() <= 0 || packet.rate() > 1) return;
        level = Minecraft.getInstance().level;
        role = ClientDataHolder.characterId();
        sample = packet;
    }
    private static double completed(float partial) {
        return sample.completed() + Math.max(0, level.getGameTime() - sample.started() + partial) * sample.rate();
    }
    public static boolean active() {
        var mc = Minecraft.getInstance();
        if (sample == null) return false;
        if (!ClientDataHolder.modEnabled() || level == null || level != mc.level || mc.player == null
                || !mc.player.isAlive() || mc.player.isSpectator() || !role.equals(ClientDataHolder.characterId())) {
            reset();
            return false;
        }
        return completed(0) < sample.ticks();
    }
    public static void input(ClientInput input){
        if(!active())return;
        Minecraft.getInstance().player.setSprinting(false);
        // The server movement attribute supplies the slow. Do not suppress a mobile channel's input twice.
        if(sample.walkFactor()>0){var k=input.keyPresses;
            input.keyPresses=new Input(k.forward(),k.backward(),k.left(),k.right(),false,k.shift(),false);return;}
        input.keyPresses=Input.EMPTY;
        ((ClientInputAccessor)input).myriad$setMoveVector(Vec2.ZERO);
        Minecraft.getInstance().player.setSprinting(false);
    }
    public static void render(GuiGraphicsExtractor gui,Font font,float partial, CombatHudLayout.Bounds combatPanel){
        if(!active())return;
        double workLeft = Math.max(0, sample.ticks() - completed(partial));
        double remaining = workLeft / sample.rate();
        float progress=(float)Math.clamp(1-workLeft/sample.ticks(),0,1);
        String name=Component.translatable("skill.myriad."+sample.skill()).getString();
        String text=Component.translatable("hud.myriad.skill_charge",name,
                String.format(java.util.Locale.ROOT,"%.1f",remaining/20)).getString();
        CastProgressBar.draw(gui,font,text,progress,combatPanel);
    }
}
