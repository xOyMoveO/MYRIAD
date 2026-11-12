package com.myriad.client.gui;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityFormulaText;
import com.myriad.character.rework.*;
import com.myriad.client.*;
import com.myriad.network.NetworkSetup;
import com.myriad.registry.ModAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import static com.myriad.client.gui.SkillChoiceTheme.*;

/** Horizontal four-group skill comparison. All ranks and eligibility come from server sync. */
public final class FirstGroupScreen extends Screen {
    private boolean attributes,supplies;
    private int scroll,scrollX,maximum,dragAxis;
    private final List<Action> actions=new ArrayList<>();
    private record Action(SkillChoiceLayout.Box box,String id,boolean enabled,boolean content){}
    public FirstGroupScreen(boolean attributes){
        super(Component.translatable("screen.myriad.g1.title"));this.attributes=attributes;
        ClientPacketDistributor.sendToServer(new NetworkSetup.RequestSyncPayload());
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){actions.clear();dragAxis=0;}
    private static String text(String key,Object... args){return Component.translatable(key,args).getString();}
    private boolean skills(){return !attributes&&!supplies;}
    private SkillChoiceLayout layout(){return SkillChoiceLayout.create(width,height,scrollX,skills()?scroll:0);}
    private void clampScroll(){var l=layout();scrollX=skills()?l.scrollX():0;scroll=Math.clamp(scroll,0,skills()?l.maxY():maximum);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        var l=layout();if(!l.viewport().contains(x,y))return super.mouseScrolled(x,y,dx,dy);
        if(skills()&&l.maxX()>0)scrollX-=Math.round((float)((dx!=0?dx:dy)*32));
        else scroll-=Math.round((float)(dy*24));
        clampScroll();actions.clear();return true;
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
        if(skills()&&(event.key()==GLFW.GLFW_KEY_LEFT||event.key()==GLFW.GLFW_KEY_RIGHT)){
            scrollX+=event.key()==GLFW.GLFW_KEY_RIGHT?80:-80;clampScroll();actions.clear();return true;}
        return super.keyPressed(event);
    }
    private boolean drag(double x,double y){
        var l=layout();
        if(dragAxis==1)scrollX=l.scrollToHorizontal(x);
        if(dragAxis==2){
            if(skills())scroll=l.scrollToVertical(y);
            else {var track=l.verticalTrack();int thumb=Math.min(track.h(),Math.max(18,track.h()*track.h()/Math.max(1,track.h()+maximum)));
                scroll=Math.clamp((int)Math.round((y-track.y()-thumb/2.0)*maximum/Math.max(1,track.h()-thumb)),0,maximum);}
        }
        clampScroll();actions.clear();return true;
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,double dx,double dy){
        return dragAxis!=0&&event.buttonInfo().button()==0?drag(event.x(),event.y()):super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){
        if(dragAxis!=0&&event.buttonInfo().button()==0){dragAxis=0;return true;}
        return super.mouseReleased(event);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean twice){
        if(event.buttonInfo().button()!=0)return super.mouseClicked(event,twice);
        var l=layout();
        if(skills()&&l.maxX()>0&&l.horizontalTrack().contains(event.x(),event.y())){dragAxis=1;return drag(event.x(),event.y());}
        if((skills()?l.maxY():maximum)>0&&l.verticalTrack().contains(event.x(),event.y())){dragAxis=2;return drag(event.x(),event.y());}
        for(var a:actions){
            if(!a.box.contains(event.x(),event.y())||a.content&&!l.viewport().contains(event.x(),event.y()))continue;
            if(!a.enabled)return true;
            if(a.id.equals("close")){onClose();return true;}
            if(Set.of("skills","attributes","supplies").contains(a.id)){
                attributes=a.id.equals("attributes");supplies=a.id.equals("supplies");scroll=scrollX=maximum=0;actions.clear();return true;
            }
            if(a.id.startsWith("g1_attribute_")||com.myriad.game.TacticalSupplySystem.isSupply(a.id))
                ClientPacketDistributor.sendToServer(new NetworkSetup.ShopBuyPayload(a.id));
            else ClientPacketDistributor.sendToServer(new NetworkSetup.UpgradeSkillPayload(a.id));
            return true;
        }
        return super.mouseClicked(event,twice);
    }
    private void button(GuiGraphicsExtractor g,SkillChoiceLayout.Box box,String label,String id,boolean enabled,boolean content,boolean selected,int mx,int my){
        boolean hover=box.contains(mx,my)&&(!content||layout().viewport().contains(mx,my));
        SkillChoiceTheme.button(g,Minecraft.getInstance().font,box,label,enabled,hover,selected);
        actions.add(new Action(box,id,enabled,content));
    }
    private void fitText(GuiGraphicsExtractor g,String value,int x,int y,int w,int color){
        var font=Minecraft.getInstance().font;
        String visible=font.width(value)>w?font.plainSubstrByWidth(value,Math.max(1,w-font.width("…")))+"…":value;
        g.text(font,visible,x,y,color);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        super.extractRenderState(g,mx,my,partial);actions.clear();var data=ClientDataHolder.get();var font=Minecraft.getInstance().font;
        if(data==null)return;
        if(!FirstGroupSkills.active(data.characterId())){onClose();return;}
        clampScroll();var l=layout();var p=l.panel();var v=l.viewport();
        backdrop(g,p);
        fitText(g,text("screen.myriad.g1.title")+" · "+text("character.myriad."+data.characterId()),p.x()+26,p.y()+22,p.w()-72,GOLD);
        fitText(g,text("screen.myriad.g1.status",data.level(),data.skillPoints(),data.gold()),p.x()+26,p.y()+37,p.w()-52,JADE);
        if(mx>=p.x()+26&&mx<p.right()-42&&my>=p.y()+20&&my<p.y()+47)
            g.setComponentTooltipForNextFrame(font,List.of(Component.translatable("screen.myriad.g1.vitals",
                    Math.round(data.health()),Math.round(data.maxHealth()),data.mana())),mx,my);
        button(g,new SkillChoiceLayout.Box(p.right()-34,p.y()+18,16,16),"×","close",true,false,false,mx,my);
        String[] names={text("screen.myriad.g1.skills"),text("screen.myriad.g1.attributes"),text("screen.myriad.shop.tab_supply")};
        String[] ids={"skills","attributes","supplies"};
        for(int i=0;i<3;i++)button(g,l.tab(i),names[i],ids[i],true,false,i==(supplies?2:attributes?1:0),mx,my);
        g.enableScissor(v.x(),v.y(),v.right(),v.bottom());
        if(skills())drawSkills(g,l,mx,my);
        else if(attributes)drawAttributes(g,v.x(),v.y()-scroll,v.w(),v.h(),mx,my);
        else drawSupplies(g,v.x(),v.y()-scroll,v.w(),v.h(),mx,my);
        g.disableScissor();
        if(skills()&&l.maxX()>0)scrollbar(g,l.horizontalTrack(),l.horizontalThumb());
        int maxY=skills()?l.maxY():maximum;
        if(maxY>0){
            var track=l.verticalTrack();var thumb=l.verticalThumb();
            if(!skills()){int h=Math.min(track.h(),Math.max(18,track.h()*track.h()/(track.h()+maxY)));
                thumb=new SkillChoiceLayout.Box(track.x(),track.y()+(track.h()-h)*scroll/maxY,track.w(),h);}
            scrollbar(g,track,thumb);
        }
        g.fill(p.x()+18,p.bottom()-20,p.right()-18,p.bottom()-6,0xED091B21);
        fitText(g,text(skills()?"screen.myriad.g1.horizontal_hint":"screen.myriad.g1.page_hint"),p.x()+24,p.bottom()-18,p.w()-48,MUTED);
    }
    private void drawSkills(GuiGraphicsExtractor g,SkillChoiceLayout l,int mx,int my){
        var data=ClientDataHolder.get();var font=Minecraft.getInstance().font;var list=FirstGroupSkills.candidates(data.characterId());
        String[] groups={"passive","regular","main","ultimate"};
        for(int group=0;group<4;group++){
            var b=l.column(group);if(!b.overlaps(l.viewport()))continue;
            String heading=text("screen.myriad.g1."+groups[group]);
            String rule=text(group==1?"screen.myriad.g1.both":"screen.myriad.g1.one");
            fitText(g,heading,b.x()+Math.max(5,(b.w()-font.width(heading))/2),b.y()+1,b.w()-10,GOLD);
            fitText(g,rule,b.x()+Math.max(5,(b.w()-font.width(rule))/2),b.y()+13,b.w()-10,JADE);
            g.fill(b.x()+5,b.y()+24,b.right()-5,b.y()+25,0x775D9D91);
        }
        for(var skill:list){
            int index=skill.index(),rank=data.skillLevels().getOrDefault(skill.getId(),0),opposite=FirstGroupSkills.opposite(index);
            var b=l.card(index);if(!b.overlaps(l.viewport()))continue;
            boolean locked=opposite>=0&&data.skillLevels().getOrDefault(list.get(opposite).getId(),0)>0;
            String error=FirstGroupSkills.learnError(data.characterId(),data.skillLevels(),data.skillPoints(),data.level(),skill.getId());
            card(g,b,rank>0,locked,l.canHit(b,mx,my));
            SkillIcon.draw(g,font,b.x()+8,b.y()+8,26,skill,skill.isUltimate(),!locked,"");
            fitText(g,skill.displayName(),b.x()+42,b.y()+9,b.w()-50,locked?MUTED:GOLD);
            fitText(g,text(rank>0?"screen.myriad.g1.learned_rank":"screen.myriad.g1.unlearned_rank",rank,skill.maxRank()),
                    b.x()+42,b.y()+23,b.w()-50,locked?MUTED:JADE);
            String summary=AbilityFormulaText.evaluate(skill.description(),Math.max(1,rank),ClientDataHolder.attrValue(ModAttributes.SPELL_POWER.value().getDescriptionId()));
            var lines=font.split(Component.literal(summary),b.w()-16);int shown=b.h()>=104?2:1;
            for(int i=0;i<Math.min(shown,lines.size());i++)g.text(font,lines.get(i),b.x()+8,b.y()+38+i*10,locked?MUTED:0xFFC5D5D2);
            String resources=skill.isPassive()?text("screen.myriad.g1.passive_cost"):
                    text("screen.myriad.g1.compact_resource",Math.round(skill.getManaCost()),String.format(Locale.ROOT,"%.1f",skill.getCooldownTicks(Math.max(1,rank))/20.0));
            fitText(g,resources,b.x()+8,b.bottom()-38,b.w()-16,0xFF9DC2D0);
            String action=locked?text("screen.myriad.g1.locked"):rank>=skill.maxRank()?text("screen.myriad.g1.maxed"):
                    text(rank==0&&opposite>=0?"screen.myriad.g1.choose":"screen.myriad.g1.upgrade");
            button(g,l.action(index),action,skill.getId(),error==null,true,false,mx,my);
            if(l.canHit(b,mx,my))skillTooltip(g,skill,rank,error,mx,my);
        }
        maximum=l.maxY();
    }
    private void skillTooltip(GuiGraphicsExtractor g,AbilitySkill skill,int rank,String error,int mx,int my){
        var data=ClientDataHolder.get();var font=Minecraft.getInstance().font;var tooltip=new ArrayList<Component>();
        tooltip.add(Component.literal(skill.displayName()));
        for(String line:SkillTooltipDescriptions.describeLines(skill,Math.max(1,rank)))tooltip.add(Component.literal(line));
        tooltip.add(Component.translatable("screen.myriad.g1.strength",Math.round(ClientDataHolder.attrValue(ModAttributes.SPELL_POWER.value().getDescriptionId()))));
        if(!skill.isPassive()){
            double reduction=Math.clamp(ClientDataHolder.attrValue(ModAttributes.COOLDOWN_REDUCTION.value().getDescriptionId()),0,60);
            double cooldown=data.training()&&data.noCooldown()?0:skill.getCooldownTicks(Math.max(1,rank))*(1-reduction/100)/20;
            tooltip.add(Component.translatable("screen.myriad.g1.current_cost",Math.round(skill.getManaCost()),
                    String.format(Locale.ROOT,"%.1f",cooldown),String.format(Locale.ROOT,"%.1f",rank>0?ClientDataHolder.cooldownTicks(skill.slot())/20.0:0)));
        }
        if(error!=null)tooltip.add(Component.translatable(error));
        g.setComponentTooltipForNextFrame(font,tooltip,mx,my);
    }
    private void drawAttributes(GuiGraphicsExtractor g,int x,int y,int available,int visibleHeight,int mx,int my){
        var data=ClientDataHolder.get();var font=Minecraft.getInstance().font;
        int total=FirstGroupGrowth.ATTRIBUTES.stream().mapToInt(k->data.buffs().getOrDefault("g1_attribute_"+k,0)).sum();
        fitText(g,text("screen.myriad.g1.attribute_budget",total),x+4,y,available-8,GOLD);
        String[] effects={"+15普攻伤害","+20技能强度","+0.06攻速 / +1.5%移速","+5物防 / +5法防","+3%冷却缩减"};
        for(int i=0;i<5;i++){
            String key=FirstGroupGrowth.ATTRIBUTES.get(i);int rank=data.buffs().getOrDefault("g1_attribute_"+key,0),cost=FirstGroupGrowth.price(rank),sy=y+19+i*38;
            var box=new SkillChoiceLayout.Box(x,sy,available,34);plate(g,box,0xFF3C716F,INK);
            fitText(g,text("screen.myriad.g1.attribute."+key)+" "+rank+"/10",x+7,sy+4,available-92,GOLD);
            fitText(g,effects[i],x+7,sy+17,available-92,JADE);
            boolean free=data.training()&&data.noGoldCost(),eligible=rank<10&&total<15&&(free||data.gold()>=cost)&&UiFeedback.shopAllowed;
            button(g,new SkillChoiceLayout.Box(x+available-77,sy+6,70,22),text("screen.myriad.g1.buy",free?0:cost),"g1_attribute_"+key,eligible,true,false,mx,my);
        }
        fitText(g,text("screen.myriad.g1.tuning"),x+4,y+218,available-8,MUTED);
        maximum=Math.max(0,235-visibleHeight);
    }
    private void drawSupplies(GuiGraphicsExtractor g,int x,int y,int available,int visibleHeight,int mx,int my){
        var data=ClientDataHolder.get();var font=Minecraft.getInstance().font;int row=0;
        for(String id:com.myriad.game.TacticalSupplySystem.supplyIds()){
            int sy=y+row++*38,cost=com.myriad.game.TacticalSupplySystem.supplyPrice(id);
            var box=new SkillChoiceLayout.Box(x,sy,available,34);plate(g,box,0xFF3C716F,INK);
            fitText(g,text("item.myriad."+id),x+7,sy+10,available-92,GOLD);
            boolean free=data.training()&&data.noGoldCost();
            button(g,new SkillChoiceLayout.Box(x+available-77,sy+6,70,22),text("screen.myriad.g1.buy",free?0:cost),id,
                    UiFeedback.shopAllowed&&(free||data.gold()>=cost),true,false,mx,my);
            if(layout().canHit(box,mx,my))g.setComponentTooltipForNextFrame(font,List.of(Component.translatable("item.myriad."+id+".desc")),mx,my);
        }
        maximum=Math.max(0,row*38-visibleHeight);
    }
}
