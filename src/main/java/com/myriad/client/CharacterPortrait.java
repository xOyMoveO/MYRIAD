package com.myriad.client;

import com.myriad.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Static, atlas-backed hero portraits. Player skin markers on the minimap stay unchanged. */
public final class CharacterPortrait {
    private CharacterPortrait(){}
    public static boolean draw(GuiGraphicsExtractor gui,CharacterType type,int x,int y,int size,float alpha){
        if(type==null||size<=0)return false;
        String path="portraits/"+type.getId();
        if(Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath("myriad","textures/gui/sprites/"+path+".png")).isEmpty())return false;
        int color=(Math.round(Math.clamp(alpha,0,1)*255)<<24)|0xFFFFFF;
        gui.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("myriad",path),x,y,size,size,color);
        return true;
    }
}
