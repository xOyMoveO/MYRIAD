package com.myriad.client;

import com.myriad.mixin.ClientInputAccessor;
import com.myriad.network.SkillChargePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/** Frame-interpolated bar; movement is suppressed after vanilla refreshes keyboard input. */
public final class SkillChargeClient {
    private static String skill="";private static int total;private static long started;private static float walkFactor;
    private SkillChargeClient(){}
    public static void reset(){skill="";total=0;started=0;walkFactor=0;}
    public static void receive(SkillChargePayload p){
        if(p.ticks()<=0){reset();return;}
        if(p.ticks()>1200||p.skill().isBlank()||!Float.isFinite(p.walkFactor())||p.walkFactor()<0||p.walkFactor()>1)return;
        skill=p.skill();total=p.ticks();started=p.started();walkFactor=p.walkFactor();
    }
    public static boolean active(){
        var mc=Minecraft.getInstance();return total>0&&ClientDataHolder.modEnabled()&&mc.level!=null&&mc.player!=null
                &&mc.player.isAlive()&&!mc.player.isSpectator()&&mc.level.getGameTime()<started+total;
    }
    public static void input(MovementInputUpdateEvent e){
        if(!active()||e.getEntity()!=Minecraft.getInstance().player)return;
        Minecraft.getInstance().player.setSprinting(false);
        // The server movement attribute supplies the slow. Do not suppress a mobile channel's input twice.
        if(walkFactor>0){var k=e.getInput().keyPresses;
            e.getInput().keyPresses=new Input(k.forward(),k.backward(),k.left(),k.right(),false,k.shift(),false);return;}
        e.getInput().keyPresses=Input.EMPTY;
        ((ClientInputAccessor)e.getInput()).myriad$setMoveVector(Vec2.ZERO);
        Minecraft.getInstance().player.setSprinting(false);
    }
    public static void render(GuiGraphicsExtractor gui,Font font,float partial){
        if(!active())return;
        var mc=Minecraft.getInstance();double remaining=Math.max(0,started+total-mc.level.getGameTime()-partial);
        float progress=(float)Math.clamp(1-remaining/total,0,1);
        int x=mc.getWindow().getGuiScaledWidth()/2,y=mc.getWindow().getGuiScaledHeight()/2+42;
        String name=Component.translatable("skill.myriad."+skill).getString();
        String text=Component.translatable("hud.myriad.skill_charge",name,
                String.format(java.util.Locale.ROOT,"%.2f",remaining/20)).getString();
        gui.fill(x-86,y-1,x+86,y+28,0xE3181026);
        gui.fill(x-86,y-1,x+86,y,0xFFAA7BD6);
        gui.text(font,text,x-font.width(text)/2,y+3,0xFFF0E6FF);
        gui.fill(x-81,y+17,x+81,y+23,0xFF362547);
        int fill=Math.round(progress*162);
        if(fill>0){gui.fill(x-81,y+17,x-81+fill,y+23,0xFFA776DC);gui.fill(x-81,y+17,x-81+fill,y+19,0xFFEAD5FF);}
    }
}
