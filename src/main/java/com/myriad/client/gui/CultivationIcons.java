package com.myriad.client.gui;

import com.myriad.client.SkillIcon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import com.myriad.client.gui.SkillChoiceLayout.Box;

/** Optional resource-pack sprites, with existing artwork and drawn navigation as defaults. */
public final class CultivationIcons {
    private CultivationIcons(){}
    /** Add a PNG under textures/gui/sprites/ followed by this path. No Java change is needed. */
    public static String spritePath(String category,String id){return "cultivation/"+category+"/"+id;}
    private static boolean optional(GuiGraphicsExtractor g,String category,String id,Box b){
        String path=spritePath(category,id);
        if(!SkillIcon.hasResource("textures/gui/sprites/"+path+".png"))return false;
        g.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("myriad",path),b.x(),b.y(),b.w(),b.h());return true;
    }
    public static void navigation(GuiGraphicsExtractor g,String id,Box b,int color){
        if(optional(g,"navigation",id,b))return;
        int x=b.x(),y=b.y(),w=b.w(),h=b.h(),cx=x+w/2,cy=y+h/2;
        if(id.equals("skills")){
            g.fill(cx-1,y+2,cx+1,y+h-2,color);g.fill(x+2,cy-1,x+w-2,cy+1,color);
            for(int[] p:new int[][]{{cx-3,y},{x,cy-3},{x+w-6,cy-3},{cx-3,y+h-6}})g.fill(p[0],p[1],p[0]+6,p[1]+6,color);
        }else if(id.equals("attributes")){
            for(int i=0;i<5;i++){int bx=x+1+i*Math.max(2,(w-2)/5),top=y+h-4-(i%3)*4;g.fill(bx,top,bx+2,y+h-1,color);}
            g.fill(x,y+h-1,x+w,y+h,color);
        }else{
            g.fill(x+3,y+4,x+w-3,y+6,color);g.fill(x+3,y+5,x+5,y+h-1,color);g.fill(x+w-5,y+5,x+w-3,y+h-1,color);
            g.fill(x+3,y+h-2,x+w-3,y+h,color);g.fill(cx-3,y,cx+3,y+2,color);
            g.fill(cx-1,cy-3,cx+1,cy+5,color);g.fill(cx-4,cy,cx+4,cy+2,color);
        }
    }
    public static void attribute(GuiGraphicsExtractor g,String id,Box b){
        if(optional(g,"attributes",id,b))return;
        String fallback=switch(id){case "strength"->"physical_attack";case "wisdom"->"spell_power";
            case "speed"->"movement_speed";case "defense"->"physical_defense";default->"cooldown_reduction";};
        String path="attributes/"+fallback;
        if(SkillIcon.hasResource("textures/gui/sprites/"+path+".png"))
            g.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("myriad",path),b.x(),b.y(),b.w(),b.h());
        else navigation(g,"attributes",b,SkillChoiceTheme.JADE);
    }
    public static void supply(GuiGraphicsExtractor g,String id,Box b){
        if(optional(g,"supplies",id,b))return;
        g.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("myriad","supply_"+id),b.x(),b.y(),b.w(),b.h());
    }
}
