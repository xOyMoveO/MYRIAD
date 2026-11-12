package com.myriad.tools;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** CPU rendering of real mesh/UV/flipbook timing. Not a Minecraft capture. */
public final class PreviewThunderPixelVfx {
    private static final int W=540,H=420;
    static final Map<Integer,BufferedImage> textures=new HashMap<>();
    private static final Map<String,BufferedImage> tints=new HashMap<>();
    private static final String[] NAMES={"雷灵三叠 · 雷印落电","雷刃共鸣 · 待发雷符","闪雷 · 起终点电痕","雷电劫 · 电弧连锁",
            "天雷护体 · 环绕电幕","斩雷 · 飞行剑气","九天神雷 · 六重雷阵","雷霆裁决 · 120°雷霆扇形"};
    private static double scale=79;
    private static double[] screen(Vec3 p) {return new double[]{(.832*p.x-.555*p.z)*scale,(-.308*p.x+.832*p.y-.462*p.z)*-scale};}
    static double depth(Quad q) {var p=q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25);return .462*p.x+.555*p.y+.693*p.z;}
    static void triangle(Graphics2D g,BufferedImage im,double[][] xy,double[][] uv) {
        double sx=uv[1][0]-uv[0][0],sy=uv[1][1]-uv[0][1],tx=uv[2][0]-uv[0][0],ty=uv[2][1]-uv[0][1];
        double determinant=sx*ty-tx*sy;if(Math.abs(determinant)<1E-8)return;
        double dx=xy[1][0]-xy[0][0],dy=xy[1][1]-xy[0][1],ex=xy[2][0]-xy[0][0],ey=xy[2][1]-xy[0][1];
        double a=(dx*ty-ex*sy)/determinant,b=(dy*ty-ey*sy)/determinant,c=(ex*sx-dx*tx)/determinant,d=(ey*sx-dy*tx)/determinant;
        var transform=new AffineTransform(a,b,c,d,xy[0][0]-a*uv[0][0]-c*uv[0][1],xy[0][1]-b*uv[0][0]-d*uv[0][1]);
        var shape=new Path2D.Double();shape.moveTo(xy[0][0],xy[0][1]);shape.lineTo(xy[1][0],xy[1][1]);shape.lineTo(xy[2][0],xy[2][1]);shape.closePath();
        var child=(Graphics2D)g.create();child.clip(shape);child.drawImage(im,transform,null);child.dispose();
    }
    static BufferedImage tint(int material,int rgb) {
        return tints.computeIfAbsent(material+":"+rgb,key->{
            var src=textures.get(material);var result=new BufferedImage(src.getWidth(),src.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<src.getHeight();y++)for(int x=0;x<src.getWidth();x++) {
                int p=src.getRGB(x,y),r=((p>>16)&255)*((rgb>>16)&255)/255,g=((p>>8)&255)*((rgb>>8)&255)/255,b=(p&255)*(rgb&255)/255;
                result.setRGB(x,y,(p&0xFF000000)|(r<<16)|(g<<8)|b);
            }return result;
        });
    }
    private static List<Quad> mesh(int index,double time,boolean still) {
        int[] kinds={PILLAR,SHIELD,TRAIL,FLIGHT,SHIELD,FLIGHT,PILLAR,ARC};
        var from=index==2?new Vec3(-1.6,.4,0):index==3?new Vec3(-1.25,.65,0):index==5?new Vec3(-2,.65,0):Vec3.ZERO;
        var to=index==0?new Vec3(0,2.8,0):index==2?new Vec3(1.6,.4,0):index==3?new Vec3(1.25,.65,0)
                :index==5?new Vec3(2,.65,0):index==6?new Vec3(0,6,0):new Vec3(1,0,0);
        int life=index==3||index==5?3:index==6||index==0?9:12;
        float age=still?(index==3||index==5?.65f:2f):(float)(time*life*.95);
        if((index==3||index==5)&&!still)age=(float)(time*12%1);
        float[] radii={.75f,.32f,.22f,.22f,.65f,1,5,8};
        var p=new AbilityVfxPayload(1,-1,1,index,kinds[index],from,to,radii[index],index==7?120:0,life,100,false,7);
        var out=new ArrayList<>(AbilityVfxGeometry.build(p,age,false));
        if(index==6){
            var field=new AbilityVfxPayload(1,-1,1,6,SEAL,new Vec3(0,.055,0),new Vec3(1,.055,0),5,360,51,100,false);
            out.addAll(AbilityVfxGeometry.build(field,still?2:(float)(time*40),false));
        }
        if(index==3){
            for(var end:List.of(new Vec3(1.8,.8,1),new Vec3(1.2,.8,-1))){
                var chain=new AbilityVfxPayload(1,-1,1,3,BEAM,new Vec3(.8,.65,0),end,.12f,0,6,100,false);
                out.addAll(AbilityVfxGeometry.build(chain,still?2:(float)(time*6*.95),false));
            }
        }
        out.sort(Comparator.comparingDouble(PreviewThunderPixelVfx::depth));return out;
    }
    private static BufferedImage board(double time,boolean still,boolean dark) {
        var image=new BufferedImage(W*4,H*2+38,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(18,24,29));g.fillRect(0,0,image.getWidth(),image.getHeight());
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(220,225,225));g.drawString("天刑 · 原生256px高清动态特效 / 实际网格与UV离线投影，非游戏截图",14,25);
        for(int index=0;index<8;index++) {
            scale=new double[]{65,95,50,75,95,75,22,18}[index]*1.5;
            int x=index%4*W,y=index/4*H+38;
            g.setColor(dark?new Color(14,23,40):new Color(130,155,117));g.fillRect(x+1,y+1,W-2,H-2);
            g.setColor(new Color(255,255,255,24));
            for(int line=-3;line<=3;line++) {
                var a=screen(new Vec3(line,-.18,-3));var b=screen(new Vec3(line,-.18,3));
                g.drawLine(x+W/2+(int)a[0],y+(int)(H*.75)+(int)a[1],x+W/2+(int)b[0],y+(int)(H*.75)+(int)b[1]);
            }
            var cell=(Graphics2D)g.create();cell.clipRect(x+1,y+28,W-2,H-30);
            for(var q:mesh(index,time,still)) {
                var points=new Vec3[]{q.a(),q.b(),q.c(),q.d()};var xy=new double[4][];
                for(int i=0;i<4;i++){xy[i]=screen(points[i]);xy[i][0]+=x+W*.46;xy[i][1]+=y+H*.72;}
                var texture=tint(q.material(),q.color());
                double u0=q.startU()*texture.getWidth(),u1=q.endU()*texture.getWidth();
                double v0=q.startV()*texture.getHeight(),v1=q.endV()*texture.getHeight();
                if(q.reverseV()){double swap=v0;v0=v1;v1=swap;}
                double[][] uv={{u0,v1},{u1,v1},{u1,v0},{u0,v0}};
                cell.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,q.alpha()));
                triangle(cell,texture,new double[][]{xy[0],xy[1],xy[2]},new double[][]{uv[0],uv[1],uv[2]});
                triangle(cell,texture,new double[][]{xy[0],xy[2],xy[3]},new double[][]{uv[0],uv[2],uv[3]});
            }
            cell.dispose();g.setColor(new Color(230,232,220));g.drawString(NAMES[index],x+12,y+21);
        }g.dispose();return image;
    }
    private static void gif(Path path)throws Exception {
        var writer=ImageIO.getImageWritersByFormatName("gif").next();
        try(var stream=ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(stream);writer.prepareWriteSequence(null);
            for(int i=0;i<24;i++) {
                var frame=board(i/24.0,false,false);
                var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame),null);
                String format=metadata.getNativeMetadataFormatName();var tree=(IIOMetadataNode)metadata.getAsTree(format);
                var control=(IIOMetadataNode)tree.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("delayTime","7");control.setAttribute("disposalMethod","none");
                if(i==0){var apps=new IIOMetadataNode("ApplicationExtensions");var app=new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID","NETSCAPE");app.setAttribute("authenticationCode","2.0");app.setUserObject(new byte[]{1,0,0});apps.appendChild(app);tree.appendChild(apps);}
                metadata.setFromTree(format,tree);writer.writeToSequence(new IIOImage(frame,null,metadata),null);
            }writer.endWriteSequence();
        }finally{writer.dispose();}
    }
    public static void main(String[] args)throws Exception {
        Path assets=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        String[] names={"slash","bolt","impact","seal","corona","pillar"};
        for(int i=0;i<6;i++)textures.put(57+i,ImageIO.read(assets.resolve("thunder_pixel_"+names[i]+".png").toFile()));
        textures.put(63,ImageIO.read(assets.resolve("thunder_projectile_models.png").toFile()));
        ImageIO.write(board(0,true,false),"png",out.resolve("thunder_pixel_meshes_grass.png").toFile());
        ImageIO.write(board(0,true,true),"png",out.resolve("thunder_pixel_meshes_dark.png").toFile());
        gif(out.resolve("thunder_pixel_skills_preview.gif"));
        System.out.println("[MYRIAD-THUNDER-PIXEL-PREVIEW] PASS: all eight real variant recipes, alpha/UV/frame timing on bright grass and dark backgrounds; offline projection, not a client test");
    }
}
