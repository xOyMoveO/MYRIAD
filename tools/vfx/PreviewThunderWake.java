package com.myriad.tools;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.tianxing.rework.ThunderVfxGeometry;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;

/** Current actual mesh and accumulated received-segment wakes, at 50/80 blocks/s.
 * Offline orthographic projection, not a Minecraft screenshot or particle-engine acceptance. */
public final class PreviewThunderWake {
    private static final int W=1060,H=310;
    private static final Vec3 CAMERA=new Vec3(.3128,.248,.8648).normalize();
    private static double[] screen(Vec3 p){return new double[]{(.94*p.x-.34*p.z)*45,(-.10*p.x-.92*p.y+.30*p.z)*45};}
    private static List<Quad> mesh(int variant,double now){
        var out=new ArrayList<Quad>();double range=variant==5?20:18,speed=variant==5?4:2.5;
        int steps=(int)Math.ceil(range/speed);
        for(int i=0;i<steps;i++){
            long born=100+i+1;double age=now-i-1;
            if(age<0)continue;
            var from=new Vec3(Math.min(range,i*speed),1.1,0);var to=new Vec3(Math.min(range,(i+1)*speed),1.1,0);
            var body=new AbilityVfxPayload(1,-1,1,variant,AbilityVfxGeometry.FLIGHT,from,to,.22f,0,3,born,false,7);
            var wake=ThunderVfxGeometry.wakeForFlight(body);
            if(age<wake.life())out.addAll(AbilityVfxGeometry.build(wake,(float)age,false));
            // New received FLIGHT replaces the old one; final CANCEL kills the body only.
            if(age<1&&now<steps)out.addAll(AbilityVfxGeometry.build(body,(float)age,false));
        }
        out.removeIf(q->q.b().subtract(q.a()).cross(q.c().subtract(q.a())).dot(CAMERA)<=1E-12);
        out.sort(Comparator.comparingDouble(q->q.a().add(q.b()).add(q.c()).add(q.d()).dot(CAMERA)));return out;
    }
    private static BufferedImage board(double time,boolean dark,boolean still){
        int cols=still?4:1;
        var image=new BufferedImage(W*cols,H*2+40,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(18,24,29));g.fillRect(0,0,image.getWidth(),image.getHeight());
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,17));g.setColor(Color.WHITE);
        g.drawString("天刑高速弹体尾流 · 实际网格/UV与逐段寿命离线投影，非游戏截图",16,26);
        for(int row=0;row<2;row++)for(int column=0;column<cols;column++){
            int variant=row==0?3:5,x=column*W,y=40+row*H;
            double now=still?new double[]{1.6,3.6,6.6,14.6}[column]:time;
            var cell=(Graphics2D)g.create();cell.clipRect(x+1,y+1,W-2,H-2);
            cell.setColor(dark?new Color(14,23,40):new Color(130,155,117));cell.fillRect(x+1,y+1,W-2,H-2);
            cell.setColor(new Color(255,255,255,34));
            for(int i=0;i<=20;i++){
                double[] a=screen(new Vec3(i,0,-2)),b=screen(new Vec3(i,0,2));
                cell.drawLine(x+50+(int)a[0],y+(int)(H*.62+a[1]),x+50+(int)b[0],y+(int)(H*.62+b[1]));
            }
            for(var q:mesh(variant,now)){
                var points=new Vec3[]{q.a(),q.b(),q.c(),q.d()};var xy=new double[4][];
                for(int i=0;i<4;i++){xy[i]=screen(points[i]);xy[i][0]+=x+50;xy[i][1]+=y+H*.62;}
                var texture=PreviewThunderPixelVfx.tint(q.material(),q.color());
                double u0=q.startU()*texture.getWidth(),u1=q.endU()*texture.getWidth(),v0=q.startV()*texture.getHeight(),v1=q.endV()*texture.getHeight();
                if(q.reverseV()){double swap=v0;v0=v1;v1=swap;}
                double[][] uv={{u0,v1},{u1,v1},{u1,v0},{u0,v0}};
                cell.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,q.alpha()));
                PreviewThunderPixelVfx.triangle(cell,texture,new double[][]{xy[0],xy[1],xy[2]},new double[][]{uv[0],uv[1],uv[2]});
                PreviewThunderPixelVfx.triangle(cell,texture,new double[][]{xy[0],xy[2],xy[3]},new double[][]{uv[0],uv[2],uv[3]});
            }
            cell.setComposite(AlphaComposite.SrcOver);cell.setColor(Color.WHITE);cell.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));
            cell.drawString((row==0?"雷电劫 · 50格/秒 · 0.30秒尾流":"斩雷 · 80格/秒 · 0.35秒尾流")+String.format(Locale.ROOT," | 出手后 %.2f秒",now/20),x+14,y+25);
            cell.dispose();
        }
        g.dispose();return image;
    }
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        String[] names={"slash","bolt","impact","seal","corona","pillar"};
        for(int i=0;i<6;i++)PreviewThunderPixelVfx.textures.put(57+i,ImageIO.read(assets.resolve("thunder_pixel_"+names[i]+".png").toFile()));
        PreviewThunderPixelVfx.textures.put(63,ImageIO.read(assets.resolve("thunder_projectile_models.png").toFile()));
        ImageIO.write(board(0,false,true),"png",out.resolve("wake_stages_grass.png").toFile());
        ImageIO.write(board(0,true,true),"png",out.resolve("wake_stages_dark.png").toFile());
        ImageIO.write(board(3.6,false,false),"png",out.resolve("wake_midflight_grass.png").toFile());
        ImageIO.write(board(3.6,true,false),"png",out.resolve("wake_midflight_dark.png").toFile());
        var writer=ImageIO.getImageWritersByFormatName("gif").next();
        try(var stream=ImageIO.createImageOutputStream(out.resolve("wake_flight_preview.gif").toFile())){
            writer.setOutput(stream);writer.prepareWriteSequence(null);
            for(int i=0;i<40;i++){
                var frame=board(i*.5,false,false);
                var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame),null);
                String format=metadata.getNativeMetadataFormatName();var tree=(IIOMetadataNode)metadata.getAsTree(format);
                var control=(IIOMetadataNode)tree.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("delayTime",i%2==0?"3":"2");control.setAttribute("disposalMethod","none");
                if(i==0){var apps=new IIOMetadataNode("ApplicationExtensions");var app=new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID","NETSCAPE");app.setAttribute("authenticationCode","2.0");app.setUserObject(new byte[]{1,0,0});apps.appendChild(app);tree.appendChild(apps);}
                metadata.setFromTree(format,tree);writer.writeToSequence(new IIOImage(frame,null,metadata),null);
            }
            writer.endWriteSequence();
        }finally{writer.dispose();}
        System.out.println("[MYRIAD-THUNDER-WAKE-PREVIEW] PASS: actual current meshes, authoritative clipped samples, accumulated finite wakes and body-end; grass/dark stages and real-speed timeline; offline only");
    }
}
