package com.myriad.client.gui;

import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.client.ClientDataHolder;
import com.myriad.client.SkillIcon;
import com.myriad.client.SkillTooltipDescriptions;
import com.myriad.client.UiFeedback;
import com.myriad.game.TacticalSupplySystem;
import com.myriad.network.NetworkSetup;
import com.myriad.registry.ModAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import com.myriad.network.Packets;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import static com.myriad.client.gui.SkillChoiceTheme.*;
import com.myriad.client.gui.SkillChoiceLayout.Box;

/** Icon navigation and four skill rows. Purchases always use the existing server requests. */
public final class FirstGroupScreen extends Screen {
    private int page,scroll,dragAxis,tooltipScroll,labelWidth=32;
    private String role="",focused="";
    private final SkillChoiceInteraction selection=new SkillChoiceInteraction();
    private final List<Action> actions=new ArrayList<>();
    private TooltipTarget tooltip,nextTooltip;
    private SkillChoiceTooltipLayout tooltipLayout;
    private Object submittedSnapshot;
    private long submittedAt;
    private record Action(Box box,String id,boolean enabled,boolean content,boolean item){}
    private record TooltipTarget(String id,Box anchor,List<String> lines){}
    private record TextLine(FormattedCharSequence text,int color){}

    public FirstGroupScreen(boolean attributes){
        super(Component.translatable("screen.myriad.g1.title"));page=attributes?1:0;
        Packets.sendToServer(new NetworkSetup.RequestSyncPayload());
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        actions.clear();dragAxis=0;clearTooltip();
        var font=Minecraft.getInstance().font;
        labelWidth=20;
        for(String key:new String[]{"passive","main","regular","ultimate"})
            labelWidth=Math.max(labelWidth,font.width(text("screen.myriad.g1."+key))+6);
    }
    private static String text(String key,Object... args){return Component.translatable(key,args).getString();}
    private SkillChoiceLayout layout(){return SkillChoiceLayout.create(width,height,page==0?scroll:0,labelWidth);}
    private int maximum(){var l=layout();return page==0?l.maxY():page==1?l.attributeMaximum():Math.max(0,TacticalSupplySystem.supplyIds().size()*32-l.viewport().h());}
    private void clampScroll(){scroll=Math.clamp(scroll,0,maximum());}
    private void clearTooltip(){tooltip=nextTooltip=null;tooltipLayout=null;tooltipScroll=0;}
    private boolean pending(){
        if(submittedSnapshot!=null&&(ClientDataHolder.get()!=submittedSnapshot||System.nanoTime()-submittedAt>1_500_000_000L))submittedSnapshot=null;
        return submittedSnapshot!=null;
    }
    private String error(String id) {
        var data = ClientDataHolder.get();
        if (data == null) return "screen.myriad.g1.loading";
        if (pending()) return "screen.myriad.g1.processing";
        if (id.startsWith("g1_attribute_")) {
            String key = id.substring(13);
            if (!FirstGroupGrowth.ATTRIBUTES.contains(key)) return "message.myriad.g1.invalid_skill";
            int rank = data.buffs().getOrDefault(id, 0);
            if (!UiFeedback.shopAllowed) return "message.myriad.shop_base_only";
            return FirstGroupGrowth.purchaseError(rank, data.gold(), data.training() && data.noGoldCost());
        }
        if (TacticalSupplySystem.isSupply(id)) {
            if (!UiFeedback.shopAllowed) return "message.myriad.shop_base_only";
            return !(data.training() && data.noGoldCost()) && data.gold() < TacticalSupplySystem.supplyPrice(id)
                    ? "message.myriad.equip_no_gold" : null;
        }
        return FirstGroupSkills.learnError(data.characterId(), data.skillLevels(), data.skillPoints(), data.level(), id);
    }
    private void submit(String id) {
        if (error(id) != null) return;
        submittedSnapshot = ClientDataHolder.get();
        submittedAt = System.nanoTime();
        if (id.startsWith("g1_attribute_") || TacticalSupplySystem.isSupply(id)) {
            Packets.sendToServer(new NetworkSetup.ShopBuyPayload(id));
        } else {
            Packets.sendToServer(new NetworkSetup.UpgradeSkillPayload(id));
        }
        actions.clear();
    }
    private boolean perform(Action action, boolean twice) {
        if (action.id.equals("close")) {
            onClose();
            return true;
        }
        if (action.id.startsWith("page:")) {
            page = Integer.parseInt(action.id.substring(5));
            scroll = 0;
            selection.reset();
            focused = action.id;
            clearTooltip();
            actions.clear();
            return true;
        }
        if (action.item) {
            if (selection.select(action.id, twice, error(action.id) == null)) submit(action.id);
            return true;
        }
        if (action.id.equals("confirm")) {
            String id = selection.selected();
            if (selection.confirm(id, twice, !id.isEmpty() && error(id) == null)) submit(id);
            return true;
        }
        if (!twice && error(action.id) == null) submit(action.id);
        return true;
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean twice) {
        if (event.buttonInfo().button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, twice);
        var layout = layout();
        double x = event.x(), y = event.y();
        if (maximum() > 0 && layout.verticalTrack().contains(x, y)) {
            dragAxis = 1;
            return drag(y);
        }
        for (var action : List.copyOf(actions)) {
            if (!action.box.contains(x, y) || action.content && !layout.viewport().contains(x, y)) continue;
            if (tooltipLayout != null && tooltipLayout.box().contains(x, y) && tooltip != null
                    && !tooltip.anchor.contains(x, y) && action.content) return true;
            focused = action.id;
            return perform(action, twice);
        }
        return super.mouseClicked(event, twice);
    }
    private boolean drag(double y){scroll=layout().scrollToVertical(y,maximum());clearTooltip();actions.clear();return true;}
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,double dx,double dy){
        return dragAxis!=0&&event.buttonInfo().button()==InputConstants.MOUSE_BUTTON_LEFT?drag(event.y()):super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){
        if(dragAxis!=0&&event.buttonInfo().button()==InputConstants.MOUSE_BUTTON_LEFT){dragAxis=0;return true;}return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(tooltip!=null&&tooltipLayout!=null&&(tooltip.anchor.contains(x,y)||tooltipLayout.box().contains(x,y))&&tooltipLayout.maximum()>0){
            tooltipScroll=Math.clamp(tooltipScroll-(int)Math.round(dy*3),0,tooltipLayout.maximum());return true;
        }
        if(!layout().viewport().contains(x,y))return super.mouseScrolled(x,y,dx,dy);
        scroll-=(int)Math.round(dy*24);clampScroll();clearTooltip();actions.clear();return true;
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == InputConstants.KEY_TAB && !actions.isEmpty()) {
            int at = -1;
            for (int i = 0; i < actions.size(); i++) {
                if (actions.get(i).id.equals(focused)) at = i;
            }
            Action action = actions.get((at + 1) % actions.size());
            focused = action.id;
            if (action.content) {
                var view = layout().viewport();
                if (action.box.y() < view.y()) scroll -= view.y() - action.box.y();
                else if (action.box.bottom() > view.bottom()) scroll += action.box.bottom() - view.bottom();
                clampScroll();
            }
            clearTooltip();
            return true;
        }
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE) {
            for (var action : List.copyOf(actions)) {
                if (action.id.equals(focused)) return perform(action, false);
            }
        }
        if (event.key() == InputConstants.KEY_PAGEDOWN || event.key() == InputConstants.KEY_PAGEUP) {
            int delta = event.key() == InputConstants.KEY_PAGEDOWN ? 8 : -8;
            if (tooltipLayout != null) {
                tooltipScroll = Math.clamp(tooltipScroll + delta, 0, tooltipLayout.maximum());
                return true;
            }
            scroll += delta * 8;
            clampScroll();
            return true;
        }
        return super.keyPressed(event);
    }
    private void fit(GuiGraphicsExtractor g,String value,int x,int y,int w,int color){
        var f=Minecraft.getInstance().font;String visible=f.width(value)>w?f.plainSubstrByWidth(value,Math.max(1,w-f.width("…")))+"…":value;g.text(f,visible,x,y,color);
    }
    private void scaledFit(GuiGraphicsExtractor graphics, String value, int x, int y, int width, int color, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale);
        fit(graphics, value, 0, 0, Math.max(1, (int) (width / scale)), color);
        graphics.pose().popMatrix();
    }
    private void centeredScaled(GuiGraphicsExtractor graphics, String value, Box box, int y, int color, float scale) {
        int width = (int) Math.ceil(Minecraft.getInstance().font.width(value) * scale);
        scaledFit(graphics, value, box.x() + (box.w() - width) / 2, y, box.w(), color, scale);
    }
    private void button(GuiGraphicsExtractor g,Box b,String label,String id,boolean enabled,boolean content,boolean selected,int mx,int my){
        boolean hover=b.contains(mx,my)&&(!content||layout().viewport().contains(mx,my));
        SkillChoiceTheme.button(g,Minecraft.getInstance().font,b,label,enabled,hover||focused.equals(id),selected);
        actions.add(new Action(b,id,enabled,content,false));
    }
    private void offer(String id, Box anchor, Supplier<List<String>> details,
            int mx, int my, boolean content) {
        boolean sameSource = tooltip != null && tooltip.id.equals(id)
                && tooltip.anchor.equals(anchor);
        boolean over = anchor.contains(mx, my)
                && (!content || layout().viewport().contains(mx, my));
        boolean inTooltip = tooltipLayout != null && tooltipLayout.box().contains(mx, my);
        if (inTooltip && tooltip != null && !tooltip.anchor.contains(mx, my) && !sameSource) {
            return;
        }
        if (over || sameSource && inTooltip) {
            nextTooltip = new TooltipTarget(id, anchor, details.get());
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        super.extractRenderState(g,mx,my,partial);actions.clear();nextTooltip=null;
        var d=ClientDataHolder.get();if(d==null){g.text(Minecraft.getInstance().font,text("screen.myriad.g1.loading"),20,20,GOLD);return;}
        if(!FirstGroupSkills.active(d.characterId())){onClose();return;}
        if(!role.equals(d.characterId())){role=d.characterId();selection.reset();scroll=0;clearTooltip();}
        pending();clampScroll();var l=layout();var p=l.panel();var v=l.viewport();var f=Minecraft.getInstance().font;
        backdrop(g,p);
        g.nextStratum();
        int side = Math.max(24, p.w() * 13 / 100);
        float headerScale = p.w() < 240 ? .9f : 1;
        scaledFit(g,text("screen.myriad.g1.title")+" · "+text("character.myriad."+d.characterId()),
                p.x()+side,l.titleY(),p.w()-side-48,GOLD,headerScale);
        scaledFit(g,text(p.w()<240?"screen.myriad.g1.status_compact":"screen.myriad.g1.status",d.level(),d.skillPoints(),d.gold()),
                p.x()+side,l.statusY(),p.w()-2*side,JADE,headerScale);
        offer("vitals",new Box(p.x()+side,l.titleY(),p.w()-2*side,22),
                ()->List.of(text("screen.myriad.g1.vitals",Math.round(d.health()),Math.round(d.maxHealth()),d.mana())),mx,my,false);
        button(g,new Box(p.right()-40,l.titleY(),16,16),"×","close",true,false,false,mx,my);
        SkillChoiceTheme.divider(g,p.x()+side,p.right()-side,l.headerDividerY(),0xFF69B7A5);
        int dividerX=l.navigationDividerX();
        g.fill(dividerX,v.y()+2,dividerX+1,v.bottom()-2,0x99569B8B);
        SkillChoiceTheme.diamond(g,dividerX,v.y()+2,0xFF69B7A5);
        SkillChoiceTheme.diamond(g,dividerX,v.bottom()-2,0xFF69B7A5);
        String[] ids={"skills","attributes","supplies"},keys={"screen.myriad.g1.skills","screen.myriad.g1.attributes","screen.myriad.shop.tab_supply"};
        for(int i=0;i<3;i++){
            var b=l.tab(i);button(g,b,"","page:"+i,true,false,page==i,mx,my);
            int inset=Math.max(4,(b.w()-18)/2);CultivationIcons.navigation(g,ids[i],new Box(b.x()+inset,b.y()+inset,b.w()-2*inset,b.h()-2*inset),page==i?GOLD:JADE);
            int navIndex=i;
            offer("nav:"+i,b,()->List.of(text(keys[navIndex])),mx,my,false);
        }
        g.enableScissor(v.x(),v.y(),v.right(),v.bottom());
        if(page==0)drawSkills(g,l,mx,my);else if(page==1)drawAttributes(g,l,mx,my);else drawSupplies(g,l,mx,my);
        g.disableScissor();
        if(maximum()>0)scrollbar(g,l.verticalTrack(),l.verticalThumb(maximum(),scroll));
        SkillChoiceTheme.divider(g,v.x(),v.right(),l.footerDividerY(),0xAA69B7A5);
        if(page<2)drawSelected(g,l,mx,my);
        else scaledFit(g,text("screen.myriad.g1.supply_hint"),v.x(),l.footerDividerY()+8,v.w(),MUTED,l.footerScale());
        drawTooltip(g,mx,my);
    }
    private void tile(GuiGraphicsExtractor graphics, Box box, String id, int mouseX, int mouseY) {
        boolean chosen = selection.selected().equals(id);
        boolean hover = layout().canHit(box, mouseX, mouseY);
        if (chosen || hover || focused.equals(id)) {
            int rankHeight = (int) Math.ceil(Minecraft.getInstance().font.lineHeight * layout().rankScale());
            int size = page == 0 ? Math.min(36, box.h() - rankHeight) : Math.min(26, box.w() - 6);
            int x = box.x() + (box.w() - size) / 2;
            int y = box.y() + (page == 1 ? 2 : 0);
            int edge = chosen ? GOLD : JADE;
            graphics.fill(x-1,y-1,x+size+1,y,edge);
            graphics.fill(x-1,y+size,x+size+1,y+size+1,edge);
            graphics.fill(x-1,y,x,y+size,edge);
            graphics.fill(x+size,y,x+size+1,y+size,edge);
        }
        actions.add(new Action(box, id, true, true, true));
    }
    private void drawSkills(GuiGraphicsExtractor graphics, SkillChoiceLayout layout, int mouseX, int mouseY) {
        var data = ClientDataHolder.get();
        var font = Minecraft.getInstance().font;
        var candidates = FirstGroupSkills.candidates(data.characterId());
        String[] groups = {"passive", "regular", "main", "ultimate"};
        for (int group = 0; group < 4; group++) {
            var box = layout.category(group);
            if (!box.overlaps(layout.viewport())) continue;
            int titleY = box.y() + Math.max(1, (box.h() - font.lineHeight) / 2 - 3);
            int titleWidth = font.width(text("screen.myriad.g1." + groups[group]));
            fit(graphics, text("screen.myriad.g1." + groups[group]),
                    box.x() + Math.max(0,(box.w()-titleWidth)/2),titleY,box.w(),GOLD);
            if (box.h() >= 17) {
                SkillChoiceTheme.divider(graphics,box.x(),box.right(),box.bottom()-2,0xAA69B7A5);
            }
            int category = group;
            offer("group:" + group, box, () -> List.of(text("screen.myriad.g1." + groups[category]),
                    text(category == 1 ? "screen.myriad.g1.both" : "screen.myriad.g1.one")), mouseX, mouseY, true);
        }
        for (var skill : candidates) {
            var box = layout.card(skill.index());
            int rank = data.skillLevels().getOrDefault(skill.getId(), 0);
            int other = FirstGroupSkills.opposite(skill.index());
            boolean locked = other >= 0 && data.skillLevels().getOrDefault(candidates.get(other).getId(), 0) > 0;
            if (!box.overlaps(layout.viewport())) {
                actions.add(new Action(box, skill.getId(), true, true, true));
                continue;
            }
            tile(graphics, box, skill.getId(), mouseX, mouseY);
            float rankScale = layout.rankScale();
            int rankHeight = (int) Math.ceil(font.lineHeight * rankScale);
            int size = Math.min(36, box.h() - rankHeight);
            int iconX = box.x() + (box.w() - size) / 2;
            int iconY = box.y();
            SkillIcon.drawArtwork(graphics, font, iconX, iconY, size, skill);
            if (skill.index() < 2 || skill.index() >= 4) {
                graphics.fill(iconX,iconY,iconX+(int)Math.ceil(7*rankScale),iconY+rankHeight,0xEE071116);
                scaledFit(graphics,(skill.index()&1)==0?"A":"B",iconX+1,iconY,8,GOLD,rankScale);
            }
            String label = locked ? text("screen.myriad.g1.locked") : rank + "/" + skill.maxRank();
            centeredScaled(graphics,label,box,box.bottom()-rankHeight,locked?MUTED:rank>0?GOLD:JADE,rankScale);
            offer(skill.getId() + ":tile", box, () -> skillLines(skill, rank, error(skill.getId())), mouseX, mouseY, true);
        }
    }
    private List<String> skillLines(AbilitySkill skill,int rank,String reason){
        var d=ClientDataHolder.get();var lines=new ArrayList<String>();lines.add(skill.displayName());
        lines.add(skill.baseAvailable()?text("screen.myriad.rivet.anchor_rank",rank,skill.maxRank()):text(rank>0?"screen.myriad.g1.learned_rank":"screen.myriad.g1.unlearned_rank",rank,skill.maxRank()));
        int level=skill.baseAvailable()?rank:Math.max(1,rank);
        lines.addAll(SkillTooltipDescriptions.describeLines(skill,level));
        lines.add(text("screen.myriad.g1.strength",Math.round(ClientDataHolder.attrValue(ModAttributes.SPELL_POWER.value().getDescriptionId()))));
        if(skill.isPassive())lines.add(text("screen.myriad.g1.passive_cost"));
        else{
            double reduction=Math.clamp(ClientDataHolder.attrValue(ModAttributes.COOLDOWN_REDUCTION.value().getDescriptionId()),
                    0,ModAttributes.MAX_COOLDOWN_REDUCTION);
            double cooldown=d.training()&&d.noCooldown()?0:skill.getCooldownTicks(level)*(1-reduction/100)/20;
            lines.add(text("screen.myriad.g1.current_cost",Math.round(skill.getManaCost()),String.format(Locale.ROOT,"%.1f",cooldown),String.format(Locale.ROOT,"%.1f",rank>0||skill.baseAvailable()?ClientDataHolder.cooldownTicks(skill.slot())/20.0:0)));
        }
        lines.add(text(skill.index()==2||skill.index()==3?"screen.myriad.g1.both":"screen.myriad.g1.one"));
        if(reason!=null)lines.add(text(reason));
        return lines;
    }
    private long attributeTotal() {
        return FirstGroupGrowth.ATTRIBUTES.stream()
                .mapToLong(key -> ClientDataHolder.syncedCounter("g1_attribute_" + key)).sum();
    }
    private int supplyCost(String id) {
        var data = ClientDataHolder.get();
        return data.training() && data.noGoldCost() ? 0 : TacticalSupplySystem.supplyPrice(id);
    }
    private static String nextAttributePrice(int rank) {
        long price = FirstGroupGrowth.price(rank);
        return price == FirstGroupGrowth.PRICE_OVERFLOW ? text("message.myriad.g1.attribute_price_overflow")
                : text("screen.myriad.g1.attribute_next_price", price);
    }
    private static boolean hasteCapped(String key) {
        return key.equals("cooldown")
                && ClientDataHolder.attrValue(ModAttributes.COOLDOWN_REDUCTION.value().getDescriptionId()) >= ModAttributes.MAX_COOLDOWN_REDUCTION;
    }
    private List<String> attributeLines(String key) {
        var data = ClientDataHolder.get();
        String id = "g1_attribute_" + key;
        int rank = ClientDataHolder.syncedCounter(id);
        var lines = new ArrayList<String>();
        lines.add(text("screen.myriad.g1.attribute." + key));
        lines.add(text("screen.myriad.g1.attribute_rank", rank));
        lines.add(text("screen.myriad.g1.attribute_effect." + key));
        if (hasteCapped(key)) lines.add(text("screen.myriad.g1.attribute_haste_capped", ModAttributes.MAX_COOLDOWN_REDUCTION));
        lines.add(nextAttributePrice(rank));
        if (data.training() && data.noGoldCost()) lines.add(text("screen.myriad.g1.attribute_free"));
        String reason = error(id);
        if (reason != null && !reason.equals("message.myriad.g1.attribute_price_overflow")) lines.add(text(reason));
        lines.add(text("screen.myriad.g1.attribute_click_hint"));
        return lines;
    }
    private void drawAttributes(GuiGraphicsExtractor g,SkillChoiceLayout l,int mx,int my){
        var f=Minecraft.getInstance().font;var v=l.viewport();
        var budget=f.split(Component.literal(text("screen.myriad.g1.attribute_budget_compact",attributeTotal())),v.w()-4);
        for(int i=0;i<budget.size();i++)g.text(f,budget.get(i),v.x()+2,v.y()+i*10-scroll,GOLD);
        for(int i=0;i<FirstGroupGrowth.ATTRIBUTES.size();i++){
            String key=FirstGroupGrowth.ATTRIBUTES.get(i),id="g1_attribute_"+key;
            int rank=ClientDataHolder.syncedCounter(id);var b=l.attribute(i,scroll);var row=l.attributeRow(i,scroll);
            actions.add(new Action(b,id,true,true,true));
            if(!row.overlaps(v))continue;
            boolean selected=selection.selected().equals(id),hover=l.canHit(b,mx,my);
            plate(g,new Box(b.x()-2,b.y()-2,b.w()+4,b.h()+4),selected?GOLD:hover||focused.equals(id)?JADE:0xFF315955,0xB0071C22);
            CultivationIcons.attribute(g,key,b);
            centeredScaled(g,text("screen.myriad.g1.attribute."+key),new Box(b.x()-4,b.y(),b.w()+8,b.h()),b.bottom()+3,selected?GOLD:JADE,l.attributeRowHeight()<40?.6f:.7f);
            var details = l.attributeDetails(i,scroll);
            String quote = nextAttributePrice(rank);
            scaledFit(g,text("screen.myriad.g1.attribute_rank",rank),details.x(),b.y()+2,details.w(),JADE,.8f);
            scaledFit(g,quote,details.x(),b.y()+12,details.w(),GOLD,Math.min(.8f,details.w()/(float)Math.max(1,f.width(quote))));
            if (hasteCapped(key)) scaledFit(g,text("screen.myriad.g1.attribute_haste_limit",ModAttributes.MAX_COOLDOWN_REDUCTION),
                    details.x(),b.y()+22,details.w(),GOLD,.8f);
            offer(id+":tile",b,()->attributeLines(key),mx,my,true);
        }
    }
    private void drawSupplies(GuiGraphicsExtractor g,SkillChoiceLayout l,int mx,int my){
        int i=0;for(String id:TacticalSupplySystem.supplyIds()){
            var b=l.supply(i,scroll);var buy=l.supplyAction(i++,scroll);if(!b.overlaps(l.viewport()))continue;
            plate(g,b,0xFF3C716F,INK);CultivationIcons.supply(g,id,new Box(b.x()+4,b.y()+5,20,20));
            fit(g,text("item.myriad."+id),b.x()+29,b.y()+10,Math.max(1,b.w()-108),GOLD);
            button(g,buy,text("screen.myriad.g1.buy",supplyCost(id)),id,error(id)==null,true,false,mx,my);
            offer(id+":supply",b,()->{
                var lines=new ArrayList<String>();
                lines.add(text("item.myriad."+id));
                lines.add(text("item.myriad."+id+".desc"));
                lines.add(text("screen.myriad.g1.buy",supplyCost(id)));
                String reason=error(id);
                if(reason!=null)lines.add(text(reason));
                return lines;
            },mx,my,true);
        }
    }
    private void drawSelected(GuiGraphicsExtractor graphics, SkillChoiceLayout layout, int mouseX, int mouseY) {
        String id = selection.selected();
        var font = Minecraft.getInstance().font;
        String name = text("screen.myriad.g1.select_prompt");
        String label = text("screen.myriad.g1.learn");
        Supplier<List<String>> details = () -> List.of(text("screen.myriad.g1.select_prompt"));
        boolean valid = false;
        var skill = FirstGroupSkills.byId(id);
        if (page == 0 && skill != null && skill.role().equals(role)) {
            int rank = ClientDataHolder.get().skillLevels().getOrDefault(id, 0);
            name = text("screen.myriad.g1.selected", skill.displayName());
            label = rank >= skill.maxRank() ? text("screen.myriad.g1.maxed")
                    : text(rank == 0 ? "screen.myriad.g1.learn" : "screen.myriad.g1.rank_up");
            details = () -> skillLines(skill, rank, error(id));
            valid = true;
        } else if (page == 1 && id.startsWith("g1_attribute_")) {
            String key = id.substring(13);
            name = text("screen.myriad.g1.selected", text("screen.myriad.g1.attribute." + key));
            var data = ClientDataHolder.get();
            long price = FirstGroupGrowth.price(ClientDataHolder.syncedCounter(id));
            if (data.training() && data.noGoldCost()) label = text("screen.myriad.g1.buy", 0);
            else if (price == FirstGroupGrowth.PRICE_OVERFLOW) label = text("screen.myriad.g1.attribute_price_limit");
            else label = text("screen.myriad.g1.buy", price);
            details = () -> attributeLines(key);
            valid = true;
        }
        if (!valid) {
            scaledFit(graphics,text(page==0?"screen.myriad.g1.icon_hint":"screen.myriad.g1.attribute_hint"),
                    layout.viewport().x(),layout.footerDividerY()+8,layout.viewport().w(),MUTED,layout.footerScale());
            return;
        }
        var anchor = layout.selectedLabel();
        float footerScale=layout.footerScale();
        var wrapped=font.split(Component.literal(name),Math.max(1,(int)(anchor.w()/footerScale)));
        graphics.pose().pushMatrix();
        graphics.pose().translate(anchor.x(),anchor.y()+2);
        graphics.pose().scale(footerScale);
        for(int i=0;i<Math.min(2,wrapped.size());i++)graphics.text(font,wrapped.get(i),0,i*10,GOLD);
        graphics.pose().popMatrix();
        button(graphics, layout.primary(), pending() ? text("screen.myriad.g1.processing") : label,
                "confirm", valid && error(id) == null, false, true, mouseX, mouseY);
        if (valid) {
            offer(id + ":selected", anchor, details, mouseX, mouseY, false);
            offer(id + ":confirm", layout.primary(), details, mouseX, mouseY, false);
        }
    }
    private void drawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (nextTooltip == null) {
            clearTooltip();
            return;
        }
        if (tooltip == null || !tooltip.id.equals(nextTooltip.id)) tooltipScroll = 0;
        tooltip = nextTooltip;
        var font = Minecraft.getInstance().font;
        Box placementAnchor=tooltip.anchor;
        if(page==0&&tooltip.id.endsWith(":tile")) {
            var skill=FirstGroupSkills.byId(tooltip.id.substring(0,tooltip.id.length()-5));
            if(skill!=null)placementAnchor=layout().card(skill.index()|1);
        }
        int longest=0,totalWidth=0;
        for(String line:tooltip.lines) {
            int measured=font.width(line);
            longest=Math.max(longest,measured);
            totalWidth+=measured;
        }
        int balanced=(int)Math.ceil(Math.sqrt(totalWidth*font.lineHeight*1.7))+14;
        int preferredWidth=Math.clamp(Math.min(longest+14,balanced),48,220);
        int textWidth=Math.max(1,SkillChoiceTooltipLayout.width(width,placementAnchor,preferredWidth)-14);
        var wrapped = new ArrayList<TextLine>();
        for (int i = 0; i < tooltip.lines.size(); i++) {
            for (var line : font.split(Component.literal(tooltip.lines.get(i)), textWidth)) {
                wrapped.add(new TextLine(line, i == 0 ? GOLD : JADE));
            }
        }
        tooltipLayout = SkillChoiceTooltipLayout.create(width,height,placementAnchor,wrapped.size(),tooltipScroll,preferredWidth);
        tooltipScroll = tooltipLayout.scroll();
        var box = tooltipLayout.box();
        graphics.nextStratum();
        plate(graphics, box, GOLD, 0xFF081A21);
        graphics.nextStratum();
        graphics.enableScissor(box.x() + 2, box.y() + 2, box.right() - 2, box.bottom() - 2);
        int end = Math.min(wrapped.size(), tooltipScroll + tooltipLayout.visibleLines());
        for (int i = tooltipScroll; i < end; i++) {
            var line = wrapped.get(i);
            graphics.text(font, line.text, box.x() + 7, box.y() + 6 + (i - tooltipScroll) * 10, line.color);
        }
        if (tooltipLayout.maximum() > 0) {
            fit(graphics, text("screen.myriad.g1.tooltip_scroll", tooltipScroll + 1, end, wrapped.size()),
                    box.x() + 7, box.bottom() - 12, box.w() - 14, MUTED);
        }
        graphics.disableScissor();
    }
}
