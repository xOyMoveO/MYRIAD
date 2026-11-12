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
public final class PreviewLevelUpBeam {
    private static final int W=320,H=440;
    private static final Map<Integer,BufferedImage> textures=new HashMap<>();
    private static final Map<String,BufferedImage> tints=new HashMap<>();
    private static final String[] NAMES={"升起 · 0.05秒","展开 · 0.20秒","光柱 · 0.45秒","上升光点 · 0.75秒","淡出 · 1.00秒","尾光 · 1.15秒"};
    private static double[] screen(Vec3 p) {return new double[]{(.832*p.x-.555*p.z)*79,(-.308*p.x+.832*p.y-.462*p.z)*-79};}
    private static double depth(Quad q) {var p=q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25);return .462*p.x+.555*p.y+.693*p.z;}
    private static void triangle(Graphics2D g,BufferedImage im,double[][] xy,double[][] uv) {
        double sx=uv[1][0]-uv[0][0],sy=uv[1][1]-uv[0][1],tx=uv[2][0]-uv[0][0],ty=uv[2][1]-uv[0][1];
        double determinant=sx*ty-tx*sy;if(Math.abs(determinant)<1E-8)return;
        double dx=xy[1][0]-xy[0][0],dy=xy[1][1]-xy[0][1],ex=xy[2][0]-xy[0][0],ey=xy[2][1]-xy[0][1];
        double a=(dx*ty-ex*sy)/determinant,b=(dy*ty-ey*sy)/determinant,c=(ex*sx-dx*tx)/determinant,d=(ey*sx-dy*tx)/determinant;
        var transform=new AffineTransform(a,b,c,d,xy[0][0]-a*uv[0][0]-c*uv[0][1],xy[0][1]-b*uv[0][0]-d*uv[0][1]);
        var shape=new Path2D.Double();shape.moveTo(xy[0][0],xy[0][1]);shape.lineTo(xy[1][0],xy[1][1]);shape.lineTo(xy[2][0],xy[2][1]);shape.closePath();
        var child=(Graphics2D)g.create();child.clip(shape);child.drawImage(im,transform,null);child.dispose();
    }
    private static BufferedImage tint(int material,int rgb) {
        return tints.computeIfAbsent(material+":"+rgb,key->{
            var src=textures.get(material);var result=new BufferedImage(src.getWidth(),src.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<src.getHeight();y++)for(int x=0;x<src.getWidth();x++) {
                int p=src.getRGB(x,y),r=((p>>16)&255)*((rgb>>16)&255)/255,g=((p>>8)&255)*((rgb>>8)&255)/255,b=(p&255)*(rgb&255)/255;
                result.setRGB(x,y,(p&0xFF000000)|(r<<16)|(g<<8)|b);
            }return result;
        });
    }
    private static List<Quad> mesh(int index,double time,boolean still) {
        float[] ages={1,4,9,15,20,23};
        var p=new AbilityVfxPayload(1,1,0,7,PILLAR,Vec3.ZERO,new Vec3(0,4.8,0),.65f,358,24,100,false);
        float age=still?ages[index]:(float)((time*24+index*4)%24);
        var out=new ArrayList<>(AbilityVfxGeometry.build(p,age,false));
        out.sort(Comparator.comparingDouble(PreviewLevelUpBeam::depth));return out;
    }
    private static BufferedImage board(double time,boolean still,boolean dark) {
        var image=new BufferedImage(W*3,H*2+38,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(18,24,29));g.fillRect(0,0,image.getWidth(),image.getHeight());
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(220,225,225));g.drawString("升级光柱 · 金白光芯与青色灵纹 / 实际几何离线投影，非游戏截图",14,25);
        for(int index=0;index<6;index++) {
            int x=index%3*W,y=index/3*H+38;
            g.setColor(dark?new Color(22,29,36):new Color(77,102,63));g.fillRect(x+1,y+1,W-2,H-2);
            g.setColor(new Color(255,255,255,24));
            for(int line=-3;line<=3;line++) {
                var a=screen(new Vec3(line,-.18,-3));var b=screen(new Vec3(line,-.18,3));
                g.drawLine(x+W/2+(int)a[0],y+H-40+(int)a[1],x+W/2+(int)b[0],y+H-40+(int)b[1]);
            }
            var cell=(Graphics2D)g.create();cell.clipRect(x+1,y+28,W-2,H-30);
            for(var q:mesh(index,time,still)) {
                var points=new Vec3[]{q.a(),q.b(),q.c(),q.d()};var xy=new double[4][];
                for(int i=0;i<4;i++){xy[i]=screen(points[i]);xy[i][0]+=x+W*.46;xy[i][1]+=y+H*.86;}
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
        var white=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<2;y++)for(int x=0;x<2;x++)white.setRGB(x,y,0xFFFFFFFF);textures.put(0,white);
        ImageIO.write(board(0,true,false),"png",out.resolve("level_up_beam_grass.png").toFile());
        ImageIO.write(board(0,true,true),"png",out.resolve("level_up_beam_dark.png").toFile());
        gif(out.resolve("level_up_beam_preview.gif"));
        System.out.println("[MYRIAD-LEVEL-UP-PREVIEW] PASS: actual beam stages, alpha/animation mesh projections on grass/dark; 24-frame offline GIF, not a client test");
    }
}
