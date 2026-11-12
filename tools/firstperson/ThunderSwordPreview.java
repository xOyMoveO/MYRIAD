package com.myriad.tools;

import com.myriad.client.firstperson.ThunderSwordRig;
import com.myriad.client.firstperson.ThunderSwordMotion;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual item/skin geometry, UV and renderer matrices; offline perspective proof, not client capture. */
public final class ThunderSwordPreview {
    private static BufferedImage draw(ThunderSwordMotion.Frame frame,boolean slim,boolean left,String title)throws Exception{
        var pose=frame.pose();
        int w=NightDaggerPreview.W,h=NightDaggerPreview.H;
        var im=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(29,44,61),0,h,new Color(69,91,85)));g.fillRect(0,0,w,h);
        g.setColor(new Color(190,201,194,100));g.drawLine(w/2-5,h/2,w/2+5,h/2);g.drawLine(w/2,h/2-5,w/2,h/2+5);g.dispose();
        float[] depth=new float[w*h];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        var wrist=ThunderSwordRig.wrist(pose,0,left);
        NightDaggerPreview.submit(im,depth,NightDaggerPreview.arm(slim,left),new Matrix4f(wrist).mul(ThunderSwordRig.arm(slim,left)));
        var blade=new Matrix4f(wrist).mul(ThunderSwordRig.blade(frame,left));
        for(var q:NightDaggerPreview.blade)for(var v:q.p())
            NightDaggerPreview.require(blade.transformPosition(new Vector3f(v)).z<-.05,"Actual sword never crosses the camera near plane");
        NightDaggerPreview.submit(im,depth,NightDaggerPreview.blade,blade);
        if(pose.equals(ThunderSwordMotion.IDLE)){int centre=0,total=0;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(Float.isFinite(depth[y*w+x])){
                total++;if(x>=w*.4&&x<w*.6&&y>=h*.3&&y<h*.7)centre++;
            }
            NightDaggerPreview.require(total>1500,"Both actual hand and weapon are visible");
            NightDaggerPreview.require(centre<100,"Idle leaves the central aiming window open: "+centre);
        }
        g=im.createGraphics();g.setColor(new Color(10,19,30,220));g.fillRect(0,0,w,47);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(228,220,182));g.drawString(title,12,20);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.setColor(new Color(170,190,190));g.drawString("离线实际网格投影 · 非游戏截图 · Steve示例皮肤 · 仅第一人称表现",12,39);g.dispose();return im;
    }
    public static void main(String[] args)throws Exception{
        Path project=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        Path assets=project.resolve("src/main/resources/assets/myriad");
        NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/tianxing_thunder_longsword.png").toFile());
        NightDaggerPreview.blade=NightDaggerPreview.loadBlade(assets,"leitingjian");
        try(var zip=new ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
        }
        var frames=new ArrayList<BufferedImage>();
        var idle=new ThunderSwordMotion().frame(0);
        var idleSheet=new BufferedImage(1280,360,BufferedImage.TYPE_INT_RGB);var ig=idleSheet.createGraphics();
        ig.drawImage(draw(idle,false,false,"天刑 · 刃口向前的待机握持"),0,0,null);
        ig.drawImage(draw(idle,true,true,"左惯用手 / 细臂 · 镜像握持"),640,0,null);ig.dispose();
        ImageIO.write(idleSheet,"png",out.resolve("thunder_sword_idle.png").toFile());
        String[] names={"左上 → 右下斩击","右上 → 左下斩击","中间横斩"};
        var motion=new ThunderSwordMotion();var sheet=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        for(int cut=0;cut<3;cut++){
            double start=cut*20;motion.attack(start,20);
            for(int i=0;i<3;i++)g.drawImage(draw(motion.frame(start+motion.duration()*new double[]{.22,.44,.62}[i]),false,false,
                    "第"+(cut+1)+"击 · "+names[cut]+" · "+new String[]{"起势","斩击中","随势"}[i]),i*640,cut*360,null);
            for(int i=0;i<25;i++)frames.add(draw(motion.frame(start+i*.8),false,false,"第"+(cut+1)+"击 · "+names[cut]));
        }
        g.dispose();ImageIO.write(sheet,"png",out.resolve("thunder_sword_firstperson.png").toFile());
        NightDaggerPreview.gif(frames,out.resolve("thunder_sword_firstperson.gif"));
        System.out.println("[MYRIAD-THUNDER-VIEW-PREVIEW] PASS: actual 169-cube V2 item + skin arms/UVs, forward-edge idle and three cuts, central idle view clear, blade near-plane safe; offline only");
    }
}
