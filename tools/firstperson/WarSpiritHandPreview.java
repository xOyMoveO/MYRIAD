package com.myriad.tools;

import com.myriad.client.firstperson.WarSpiritHandMotion;
import com.myriad.client.firstperson.WarSpiritHandRig;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.myriad.character.ability.WarSpiritHandCues.*;

/** Samples real runtime poses, weapon cuboids/atlas and vanilla wide sleeve meshes. */
public final class WarSpiritHandPreview {
    public static void main(String[] args)throws Exception {
        var assets=Path.of(args[0],"src/main/resources/assets/myriad");
        var out=Path.of(args[1]);Files.createDirectories(out);
        try(var zip=MinecraftPreviewResources.open()){
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
        }
        var main=NightDaggerPreview.arm(false,false);
        var off=NightDaggerPreview.arm(false,true);
        for(boolean war:new boolean[]{true,false}){
            String name=war?"chixiao":"yunsheng",item=war?"chixiao_blade":"yunsheng_bell";
            String[] labels=war?new String[]{"断岳斩","追魂踏","赤霄反锋","裂阵刀罡","破军无回","横刀镇岳","压斩","反撩","平扫"}:
                    new String[]{"纸鹤传音","定契索","回音界标","云梭护行","众生归宗","灵契同舟","前振","回铃","提铃"};
            NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/"+item+".png").toFile());
            var weapon=NightDaggerPreview.loadBlade(assets,item);
            int w=280,h=158;
            var board=new BufferedImage(w*6,h*5+42,BufferedImage.TYPE_INT_RGB);var g=board.createGraphics();
            g.setColor(new Color(15,22,27));g.fillRect(0,0,board.getWidth(),board.getHeight());
            g.setColor(Color.WHITE);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,20));
            g.drawString((war?"赤霄":"云笙")+"  ·  第一人称动作离线关键帧 / 非游戏截图",16,28);
            for(int i=0;i<labels.length;i++){
                var m=new WarSpiritHandMotion(war);int slot=i+2;double at=8;
                if(i>=6){for(int step=0;step<=i-6;step++)m.cue(0,BASIC,step*12,9,13);at=(i-6)*12;}
                else if(war&&i==1||!war&&i==3){m.cue(slot,MOVE,0,12,0);}
                else if(war&&i==2){m.cue(slot,HOLD,0,20,0);}
                else if(war&&i==4){m.cue(slot,MOVE,0,12,.62);}
                else if(!war&&i==4){m.cue(slot,HOLD,0,12,.78);}
                else if(!war&&i==2){m.cue(slot,AIM,0,12,0);}
                for(int frame=0;frame<3;frame++){
                    if(i<6&&frame==1){
                        int cue=(war&&i==1||war&&i==4||!war&&i==3)?FINISH:war&&i==2?FOLLOW:CAST;
                        m.cue(slot,cue,at,10,0);
                    }
                    double now=at+new double[]{0,1.8,3.65}[frame];
                    var shot=draw(war,m,now,weapon,main,off,labels[i]+"  "+new String[]{"起势","生效","收势"}[frame]);
                    g.drawImage(shot,((i%2)*3+frame)*w,42+(i/2)*h,w,h,null);
                }
            }
            g.dispose();ImageIO.write(board,"png",out.resolve(name+"-firstperson.png").toFile());
        }
    }

    private static BufferedImage draw(boolean war,WarSpiritHandMotion motion,double now,List<NightDaggerPreview.Quad> weapon,
                                      List<NightDaggerPreview.Quad> main,List<NightDaggerPreview.Quad> off,String label){
        int w=NightDaggerPreview.W,h=NightDaggerPreview.H;
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(30,49,68),0,h,new Color(71,86,75)));g.fillRect(0,0,w,h);
        g.setColor(new Color(207,216,211));g.fillRect(w/2-7,h/2-1,15,3);g.fillRect(w/2-1,h/2-7,3,15);
        g.dispose();var depth=new float[w*h];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        var p=motion.pose(now);var item=WarSpiritHandRig.item(motion,now,war,false,0);
        submit(image,depth,weapon,item);
        submit(image,depth,main,WarSpiritHandRig.arm(p,item,war,false,false,false,0));
        if(p.palm().y> -1.10)submit(image,depth,off,WarSpiritHandRig.arm(p,item,war,false,true,false,0));
        g=image.createGraphics();g.setColor(Color.WHITE);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,27));g.drawString(label,16,32);g.dispose();
        return image;
    }

    private record Vertex(Vector3f p,float u,float v) {}
    private static void submit(BufferedImage im,float[] depth,List<NightDaggerPreview.Quad> mesh,Matrix4f matrix){
        final float near=-.052f;
        var light=new Vector3f(.3f,.6f,1).normalize();
        for(var q:mesh){
            var face=new Vertex[4];
            for(int i=0;i<4;i++)face[i]=new Vertex(matrix.transformPosition(new Vector3f(q.p()[i])),q.uv()[i*2],q.uv()[i*2+1]);
            var clipped=new ArrayList<Vertex>(5);
            for(int i=0;i<4;i++){
                var a=face[i];var b=face[(i+1)%4];
                if(a.p.z<=near)clipped.add(a);
                if((a.p.z<=near)!=(b.p.z<=near)){
                    float t=(near-a.p.z)/(b.p.z-a.p.z);
                    clipped.add(new Vertex(new Vector3f(a.p).lerp(b.p,t),a.u+t*(b.u-a.u),a.v+t*(b.v-a.v)));
                }
            }
            var normal=new Vector3f(face[1].p).sub(face[0].p).cross(new Vector3f(face[2].p).sub(face[0].p)).normalize();
            float shade=.62f+.38f*Math.abs(normal.dot(light));
            for(int i=1;i+1<clipped.size();i++){
                var a=clipped.getFirst();var b=clipped.get(i);var c=clipped.get(i+1);
                NightDaggerPreview.triangle(im,depth,new Vector3f[]{a.p,b.p,c.p},new float[]{a.u,a.v,b.u,b.v,c.u,c.v},q.texture(),0,1,2,shade);
            }
        }
    }
}
