package com.myriad.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

/** Jade/antique-gold panel art with crisp, code-drawn interactive states and live text. */
final class SkillChoiceTheme {
    private static final Identifier BACKDROP=Identifier.fromNamespaceAndPath("myriad","textures/gui/skill_choice/meridian_hall.png");
    static final int GOLD=0xFFE8CE90,JADE=0xFF91D9CA,INK=0xF00A1C22,MUTED=0xFF869C9F;
    private SkillChoiceTheme(){}
    static void backdrop(GuiGraphicsExtractor g, SkillChoiceLayout.Box box) {
        g.fill(box.x() - 2, box.y() - 2, box.right() + 2, box.bottom() + 2, 0x90000000);
        // Preserve the complete original artwork, including all corners and the sword crest.
        g.blit(BACKDROP, box.x(), box.y(), box.right(), box.bottom(), 0, 1, 0, 1);
    }
    static void divider(GuiGraphicsExtractor g, int left, int right, int y, int color) {
        int middle = (left + right) / 2;
        g.fill(left, y, middle - 4, y + 1, color);
        g.fill(middle + 5, y, right, y + 1, color);
        diamond(g, middle, y, color);
    }
    static void diamond(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y - 3, x + 1, y - 1, color);
        g.fill(x - 2, y - 1, x, y + 1, color);
        g.fill(x + 1, y - 1, x + 3, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 3, color);
    }
    static void plate(GuiGraphicsExtractor g,SkillChoiceLayout.Box b,int border,int fill){
        g.fill(b.x(),b.y(),b.right(),b.bottom(),border);
        g.fill(b.x()+1,b.y()+1,b.right()-1,b.bottom()-1,fill);
        g.fill(b.x()+3,b.y()+3,b.right()-3,b.y()+4,0x2949A99D);
    }
    static void card(GuiGraphicsExtractor g,SkillChoiceLayout.Box b,boolean learned,boolean locked,boolean hover){
        int border=locked?0xFF34454B:learned?GOLD:hover?JADE:0xFF3C716F;
        plate(g,b,border,locked?0xEF101B23:learned?0xF0173439:INK);
        // Stepped metal corner inlays remain sharp at every GUI scale.
        for(int dx:new int[]{0,b.w()-8})for(int dy:new int[]{0,b.h()-8}){
            int x=b.x()+dx,y=b.y()+dy;g.fill(x,y,x+8,y+1,border);g.fill(x,y,x+1,y+8,border);
        }
        if(learned)g.fill(b.x()+2,b.y()+7,b.x()+4,b.bottom()-7,GOLD);
    }
    static void button(GuiGraphicsExtractor g,Font font,SkillChoiceLayout.Box b,String text,boolean enabled,boolean hover,boolean selected){
        int edge=selected?GOLD:enabled?(hover?GOLD:0xFF50877E):0xFF34444B;
        int fill=selected?0xF02C4D46:enabled?(hover?0xF02A5350:0xF016373B):0xF0122028;
        plate(g,b,edge,fill);
        if(selected)g.fill(b.x()+8,b.bottom()-3,b.right()-8,b.bottom()-2,GOLD);
        String label=font.plainSubstrByWidth(text,Math.max(1,b.w()-8));
        g.text(font,label,b.x()+(b.w()-font.width(label))/2,b.y()+(b.h()-font.lineHeight)/2,enabled?GOLD:MUTED);
    }
    static void scrollbar(GuiGraphicsExtractor g,SkillChoiceLayout.Box track,SkillChoiceLayout.Box thumb){
        g.fill(track.x(),track.y(),track.right(),track.bottom(),0xAA071418);
        g.fill(thumb.x(),thumb.y(),thumb.right(),thumb.bottom(),0xBBAEBA8C);
    }
}
