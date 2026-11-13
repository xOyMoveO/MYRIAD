package com.myriad.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/** Artillery readiness and the independently limited muzzle; the camera is never constrained. */
public final class YikeHud {
    private YikeHud(){}
    public static void draw(GuiGraphicsExtractor gui,Font font){
        var mc=Minecraft.getInstance();if(mc.player==null||!"yike".equals(ClientDataHolder.characterId()))return;
        var s=YikeClientState.get(mc.player.getId());if(s==null)return;
        int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight(),x=width/2+80,y=height-88;
        String title=s.mode()==1?"定轨炮台":s.mode()==2?"游走阵列":s.mode()==3?"贯虹阵列":"虹列 · 携行";
        if(s.mode()>0)title+="  "+String.format(java.util.Locale.ROOT,"%.1fs",s.remaining()/20f);
        gui.text(font,title,x,y,0xFF5DDEFF);
        String status;
        if(s.windup()>=0)status=s.windup()<4?"待出膛 · 结束时验蓝":"展开／蓄势中";
        else if(s.mode()==3)status="引导 "+s.jumps()+" / 6 · 再按终极取消";
        else if(s.mode()>0){
            boolean mana=ClientDataHolder.get().mana()>=35;
            status=!mana?"蓝量不足":s.nextShot()>0?String.format(java.util.Locale.ROOT,"间隔 %.2fs",s.nextShot()/20f):"Q 可发射 · 每炮35蓝";
        }else status="左键物理普攻 · Q出膛付费";
        gui.text(font,status,x,y+12,s.windup()>=0?0xFFF1FCFF:0xFF5DDEFF);
        if(s.mode()==1||s.mode()==2){int total=s.mode()==1?5:4;
            for(int i=0;i<total;i++){int bx=x+i*15;gui.fill(bx,y+25,bx+10,y+31,i<s.shells()?0xFF5DDEFF:0xFF15222E);gui.fill(bx,y+25,bx+10,y+26,0xFF5DDEFF);}
            gui.text(font,s.shells()+" / "+total+" · 再按主要键收拢",x,y+37,0xFFF1FCFF);
        }
        var passive=com.myriad.character.rework.FirstGroupSkills.selected("yike",0,ClientDataHolder.get().skillLevels());
        if(passive!=null&&passive.index()==1&&ClientDataHolder.get().skillLevels().getOrDefault(passive.getId(),0)>0){
            String label=s.barrierCooldown()>0?String.format(java.util.Locale.ROOT,"稳态屏障 %.1fs",s.barrierCooldown()/20f):s.steady()?"稳态已就绪":"站稳1.2秒可得屏障";
            gui.text(font,label,x,y-13,s.steady()?0xFFF1FCFF:0xFFA88AFF);
        }
        if(s.mode()>0){
            Vec3 forward=mc.player.getLookAngle(),right=forward.cross(new Vec3(0,1,0)).normalize(),up=right.cross(forward).normalize();
            double z=s.aim().dot(forward),scale=height/(2*Math.tan(Math.toRadians(mc.options.fov().get()*.5)));
            int rx=(int)(width*.5+s.aim().dot(right)/Math.max(.05,z)*scale),ry=(int)(height*.5-s.aim().dot(up)/Math.max(.05,z)*scale);
            rx=Math.clamp(rx,14,width-14);ry=Math.clamp(ry,14,height-14);
            int color=z<.99?0xFFFF6AAE:0xFF5DDEFF;
            gui.fill(rx-5,ry-5,rx-2,ry-4,color);gui.fill(rx+2,ry-5,rx+5,ry-4,color);
            gui.fill(rx-5,ry+4,rx-2,ry+5,color);gui.fill(rx+2,ry+4,rx+5,ry+5,color);
            if(z<.95)gui.text(font,"炮口转向中",rx-25,ry+9,color);
        }
    }
}
