package com.myriad.client.gui;

import com.myriad.MyriadClient;
import com.myriad.client.TeamSignalClient;
import com.myriad.game.TeamSignalKind;
import com.myriad.network.TeamSignalRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Hold the bound key, aim a sector, release to send; center/Escape/right click cancel. */
public final class TeamSignalWheel extends Screen {
    private final Vec3 point; private int selected=-1;private boolean closed;
    private net.minecraft.client.multiplayer.ClientLevel world;
    public TeamSignalWheel(Vec3 point){super(Component.translatable("signal.myriad.title"));this.point=point;}
    public static void open(){var mc=Minecraft.getInstance();if(mc.gui.screen()!=null||!TeamSignalClient.available())return;
        var camera=mc.getCameraEntity();if(camera==null)return;
        Vec3 origin=camera.getEyePosition();Vec3 end=com.myriad.game.TeamSignalTargeting.end(origin,camera.getLookAngle());
        var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(origin,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,camera));
        Vec3 at=com.myriad.game.TeamSignalTargeting.select(origin,camera.getLookAngle(),hit,mc.hitResult);
        mc.setScreenAndShow(new TeamSignalWheel(at));
    }
    @Override protected void init(){world=Minecraft.getInstance().level;}
    public static int sector(double dx,double dy){return com.myriad.game.SignalWheelGeometry.sector(dx,dy);}
    private int radius(){return Math.max(40,Math.min(82,Math.min(width/2-40,height/2-32)));}
    private int selectAt(double x,double y){return com.myriad.game.SignalWheelGeometry.sector(x-width*.5,y-height*.5,radius()/2.0);}
    private void finish(){if(closed)return;closed=true;
        var mc=Minecraft.getInstance();
        if(selected>=0&&world==mc.level&&mc.isWindowActive()&&TeamSignalClient.available())
        com.myriad.network.Packets.sendToServer(new TeamSignalRequest(selected,point));onClose();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){if(!TeamSignalClient.available()||!Minecraft.getInstance().isWindowActive()
            ||world!=Minecraft.getInstance().level)onClose();}
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(MyriadClient.KEY_SIGNAL.matchesMouse(event)){selected=selectAt(event.x(),event.y());finish();return true;}
        return super.mouseReleased(event);}
    @Override public boolean keyReleased(KeyEvent event){if(MyriadClient.KEY_SIGNAL.matches(event)){
        var mc=Minecraft.getInstance();var window=mc.getWindow();
        selected=selectAt(mc.mouseHandler.getScaledXPos(window),mc.mouseHandler.getScaledYPos(window));
        finish();return true;}return super.keyReleased(event);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){if(event.buttonInfo().button()==1){onClose();return true;}return true;}
    @Override public boolean keyPressed(KeyEvent event){if(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE){onClose();return true;}return true;}
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int mouseX,int mouseY,float partial){
        selected=selectAt(mouseX,mouseY);int cx=width/2,cy=height/2;
        int outer=radius(),inner=outer/2;
        for(int dy=-outer;dy<=outer;dy+=2){int a=(int)Math.sqrt(Math.max(0,outer*outer-dy*dy));
            int b=Math.abs(dy)<inner?(int)Math.sqrt(inner*inner-dy*dy):0;
            gui.fill(cx-a,cy+dy,cx-b,cy+dy+2,0xA91A2635);gui.fill(cx+b,cy+dy,cx+a,cy+dy+2,0xA91A2635);}
        var font=Minecraft.getInstance().font;
        for(int i=0;i<7;i++){double angle=i*Math.PI*2/7-Math.PI/2;int color=TeamSignalKind.values()[i].color;
            if(i==selected)for(double a=angle-Math.PI/7+.03;a<angle+Math.PI/7-.03;a+=.055)
                for(int r=inner+2;r<outer;r+=4){int x=cx+(int)(Math.cos(a)*r),y=cy+(int)(Math.sin(a)*r);gui.fill(x-2,y-2,x+2,y+2,0x806D849A);}
            int x=cx+(int)(Math.cos(angle)*(inner+outer)*.5),y=cy+(int)(Math.sin(angle)*(inner+outer)*.5);
            TeamSignalClient.glyph(gui,x,y-5,color,i==selected?6:4,i);
            String label=Component.translatable(TeamSignalKind.values()[i].key()+".short").getString();
            gui.text(font,label,x-font.width(label)/2,y+5,color);}
        String center=Component.translatable(selected<0?"signal.myriad.cancel":TeamSignalKind.values()[selected].key()+".short").getString();
        gui.text(font,center,cx-font.width(center)/2,cy-4,0xFFFFFFFF);
        int hintY=cy+outer+8;
        for(var line:font.split(Component.translatable("signal.myriad.hold_hint"),Math.max(80,width-12))){
            gui.text(font,line,cx-font.width(line)/2,hintY,0xFFE3EDF5);hintY+=10;}
    }
}
