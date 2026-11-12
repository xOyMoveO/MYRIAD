package com.myriad.tools;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.client.firstperson.*;
import com.myriad.character.skills.shuo.*;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Exact production UV/alpha meshes over QA backgrounds, not a Minecraft screenshot. */
public final class ShuoVfxPreview {
    private static BufferedImage art,point;
    static final Map<Integer,BufferedImage> materials=new HashMap<>();
    private static final int W=640,H=360;
    private static final float F=(float)(180/Math.tan(Math.toRadians(35)));
    private static BufferedImage frame(float p,Color bg,boolean world){
        return frame(p,bg,world,0);
    }
    private static BufferedImage frame(float p,Color bg,boolean world,int action){
        var im=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();g.setColor(bg);g.fillRect(0,0,W,H);
        g.setColor(new Color(130,145,150));g.drawLine(315,180,325,180);g.drawLine(320,175,320,185);g.dispose();
        float t=action==0?p*.66f:.18f+p*.52f;
        List<Quad> mesh=world?ShuoVfx.build(ShuoVisuals.basicRecipe(1,new Vec3(0,0,-.55),new Vec3(0,0,-1),true,action,0),action==0?p*6:t*9,false)
            :ShuoTrail.build(ShuoRig.frame(action,t,true),false);
        mesh.stream().sorted(Comparator.comparingDouble(q->(q.a().z+q.b().z+q.c().z+q.d().z)/4)).forEach(q->{
            if(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).z<=0)return;
            var v=new Vec3[]{q.a(),q.b(),q.c(),q.d()};float v0=q.startV(),v1=q.endV();if(q.reverseV()){float swap=v0;v0=v1;v1=swap;}
            float[] uv={q.startU(),v1,q.endU(),v1,q.endU(),v0,q.startU(),v0};
            triangle(im,q,v,uv,0,1,2);triangle(im,q,v,uv,0,2,3);
        });
        g=im.createGraphics();g.setColor(new Color(10,18,28,230));g.fillRect(0,0,640,45);g.setColor(Color.WHITE);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));
        g.drawString("朔 · "+(action==0?"斜斩":action==11?"反向上挑":"横向收斩")+" / "+(world?"世界视角":"第一人称")+" / "+Math.round(p*100)+"%",12,19);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.drawString("真实纹理、几何和透明度的离线合成 · 非游戏截图",12,37);g.dispose();return im;
    }
    static void triangle(BufferedImage im,Quad q,Vec3[] points,float[] uv,int a,int b,int c){
        int[] ix={a,b,c};float[] x=new float[3],y=new float[3],z=new float[3];
        for(int i=0;i<3;i++){var v=points[ix[i]];if(v.z>=-.05)return;z[i]=(float)(1/-v.z);x[i]=320+(float)v.x*z[i]*F;y[i]=180-(float)v.y*z[i]*F;}
        float area=(y[1]-y[2])*(x[0]-x[2])+(x[2]-x[1])*(y[0]-y[2]);if(Math.abs(area)<1E-6)return;
        int x0=Math.max(0,(int)Math.floor(Math.min(x[0],Math.min(x[1],x[2])))),x1=Math.min(W-1,(int)Math.ceil(Math.max(x[0],Math.max(x[1],x[2]))));
        int y0=Math.max(0,(int)Math.floor(Math.min(y[0],Math.min(y[1],y[2])))),y1=Math.min(H-1,(int)Math.ceil(Math.max(y[0],Math.max(y[1],y[2]))));
        var tex=materials.containsKey(q.material())?materials.get(q.material()):q.material()==75?art:point;
        for(int py=y0;py<=y1;py++)for(int px=x0;px<=x1;px++){
            float u=((y[1]-y[2])*(px+.5f-x[2])+(x[2]-x[1])*(py+.5f-y[2]))/area;
            float v=((y[2]-y[0])*(px+.5f-x[2])+(x[0]-x[2])*(py+.5f-y[2]))/area,w=1-u-v;
            if(u<0||v<0||w<0)continue;float d=u*z[0]+v*z[1]+w*z[2];
            float tu=(u*z[0]*uv[a*2]+v*z[1]*uv[b*2]+w*z[2]*uv[c*2])/d,tv=(u*z[0]*uv[a*2+1]+v*z[1]*uv[b*2+1]+w*z[2]*uv[c*2+1])/d;
            int rgba=tex.getRGB(Math.clamp((int)(tu*tex.getWidth()),0,tex.getWidth()-1),Math.clamp((int)(tv*tex.getHeight()),0,tex.getHeight()-1));
            float alpha=(rgba>>>24)/255f*q.alpha();if(alpha<=0)continue;int old=im.getRGB(px,py),rgb=0;
            for(int shift:new int[]{16,8,0}){float color=((rgba>>shift)&255)*((q.color()>>shift)&255)/255f;rgb|=Math.clamp(Math.round(color*alpha+((old>>shift)&255)*(1-alpha)),0,255)<<shift;}
            im.setRGB(px,py,rgb);
        }
    }
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(args[0]).resolve("src/main/resources/assets/myriad"),out=Path.of(args[1]);Files.createDirectories(out);
        art=ImageIO.read(assets.resolve("textures/particle/vfx/shuo_basic_hd.png").toFile());
        var atlas=com.google.gson.JsonParser.parseString(Files.readString(assets.resolve("particles/ability_mesh.json"))).getAsJsonObject().getAsJsonArray("textures");
        String[] first=atlas.get(0).getAsString().split(":");
        if(first[0].equals("minecraft"))try(var zip=new java.util.zip.ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            point=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/particle/"+first[1]+".png")));
        }else point=ImageIO.read(assets.resolve("textures/particle/"+first[1]+".png").toFile());
        var colors=List.of(new Color(32,49,66),new Color(104,139,65),new Color(215,227,239));
        var sheet=new BufferedImage(2560,1440,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();var animation=new ArrayList<BufferedImage>();
        for(int i=0;i<16;i++){var image=frame((i+.5f)/16,colors.get(0),false);g.drawImage(image,i%4*W,i/4*H,null);animation.add(image);}g.dispose();
        ImageIO.write(sheet,"png",out.resolve("shuo_basic_16_stages.png").toFile());
        var contexts=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);g=contexts.createGraphics();
        for(int i=0;i<3;i++)for(int row=0;row<2;row++)g.drawImage(frame(.49f,colors.get(i),row==1),i*W,row*H,null);g.dispose();
        ImageIO.write(contexts,"png",out.resolve("shuo_basic_contexts.png").toFile());
        for(int i=0;i<5;i++)animation.add(frame(1,colors.get(0),false));NightDaggerPreview.gif(animation,out.resolve("shuo_basic_flow.gif"));
        var variants=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);g=variants.createGraphics();int[] actions={0,11,12};
        for(int i=0;i<3;i++)for(int row=0;row<2;row++)g.drawImage(frame(.49f,colors.get(1),row==1,actions[i]),i*W,row*H,null);g.dispose();
        ImageIO.write(variants,"png",out.resolve("shuo_three_trails.png").toFile());
        System.out.println("[MYRIAD-SHUO-SLASH-PREVIEW] PASS: 16 actual geometry stages / full-alpha composites over dark, grass-green and sky-light QA backgrounds, local/world; NOT live-client acceptance");
    }
}
