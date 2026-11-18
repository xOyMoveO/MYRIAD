package com.myriad.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.myriad.client.gui.SkillChoiceLayout.Box;

/** Translucent glass styling, not framebuffer blur/refraction. */
final class TrainingGlass {
    static final int TEXT=0xFFF1FAFF,MUTED=0xFFA5BACB,ACCENT=0xFF91E4F5;
    static void round(GuiGraphicsExtractor g,Box b,int radius,int color){
        int r=Math.max(0,Math.min(radius,Math.min(b.w(),b.h())/2));
        for(int y=0;y<b.h();y++){
            double dy=y<r?r-y-.5:y>=b.h()-r?y-(b.h()-r)+.5:0;
            int inset=dy==0?0:(int)Math.ceil(r-Math.sqrt(Math.max(0,r*r-dy*dy)));
            g.fill(b.x()+inset,b.y()+y,b.right()-inset,b.y()+y+1,color);
        }
    }
    static void surface(GuiGraphicsExtractor g,Box b,int radius,boolean hover,boolean panel){
        round(g,new Box(b.x(),b.y()+2,b.w(),b.h()),radius,panel?0x50000614:0x26000614);
        round(g,b,radius,hover?0xB8C4EEFF:0x6589B5CC);
        round(g,new Box(b.x()+1,b.y()+1,b.w()-2,b.h()-2),Math.max(0,radius-1),panel?0xD0132235:hover?0xB834566B:0x98223B50);
        g.fill(b.x()+radius,b.y()+1,b.right()-radius,b.y()+2,hover?0xC5F2FFFF:0x88D8EFFF);
        g.fill(b.x()+radius,b.y()+2,b.right()-radius,b.y()+Math.min(5,b.h()-1),0x163ECFEF);
    }
}
