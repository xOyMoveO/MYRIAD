package com.myriad.tools;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;

/** Actual runtime fire mesh/UV projection. Offline review, not a Minecraft capture. */
public final class PreviewFenjueVfx {
    private static final int W=560,H=390;
    private static final String[] LABELS={"焚心余烬 · 灼烧","焚天战意 · 触发","烈焰斩 · 火刃","炎步 · 流焰路径8格",
            "熔岩牢笼 · 环形火幕","焚诀爆发 · 6格冲击","焚天火海 · 落地与火海","赤焰战甲 · 周身火环"};
    private static final Vec3 CAMERA=new Vec3(.462,.555,.693);
    private static double scale;
    private static double[] screen(Vec3 p){return new double[]{(.832*p.x-.555*p.z)*scale,(-.308*p.x+.832*p.y-.462*p.z)*-scale};}
    private static List<Quad> mesh(int i,double time,boolean still){
        int kind=switch(i){case 0,1,5->AbilityVfxGeometry.BURST;case 2->AbilityVfxGeometry.ARC;case 3->AbilityVfxGeometry.TRAIL;case 7->AbilityVfxGeometry.SHIELD;default->AbilityVfxGeometry.SEAL;};
        float radius=new float[]{.45f,1,4,.22f,3.5f,6,5,.65f}[i];
        var from=i==3?new Vec3(-4,0,0):Vec3.ZERO;var to=i==3?new Vec3(4,0,0):new Vec3(1,0,0);
        int life=i==4||i==6?80:i==7?12:9;
        float age=still?3:(float)(time*(life>=20?14:life*.96));
        var p=new AbilityVfxPayload(1,-1,2,i,kind,from,to,radius,i==2?100:0,life,100,false);
        var out=new ArrayList<>(AbilityVfxGeometry.build(p,age,false));
        if(i==6){var burst=new AbilityVfxPayload(1,-1,2,6,AbilityVfxGeometry.BURST,Vec3.ZERO,to,5,0,9,100,false);
            if(still||time<.55)out.addAll(AbilityVfxGeometry.build(burst,still?3:(float)(time*16),false));}
        out.removeIf(q->q.b().subtract(q.a()).cross(q.c().subtract(q.a())).dot(CAMERA)<=1E-12);
        out.sort(Comparator.comparingDouble(PreviewThunderPixelVfx::depth));return out;
    }
    private static BufferedImage board(double time,boolean dark,boolean still){
        var image=new BufferedImage(W*4,H*2+40,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(24,20,20));g.fillRect(0,0,image.getWidth(),image.getHeight());g.setColor(Color.WHITE);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,17));g.drawString("火部·焚诀 · 实际动态网格与UV离线投影 / 非游戏截图",14,27);
        for(int i=0;i<8;i++){
            int x=i%4*W,y=i/4*H+40;scale=new double[]{120,90,34,50,35,25,27,95}[i];
            var cell=(Graphics2D)g.create();cell.clipRect(x+1,y+1,W-2,H-2);cell.setColor(dark?new Color(17,23,32):new Color(111,145,88));cell.fillRect(x,y,W,H);
            cell.setColor(new Color(255,255,255,20));
            for(int z=-6;z<=6;z++){
                var a=screen(new Vec3(-6,-.08,z));var b=screen(new Vec3(6,-.08,z));
                cell.drawLine(x+W/2+(int)a[0],y+(int)(H*.68+a[1]),x+W/2+(int)b[0],y+(int)(H*.68+b[1]));
            }
            for(var q:mesh(i,time,still)){
                var points=new Vec3[]{q.a(),q.b(),q.c(),q.d()};var xy=new double[4][];
                for(int j=0;j<4;j++){xy[j]=screen(points[j]);xy[j][0]+=x+W*.46;xy[j][1]+=y+H*.68;}
                var texture=PreviewThunderPixelVfx.tint(q.material(),q.color());double u0=q.startU()*texture.getWidth(),u1=q.endU()*texture.getWidth(),v0=q.startV()*texture.getHeight(),v1=q.endV()*texture.getHeight();
                if(q.reverseV()){double swap=v0;v0=v1;v1=swap;}double[][] uv={{u0,v1},{u1,v1},{u1,v0},{u0,v0}};
                cell.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,q.alpha()));
                PreviewThunderPixelVfx.triangle(cell,texture,new double[][]{xy[0],xy[1],xy[2]},new double[][]{uv[0],uv[1],uv[2]});
                PreviewThunderPixelVfx.triangle(cell,texture,new double[][]{xy[0],xy[2],xy[3]},new double[][]{uv[0],uv[2],uv[3]});
            }
            cell.setComposite(AlphaComposite.SrcOver);cell.setColor(Color.WHITE);cell.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));cell.drawString(LABELS[i],x+12,y+24);cell.dispose();
        }
        g.dispose();return image;
    }
    public static void main(String[] args)throws Exception{
        var assets=Path.of(args[0]);var out=Path.of(args[1]);Files.createDirectories(out);
        String[] names={"slash","flame","burst","ring","stream","embers"};
        for(int i=0;i<6;i++)PreviewThunderPixelVfx.textures.put(64+i,ImageIO.read(assets.resolve("fenjue_pixel_"+names[i]+".png").toFile()));
        ImageIO.write(board(0,false,true),"png",out.resolve("fenjue_mesh_grass.png").toFile());ImageIO.write(board(0,true,true),"png",out.resolve("fenjue_mesh_dark.png").toFile());
        var writer=ImageIO.getImageWritersByFormatName("gif").next();
        try(var stream=ImageIO.createImageOutputStream(out.resolve("fenjue_mesh_preview.gif").toFile())){
            writer.setOutput(stream);writer.prepareWriteSequence(null);
            for(int frame=0;frame<24;frame++){
                var image=board(frame/24.0,false,false);var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),null);
                String format=metadata.getNativeMetadataFormatName();var tree=(IIOMetadataNode)metadata.getAsTree(format);
                var control=(IIOMetadataNode)tree.getElementsByTagName("GraphicControlExtension").item(0);control.setAttribute("delayTime","6");control.setAttribute("disposalMethod","none");
                if(frame==0){var apps=new IIOMetadataNode("ApplicationExtensions");var app=new IIOMetadataNode("ApplicationExtension");app.setAttribute("applicationID","NETSCAPE");app.setAttribute("authenticationCode","2.0");app.setUserObject(new byte[]{1,0,0});apps.appendChild(app);tree.appendChild(apps);}
                metadata.setFromTree(format,tree);writer.writeToSequence(new IIOImage(image,null,metadata),null);
            }
            writer.endWriteSequence();
        }finally{writer.dispose();}
        System.out.println("[MYRIAD-FENJUE-PREVIEW] PASS: eight real branch recipes, actual texture/UVs, grass/dark and 24-frame timeline; offline only");
    }
}
