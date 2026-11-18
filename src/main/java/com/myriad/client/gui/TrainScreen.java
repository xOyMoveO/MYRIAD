package com.myriad.client.gui;

import com.myriad.client.ClientDataHolder;
import com.myriad.network.NetworkSetup;
import com.myriad.network.Packets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.myriad.client.gui.SkillChoiceLayout.Box;

/** Server-synchronised controls; labels left, glass actions/switches right. */
public final class TrainScreen extends Screen {
    private int scroll,level=1;
    private final List<Control> controls=new ArrayList<>();
    private record Control(GlassButton button,int row,int column,int columns,boolean requiresTraining){}
    private GlassButton levelLabel;
    private static final String[] ROW_KEYS={"enabled","apply_level","refresh","restore","nocooldown","nomana","nogold","level","gold","team","team"};
    public TrainScreen(){
        super(label("title"));
        if(ClientDataHolder.get()!=null)level=ClientDataHolder.get().level();
        Packets.sendToServer(new NetworkSetup.RequestSyncPayload());
    }
    @Override public boolean isPauseScreen(){return false;}
    private static Component label(String key){return Component.translatable("screen.myriad.train."+key);}
    private void send(String action,boolean value){Packets.sendToServer(new NetworkSetup.TrainActionPayload(action,value));}
    private GlassButton control(int row,int column,int columns,Component text,boolean needsTraining,BooleanSupplier state,Button.OnPress press){
        var b=addRenderableWidget(new GlassButton(text,state,press));
        controls.add(new Control(b,row,column,columns,needsTraining));return b;
    }
    private GlassButton action(int row,Component text,Button.OnPress press){return control(row,0,1,text,true,null,press);}
    private void toggle(int row,String key,boolean training,BooleanSupplier state){control(row,0,1,label(key),training,state,b->send(key,!state.getAsBoolean()));}
    @Override protected void init(){
        controls.clear();
        toggle(0,"enabled",false,ClientDataHolder::isTraining);
        control(1,0,4,Component.literal("−"),true,null,b->{level=Math.max(1,level-1);refresh();});
        levelLabel=control(1,1,4,Component.literal(""+level),true,null,b->{level=level==20?1:20;refresh();});
        control(1,2,4,Component.literal("+"),true,null,b->{level=Math.min(20,level+1);refresh();});
        control(1,3,4,label("apply"),true,null,b->send("level:"+level,true));
        levelLabel.setTooltip(Tooltip.create(label("level_hint")));
        action(2,label("execute"),b->send("refresh",true));
        action(3,label("execute"),b->send("restore",true));
        toggle(4,"nocooldown",true,ClientDataHolder::noCooldown);
        toggle(5,"nomana",true,ClientDataHolder::noMana);
        toggle(6,"nogold",true,ClientDataHolder::noGoldCost);
        action(7,Component.literal("+1"),b->send("level",true));
        action(8,Component.literal("+10000"),b->send("gold",true));
        action(9,label("faction_guardian"),b->send("faction_guardian",true)).textColor=com.myriad.game.Faction.GUARDIAN.argb();
        action(10,label("faction_destruction"),b->send("faction_destruction",true)).textColor=com.myriad.game.Faction.DESTRUCTION.argb();
        var p=layout().panel();
        var done=addRenderableWidget(new GlassButton(Component.translatable("gui.done"),null,b->onClose()));
        done.setPosition(p.right()-91,p.bottom()-27);done.setWidth(78);
        refresh();
    }
    private TrainLayout layout(){return TrainLayout.create(width,height,scroll);}
    private void refresh(){
        levelLabel.setMessage(Component.literal(""+level));
        var l=layout();scroll=l.scroll();
        for(var c:controls){
            var row=l.row(c.row());var a=l.actions(c.row());
            int w=c.button().state!=null?64:(a.w()-3*(c.columns()-1))/c.columns();
            int x=c.button().state!=null?a.right()-w:a.x()+c.column()*(w+3);
            c.button().setPosition(x,a.y());c.button().setWidth(w);
            c.button().visible=row.y()>=l.viewport().y()&&row.bottom()<=l.viewport().bottom();
            c.button().active=ClientDataHolder.modEnabled()&&(!c.requiresTraining()||ClientDataHolder.isTraining());
        }
    }
    @Override public void tick(){refresh();}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        var l=layout();if(!l.viewport().contains(x,y))return super.mouseScrolled(x,y,dx,dy);
        scroll=Math.clamp(scroll-(int)Math.round(dy*TrainLayout.STEP),0,l.maximum());refresh();return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        refresh();var l=layout();var p=l.panel();
        g.fill(0,0,width,height,0x31030A17);
        TrainingGlass.surface(g,p,12,false,true);
        g.text(font,title,p.x()+15,p.y()+12,TrainingGlass.TEXT);
        var data=ClientDataHolder.get();
        String status=ClientDataHolder.modEnabled()?Component.translatable("screen.myriad.train.current",data==null?1:data.level()).getString():label("require_mod").getString();
        g.text(font,font.plainSubstrByWidth(status,p.w()-30),p.x()+15,p.y()+29,TrainingGlass.ACCENT);
        for(int i=0;i<TrainLayout.ROWS;i++){
            var row=l.row(i);if(row.y()<l.viewport().y()||row.bottom()>l.viewport().bottom())continue;
            TrainingGlass.surface(g,row,7,row.contains(mx,my),false);
            var b=l.label(i);String full=label(ROW_KEYS[i]).getString();
            g.text(font,font.plainSubstrByWidth(full,b.w()),b.x(),b.y(),TrainingGlass.TEXT);
            if(b.contains(mx,my))g.setComponentTooltipForNextFrame(font,List.of(label(ROW_KEYS[i])),mx,my);
        }
        if(l.maximum()>0){
            int h=Math.max(12,l.viewport().h()*l.viewport().h()/(l.viewport().h()+l.maximum()));
            TrainingGlass.round(g,new Box(p.right()-7,l.viewport().y(),2,l.viewport().h()),1,0x40395D73);
            TrainingGlass.round(g,new Box(p.right()-7,l.viewport().y()+(l.viewport().h()-h)*scroll/l.maximum(),2,h),1,0xC0A1EAF7);
        }
        g.text(font,label("scroll"),p.x()+15,p.bottom()-19,TrainingGlass.MUTED);
        super.extractRenderState(g,mx,my,partial);
    }
    private final class GlassButton extends Button {
        final BooleanSupplier state;
        int textColor=TrainingGlass.TEXT;
        GlassButton(Component label,BooleanSupplier state,OnPress press){super(0,0,1,22,label,press,DEFAULT_NARRATION);this.state=state;}
        @Override public Component getMessage(){
            return state==null?super.getMessage():super.getMessage().copy().append(": ").append(Component.translatable(state.getAsBoolean()?"options.on":"options.off"));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float partial){
            var b=new Box(getX(),getY(),getWidth(),getHeight());
            TrainingGlass.surface(g,b,state==null?6:11,active&&isHoveredOrFocused(),false);
            if(state!=null){
                boolean on=state.getAsBoolean();
                TrainingGlass.round(g,new Box(b.right()-25,b.y()+3,22,16),8,!active?0xFF52606D:on?0xFF75DDF1:0xFF607C8E);
                TrainingGlass.round(g,new Box(b.right()-(on?15:23),b.y()+5,12,12),6,active?0xFFF2FCFF:0xFFB0BDCA);
                g.text(font,Component.translatable(on?"options.on":"options.off"),b.x()+7,b.y()+7,active?TrainingGlass.TEXT:TrainingGlass.MUTED);
            }else{
                String text=font.plainSubstrByWidth(getMessage().getString(),Math.max(1,b.w()-6));
                g.text(font,text,b.x()+(b.w()-font.width(text))/2,b.y()+7,active?textColor:TrainingGlass.MUTED);
            }
        }
    }
}
