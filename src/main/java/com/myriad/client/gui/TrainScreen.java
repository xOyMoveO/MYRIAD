package com.myriad.client.gui;

import com.myriad.client.ClientDataHolder;
import com.myriad.client.LobbyClient;
import net.minecraft.client.Minecraft;
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
    private final Screen parent;
    private int scroll,level=1;
    private boolean inside;
    private final List<Control> controls=new ArrayList<>();
    private record Control(GlassButton button,int row,int column,int columns,boolean requiresTraining){}
    private GlassButton levelLabel, travel;
    private static final String[] ROW_KEYS={"ground","apply_level","refresh","health","nocooldown","nomana","nogold","level","gold","team","team","mana","dummy","friendly_dummy","sparring","choose","remove_dummy"};
    public TrainScreen(){this(null);}
    public TrainScreen(Screen parent){
        super(label("title"));
        this.parent=parent;
        inside=inGround();
        LobbyClient.send("refresh","");
        if(ClientDataHolder.get()!=null)level=ClientDataHolder.get().level();
        Packets.sendToServer(new NetworkSetup.RequestSyncPayload());
    }
    private boolean inGround(){var s=LobbyClient.snapshot();return s!=null && s.training();}
    @Override public void onClose(){Minecraft.getInstance().setScreenAndShow(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private static Component label(String key){return Component.translatable("screen.myriad.train."+key);}
    private void send(String action,boolean value){Packets.sendToServer(new NetworkSetup.TrainActionPayload(action,value));}
    private GlassButton control(int row,int column,int columns,Component text,boolean needsTraining,BooleanSupplier state,Button.OnPress press){
        var b=addRenderableWidget(new GlassButton(text,state,press));
        controls.add(new Control(b,row,column,columns,needsTraining));return b;
    }
    private GlassButton action(int row,Component text,Button.OnPress press){return control(row,0,1,text,true,null,press);}
    private GlassButton toggle(int row,String key,boolean training,BooleanSupplier state){
        return control(row,0,1,label(key),training,state,b->send(key,!state.getAsBoolean()));
    }
    @Override protected void init(){
        controls.clear();
        travel=control(0,0,1,label("enter"),false,null,b->LobbyClient.send(inGround()?"return":"train",""));
        control(1,0,4,Component.literal("−"),true,null,b->{level=Math.max(1,level-1);refresh();});
        levelLabel=control(1,1,4,Component.literal(""+level),true,null,b->{level=level==20?1:20;refresh();});
        control(1,2,4,Component.literal("+"),true,null,b->{level=Math.min(20,level+1);refresh();});
        control(1,3,4,label("apply"),true,null,b->send("level:"+level,true));
        levelLabel.setTooltip(Tooltip.create(label("level_hint")));
        action(2,label("execute"),b->send("refresh",true));
        action(3,label("execute"),b->send("health",true));
        toggle(4,"nocooldown",true,ClientDataHolder::noCooldown);
        toggle(5,"nomana",true,ClientDataHolder::noMana);
        toggle(6,"nogold",true,ClientDataHolder::noGoldCost);
        action(7,Component.literal("+1"),b->send("level",true));
        action(8,Component.literal("+10000"),b->send("gold",true));
        action(9,label("faction_guardian"),b->send("faction_guardian",true)).textColor=com.myriad.game.Faction.GUARDIAN.argb();
        action(10,label("faction_destruction"),b->send("faction_destruction",true)).textColor=com.myriad.game.Faction.DESTRUCTION.argb();
        action(11,label("execute"),b->send("mana",true));
        action(12,label("spawn"),b->send("dummy",true));
        action(13,label("spawn"),b->send("friendly_dummy",true));
        toggle(14,"sparring",true,()->LobbyClient.snapshot()!=null && LobbyClient.snapshot().sparring())
                .setTooltip(Tooltip.create(label("sparring_hint")));
        action(15,label("choose"),b->Minecraft.getInstance().setScreenAndShow(new CharacterSelectScreen(this,false)));
        action(16,label("remove_dummy"),b->send("remove_dummy",true));
        var p=layout().panel();
        var done=addRenderableWidget(new GlassButton(Component.translatable("gui.done"),null,b->onClose()));
        done.setPosition(p.right()-91,p.bottom()-27);done.setWidth(78);
        refresh();
    }
    private TrainLayout layout(){return TrainLayout.create(width,height,scroll);}
    private void refresh(){
        levelLabel.setMessage(Component.literal(""+level));
        boolean ground = inGround();
        travel.setMessage(label(ground ? "leave" : "enter"));
        // An operator who explicitly enabled the legacy sandbox can still use its existing tools.
        var player = Minecraft.getInstance().player;
        boolean sandbox = !ground && ClientDataHolder.isTraining() && player != null
                && player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR);
        var l=layout();scroll=l.scroll();
        for(var c:controls){
            var row=l.row(c.row());var a=l.actions(c.row());
            int w=c.button().state!=null?Math.min(64,a.w()):(a.w()-3*(c.columns()-1))/c.columns();
            int x=c.button().state!=null?a.right()-w:a.x()+c.column()*(w+3);
            c.button().setPosition(x,a.y());c.button().setWidth(w);
            c.button().visible=row.y()>=l.viewport().y()&&row.bottom()<=l.viewport().bottom();
            var lobby=LobbyClient.snapshot();
            c.button().active=lobby!=null && !lobby.participant() && ClientDataHolder.modEnabled()
                    && (!c.requiresTraining() || ground || sandbox && c.row()!=14);
        }
    }
    @Override public void tick(){
        boolean now=inGround();
        if(inside && !now){LobbyClient.openLobby();return;}
        inside=now;
        refresh();
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int step = Math.max(1,layout().viewport().h()/TrainLayout.STEP)*TrainLayout.STEP;
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) {
            scroll=Math.max(0,scroll-step); refresh(); return true;
        }
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN) {
            scroll=Math.min(layout().maximum(),scroll+step); refresh(); return true;
        }
        return super.keyPressed(event);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        var l=layout();if(!l.viewport().contains(x,y))return super.mouseScrolled(x,y,dx,dy);
        scroll=Math.clamp(scroll-(int)Math.round(dy*TrainLayout.STEP),0,l.maximum());refresh();return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        refresh();var l=layout();var p=l.panel();
        g.fill(0,0,width,height,0x31030A17);
        TrainingGlass.surface(g,p,12,false,true);
        g.text(font,title,p.x()+15,p.y()+(p.h()>=144?12:6),TrainingGlass.TEXT);
        var data=ClientDataHolder.get();
        String status=!ClientDataHolder.modEnabled()?label("require_mod").getString()
                :!inGround()?label("enter_hint").getString()
                :Component.translatable("screen.myriad.train.current",data==null?1:data.level()).getString();
        if (l.viewport().y()-p.y()>22)
            g.text(font,font.plainSubstrByWidth(status,p.w()-30),p.x()+15,p.y()+(p.h()>=144?29:21),TrainingGlass.ACCENT);
        for(int i=0;i<ROW_KEYS.length;i++){
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
        g.text(font,font.plainSubstrByWidth(label("scroll").getString(),Math.max(0,p.w()-110)),
                p.x()+15,p.bottom()-19,TrainingGlass.MUTED);
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
