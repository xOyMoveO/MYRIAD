package com.myriad.tools;

import com.myriad.client.firstperson.*;
import com.myriad.network.ThunderSkillViewPayload;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual V2 sword, skin meshes, HD UVs and shipping matrices; not a game capture. */
public final class ThunderSkillPreview {
    private static final int W=640,H=360;
    private static final String[] NAMES={"闪雷 · 双手倒剑插地","雷电劫 · 左手蓄雷前推","天雷护体 · 双手合诀","斩雷 · 左上蓄势斩击","九天神雷 · 左手指向选点","雷霆裁决 · 拂刃引雷"};
    private static final int[] READY={0,1,3,4,10,6},FIRE={-1,2,-1,5,8,7},TICKS={14,6,14,10,20,12};
    private static ThunderSkillMotion motion(int action,int ticks){var m=new ThunderSkillMotion();m.accept(new ThunderSkillViewPayload(action,ticks,100,1),100,0,ThunderSwordMotion.IDLE);return m;}
    private static BufferedImage draw(ThunderSkillMotion.Frame f,double time,boolean slim,boolean left,String title){
        var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(29,44,61),0,H,new Color(69,91,85)));g.fillRect(0,0,W,H);
        g.setColor(new Color(190,201,194,100));g.drawLine(W/2-5,H/2,W/2+5,H/2);g.drawLine(W/2,H/2-5,W/2,H/2+5);g.dispose();
        float[] depth=new float[W*H];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        var main=ThunderSkillRig.main(f,left);var sword=ThunderSkillRig.sword(f,left);
        NightDaggerPreview.submit(image,depth,NightDaggerPreview.arm(slim,left),new Matrix4f(main).mul(ThunderSwordRig.arm(slim,left)));
        if(f.showOff())NightDaggerPreview.submit(image,depth,NightDaggerPreview.arm(slim,!left),ThunderSkillRig.off(f,left).mul(ThunderSwordRig.arm(slim,!left)));
        if(f.showBlade())NightDaggerPreview.submit(image,depth,NightDaggerPreview.blade,sword);
        if(f.showBlade())NightSkillPreview.vfx(image,depth,ThunderSkillHandVfx.blade(f,time),sword);
        NightSkillPreview.vfx(image,depth,ThunderSkillHandVfx.camera(f,time,left),new Matrix4f());
        g=image.createGraphics();g.setColor(new Color(12,20,28,220));g.fillRect(0,0,W,48);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(225,238,240));g.drawString(title,12,20);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.setColor(new Color(170,190,190));g.drawString("真实网格/皮肤/UV与动作矩阵 · 离线预览，非Minecraft截图",12,39);g.dispose();return image;
    }
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(args[0]).resolve("src/main/resources/assets/myriad"),out=Path.of(args[1]);Files.createDirectories(out);
        NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/tianxing_thunder_longsword.png").toFile());NightDaggerPreview.blade=NightDaggerPreview.loadBlade(assets,"leitingjian");
        try(var zip=new ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
        }
        String[] names={"slash","bolt","impact","seal","corona","pillar"};
        for(int i=0;i<6;i++)NightSkillPreview.TEXTURES.put(57+i,ImageIO.read(assets.resolve("textures/particle/vfx/thunder_pixel_"+names[i]+".png").toFile()));
        var sheet=new BufferedImage(W*3,H*6,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();int checked=0;
        for(int n=0;n<6;n++){
            var m=n==4?new ThunderSkillMotion():motion(READY[n],TICKS[n]);var frames=new ArrayList<BufferedImage>();
            for(double t=0;t<TICKS[n];t+=.7){var f=n==4?ThunderSkillMotion.aim(t):m.sample(100+t);frames.add(draw(f,100+t,false,false,NAMES[n]+" · 起势"));}
            double[] keyTimes={.30,.65,.90};
            for(int k=0;k<3;k++){double t=TICKS[n]*keyTimes[k];g.drawImage(draw(n==4?ThunderSkillMotion.aim(t):m.sample(100+t),100+t,false,false,NAMES[n]),k*W,n*H,null);}
            if(FIRE[n]>=0){
                m.accept(new ThunderSkillViewPayload(FIRE[n],10,100+TICKS[n],1),100+TICKS[n],0,ThunderSwordMotion.IDLE);
                for(double t=0;t<14;t+=.7)frames.add(draw(m.sample(100+TICKS[n]+t),100+TICKS[n]+t,false,false,NAMES[n]+" · 释放"));
            }else for(double t=TICKS[n];t<TICKS[n]+4;t+=.7)frames.add(draw(m.sample(100+t),100+t,false,false,NAMES[n]));
            NightDaggerPreview.gif(frames,out.resolve("thunder_skill_"+n+".gif"));
        }
        g.dispose();ImageIO.write(sheet,"png",out.resolve("thunder_skill_pose_sheet.png").toFile());
        for(int action=0;action<=8;action++)for(boolean left:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true}){
            var m=motion(action,14);
            for(int i=0;i<100;i++){
                var f=m.sample(100+i*.14);if(!f.showBlade())continue;var sword=ThunderSkillRig.sword(f,left);
                for(var q:NightDaggerPreview.blade)for(var p:q.p()){
                    var at=sword.transformPosition(new Vector3f(p));
                    NightDaggerPreview.require(at.isFinite()&&at.z<-.051,"Skill sword near plane: "+action+" / "+i+" / "+at);
                }checked++;
            }
        }
        System.out.println("[MYRIAD-THUNDER-SKILL-PREVIEW] PASS: six linked gesture GIFs, actual V2 mesh/HD UVs/skin matrices, "+checked+" finite/clipping poses; offline only");
    }
}
