package com.myriad.client.gui;

import com.myriad.character.CharacterType;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.client.*;
import com.myriad.network.NetworkSetup;
import com.myriad.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.myriad.network.Packets;
import java.util.*;
import static com.myriad.client.gui.SkillChoiceTheme.*;

/** /myriad choose: portrait cards and scrollable skill preview, without historical role factions. */
public final class CharacterSelectScreen extends Screen {
    private String selectedId;
    private int scrollRow,detailScroll,detailMaximum;
    private Map<String,Boolean> taken=Map.of();
    private String current="";
    private CharacterChoiceLayout layout;
    private Button confirm;
    public CharacterSelectScreen(){
        super(Component.translatable("screen.myriad.choose.title"));
        Packets.sendToServer(new NetworkSetup.RequestSyncPayload());
    }
    @Override public boolean isPauseScreen(){return false;}
    private boolean available(String id){return CharacterType.byId(id)!=null&&CharacterChoiceLayout.available(id,ClientDataHolder.characterId(),ClientDataHolder.takenCharacters());}
    private boolean canConfirm(){
        return CharacterChoiceLayout.canConfirm(ClientDataHolder.modEnabled(), available(selectedId));
    }
    @Override protected void init(){
        var roles=CharacterType.playableValues();
        current=ClientDataHolder.characterId();taken=Map.copyOf(ClientDataHolder.takenCharacters());
        boolean first=selectedId==null;
        if(CharacterType.byId(selectedId)==null)selectedId=CharacterType.byId(current)!=null?current:
                Arrays.stream(roles).filter(r->available(r.getId())).findFirst().orElse(roles[0]).getId();
        layout=CharacterChoiceLayout.create(width,height,roles.length,scrollRow);
        if(first){
            int index=0;for(int i=0;i<roles.length;i++)if(roles[i].getId().equals(selectedId))index=i;
            layout=CharacterChoiceLayout.create(width,height,roles.length,index/layout.columns());
        }
        scrollRow=layout.scrollRow();
        for(int i=0;i<roles.length;i++)if(layout.visible(i))addRenderableWidget(new RoleCard(roles[i],layout.card(i)));
        addRenderableWidget(new PanelButton(layout.previous(),Component.literal("‹"),b->scrollBy(-1))).active=scrollRow>0;
        addRenderableWidget(new PanelButton(layout.next(),Component.literal("›"),b->scrollBy(1))).active=scrollRow<layout.maxScroll();
        addRenderableWidget(new PanelButton(layout.guide(),Component.translatable("hero.guide.open"),b->{
            var role=CharacterType.byId(selectedId);if(role!=null)Minecraft.getInstance().setScreenAndShow(new CharacterGuideScreen(this,role));
        }));
        confirm=addRenderableWidget(new PanelButton(layout.confirm(),Component.translatable("screen.myriad.choose.confirm"),b->{
            // Read the latest sync again, never a map captured when the window opened.
            if(!canConfirm())return;
            Packets.sendToServer(new NetworkSetup.ChooseCharacterPayload(selectedId));onClose();
        }));
        confirm.active=canConfirm();
        addRenderableWidget(new PanelButton(layout.close(),Component.literal("×"),b->onClose()));
    }
    @Override public void tick(){
        if(!current.equals(ClientDataHolder.characterId())||!taken.equals(ClientDataHolder.takenCharacters()))rebuild();
        if(confirm!=null)confirm.active=canConfirm();
    }
    private void rebuild(){clearWidgets();init();}
    private void select(String id){selectedId=id;detailScroll=detailMaximum=0;if(confirm!=null)confirm.active=canConfirm();}
    private void scrollBy(int delta){
        int next=Math.clamp(scrollRow+delta,0,layout.maxScroll());
        if(next!=scrollRow){scrollRow=next;rebuild();}
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(layout.grid().contains(x,y)&&dy!=0){scrollBy(dy>0?-1:1);return true;}
        if(layout.details().contains(x,y)&&dy!=0){detailScroll=Math.clamp(detailScroll-(int)Math.round(dy*24),0,detailMaximum);return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
    private void fit(GuiGraphicsExtractor g,String value,int x,int y,int w,int color){
        String label=font.width(value)>w?font.plainSubstrByWidth(value,Math.max(1,w-font.width("…")))+"…":value;
        g.text(font,label,x,y,color);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        if(layout==null)return;
        XianxiaUiSkin.pickBackground(g,width,height);g.fill(0,0,width,height,0xA5051019);
        var p=layout.panel();plate(g,p,0xFF7D9589,0xEF0A1C24);
        fit(g,title.getString(),p.x()+12,p.y()+11,p.w()-48,GOLD);
        var grid=layout.grid();var details=layout.details();
        plate(g,new SkillChoiceLayout.Box(grid.x()-4,grid.y()-4,grid.w()+8,grid.h()+8),0xFF304D55,0x9909151E);
        plate(g,details,0xFF4A6D70,0xC70B202A);
        var role=CharacterType.byId(selectedId);if(role!=null)drawDetails(g,role,details,mx,my);
        int first=scrollRow*layout.columns()+1,last=Math.min(CharacterType.playableValues().length,(scrollRow+layout.rows())*layout.columns());
        var page=Component.translatable("screen.myriad.choose.range",first,last,CharacterType.playableValues().length);
        g.text(font,page,grid.x()+(grid.w()-font.width(page))/2,layout.previous().y()+7,MUTED);
        super.extractRenderState(g,mx,my,partial);
    }
    private void drawDetails(GuiGraphicsExtractor g,CharacterType role,SkillChoiceLayout.Box b,int mx,int my){
        fit(g,Component.translatable(role.nameKey()).getString(),b.x()+9,b.y()+10,b.w()-18,GOLD);
        String state=ClientDataHolder.get()==null?"screen.myriad.choose.syncing"
                :!ClientDataHolder.modEnabled()?"screen.myriad.choose.require_mod"
                :role.getId().equals(current)?"screen.myriad.choose.current"
                :available(role.getId())?"screen.myriad.choose.preview":"screen.myriad.choose.occupied";
        var status=Component.translatable(state);
        fit(g,status.getString(),b.x()+9,b.y()+25,b.w()-18,canConfirm()?JADE:MUTED);
        if(mx>=b.x()+9&&mx<b.right()-9&&my>=b.y()+25&&my<b.y()+39)
            g.setComponentTooltipForNextFrame(font,List.of(status),mx,my);
        g.fill(b.x()+8,b.y()+39,b.right()-8,b.y()+40,0xFF385C65);
        var v=layout.skillViewport();if(v.h()<=0)return;
        detailScroll=Math.clamp(detailScroll,0,detailMaximum);int y=v.y()+3-detailScroll;
        g.enableScissor(v.x(),v.y(),v.right(),v.bottom());
        String[] categories={"passive_a","passive_b","regular_a","regular_b","main_a","main_b","ultimate_a","ultimate_b"};
        for(var skill:FirstGroupSkills.candidates(role.getId())){
            var heading=Component.translatable("screen.myriad.choose."+categories[skill.index()]).append(" · ").append(skill.displayName());
            for(var line:font.split(heading,v.w()-8)){g.text(font,line,v.x()+3,y,GOLD);y+=11;}
            int headingEnd=y;
            for(var line:font.split(Component.literal(SkillTooltipDescriptions.describeLines(skill,1).stream().findFirst().orElse("")),v.w()-8)){
                g.text(font,line,v.x()+3,y,0xFFBFCED6);y+=10;
            }
            if(mx>=v.x()&&mx<v.right()&&my>=Math.max(v.y(),headingEnd-22)&&my<Math.min(v.bottom(),y)){
                var lines=new ArrayList<Component>();for(String line:SkillTooltipDescriptions.describeLines(skill,1))lines.add(Component.literal(line));
                g.setComponentTooltipForNextFrame(font,lines,mx,my);
            }
            y+=10;
        }
        detailMaximum=Math.max(0,y+detailScroll-v.bottom());g.disableScissor();
        if(detailMaximum>0){
            int h=Math.max(8,v.h()*v.h()/(v.h()+detailMaximum));
            scrollbar(g,new SkillChoiceLayout.Box(b.right()-5,v.y(),2,v.h()),new SkillChoiceLayout.Box(b.right()-5,v.y()+(v.h()-h)*detailScroll/detailMaximum,2,h));
        }
    }
    private class PanelButton extends Button {
        PanelButton(SkillChoiceLayout.Box b,Component label,OnPress press){super(b.x(),b.y(),b.w(),b.h(),label,press,DEFAULT_NARRATION);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float partial){
            SkillChoiceTheme.button(g,font,new SkillChoiceLayout.Box(getX(),getY(),getWidth(),getHeight()),getMessage().getString(),active,isHoveredOrFocused(),false);
        }
    }
    private final class RoleCard extends Button {
        private final CharacterType role;
        RoleCard(CharacterType role,SkillChoiceLayout.Box b){
            super(b.x(),b.y(),b.w(),b.h(),Component.translatable(role.nameKey()),button->select(role.getId()),DEFAULT_NARRATION);this.role=role;
            setTooltip(Tooltip.create(Component.translatable(role.nameKey())));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float partial){
            var b=new SkillChoiceLayout.Box(getX(),getY(),getWidth(),getHeight());
            boolean selected=role.getId().equals(selectedId),free=available(role.getId());card(g,b,selected,!free,isHoveredOrFocused());
            int size=Math.min(46,b.h()-23),x=b.x()+(b.w()-size)/2,y=b.y()+4;
            if(!CharacterPortrait.draw(g,role,x,y,size,free?1:.38f)){
                plate(g,new SkillChoiceLayout.Box(x,y,size,size),0xFF476773,0xD0152935);
                g.item(new ItemStack(ModItems.initialWeaponItem(role.getId())),x+(size-16)/2,y+(size-16)/2);
            }
            fit(g,Component.translatable(role.nameKey()).getString(),b.x()+4,b.bottom()-14,b.w()-8,free?GOLD:MUTED);
            if(selected||!free)g.text(font,selected?"◆":"×",b.right()-10,b.y()+3,free?JADE:MUTED);
        }
    }
}
