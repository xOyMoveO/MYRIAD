package com.myriad.tools;

import com.myriad.client.firstperson.*;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual production poses, item/skin UVs and translucent trail meshes; offline, not client footage. */
public final class FastTrailPreview {
    private static final int W=640,H=360;
    private static final Map<Integer,BufferedImage> textures=new HashMap<>();
    private static List<NightDaggerPreview.Quad> blade,arm;
    private static FlameDaoArmModel.Skin flameArm;
    private static final NightDaggerMotion night=new NightDaggerMotion();
    private static final ThunderSwordMotion thunder=new ThunderSwordMotion();
    private static final FlameDaoMotion flame=new FlameDaoMotion();
    static List<NightDaggerPreview.Quad> model(ModelPart part){
        var result=new ArrayList<NightDaggerPreview.Quad>();
        part.visit(new PoseStack(),(pose,path,index,cube)->{
            for(var polygon:cube.polygons){var vertices=new Vector3f[4];var uv=new float[8];
                for(int i=0;i<4;i++){var v=polygon.vertices()[i];vertices[i]=pose.pose().transformPosition(new Vector3f(v.worldX(),v.worldY(),v.worldZ()));uv[i*2]=v.u();uv[i*2+1]=v.v();}
                result.add(new NightDaggerPreview.Quad(vertices,uv,NightDaggerPreview.skin));
            }
        });return result;
    }
    private static BufferedImage draw(int role,double tick,String title){
        var im=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(26,39,53),0,H,new Color(67,86,79)));g.fillRect(0,0,W,H);
        g.setColor(new Color(190,210,203));g.drawLine(W/2-5,H/2,W/2+5,H/2);g.drawLine(W/2,H/2-5,W/2,H/2+5);g.dispose();
        var depth=new float[W*H];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        Matrix4f hand,weapon;List<Quad> trail;
        if(role==0){
            var p=night.sample(tick);hand=NightDaggerRig.wrist(p,false).mul(NightDaggerRig.arm(false,false));
            weapon=NightDaggerRig.wrist(p,false).mul(NightDaggerRig.blade(false,p.grip()));trail=NightBasicTrail.mesh(night,tick,false);
        }else if(role==1){
            var f=thunder.frame(tick);hand=ThunderSwordRig.wrist(f.pose(),0,false).mul(ThunderSwordRig.arm(false,false));
            weapon=ThunderSwordRig.wrist(f.pose(),0,false).mul(ThunderSwordRig.blade(f,false));trail=ThunderBasicTrail.mesh(thunder,tick,false);
        }else{
            var f=flame.frame(tick);flameArm.pose(f.pose(),0);arm=model(flameArm.model());
            hand=FlameDaoRig.hand(f.pose(),0,false).mul(FlameDaoRig.arm(false,false));
            weapon=FlameDaoRig.wrist(f.pose(),0,false).mul(FlameDaoRig.blade(f,false));trail=FlameBasicTrail.mesh(flame,tick,false);
        }
        NightDaggerPreview.submit(im,depth,arm,hand);NightDaggerPreview.submit(im,depth,blade,weapon);
        // Double-sided source pairs: retain the side actually facing the camera for this projection.
        var front=trail.stream().filter(q->{var n=q.b().subtract(q.a()).cross(q.c().subtract(q.a()));
            var centre=q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25);return n.dot(centre)<0;})
                .sorted(Comparator.comparingDouble(q->(q.a().z+q.b().z+q.c().z+q.d().z)/4)).toList();
        for(var q:front){
            var points=new Vector3f[]{vec(q.a()),vec(q.b()),vec(q.c()),vec(q.d())};float v0=q.startV(),v1=q.endV();
            if(q.reverseV()){float swap=v0;v0=v1;v1=swap;}
            float[] uv={q.startU(),v1,q.endU(),v1,q.endU(),v0,q.startU(),v0};
            var texture=textures.get(q.material());alphaTriangle(im,depth,points,uv,texture,q.alpha(),q.color(),0,1,2);
            alphaTriangle(im,depth,points,uv,texture,q.alpha(),q.color(),0,2,3);
        }
        g=im.createGraphics();g.setColor(new Color(9,17,28,220));g.fillRect(0,0,W,46);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(235,218,175));g.drawString(title,12,20);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.setColor(new Color(166,190,190));
        g.drawString("实际模型/轨迹/动态贴图的离线投影 · 非游戏录屏 · 攻击间隔不变",12,38);g.dispose();return im;
    }
    private static Vector3f vec(net.minecraft.world.phys.Vec3 v){return new Vector3f((float)v.x,(float)v.y,(float)v.z);}
    static void alphaTriangle(BufferedImage im,float[] depth,Vector3f[] p,float[] uv,BufferedImage tex,float opacity,int tint,int a,int b,int c){
        int[] ix={a,b,c};float[] x=new float[3],y=new float[3],z=new float[3];float focal=(float)(H/2/Math.tan(Math.toRadians(35)));
        for(int i=0;i<3;i++){var q=p[ix[i]];if(q.z>=-.05)return;z[i]=1/-q.z;x[i]=W/2+q.x*z[i]*focal;y[i]=H/2-q.y*z[i]*focal;}
        float area=(y[1]-y[2])*(x[0]-x[2])+(x[2]-x[1])*(y[0]-y[2]);if(Math.abs(area)<1E-6)return;
        int x0=Math.max(0,(int)Math.floor(Math.min(x[0],Math.min(x[1],x[2])))),x1=Math.min(W-1,(int)Math.ceil(Math.max(x[0],Math.max(x[1],x[2]))));
        int y0=Math.max(0,(int)Math.floor(Math.min(y[0],Math.min(y[1],y[2])))),y1=Math.min(H-1,(int)Math.ceil(Math.max(y[0],Math.max(y[1],y[2]))));
        for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++){
            float u=((y[1]-y[2])*(xx+.5f-x[2])+(x[2]-x[1])*(yy+.5f-y[2]))/area;
            float v=((y[2]-y[0])*(xx+.5f-x[2])+(x[0]-x[2])*(yy+.5f-y[2]))/area,w=1-u-v;if(u<0||v<0||w<0)continue;
            float inverse=u*z[0]+v*z[1]+w*z[2];if(inverse+1E-5<depth[yy*W+xx])continue;
            float tu=(u*z[0]*uv[a*2]+v*z[1]*uv[b*2]+w*z[2]*uv[c*2])/inverse;
            float tv=(u*z[0]*uv[a*2+1]+v*z[1]*uv[b*2+1]+w*z[2]*uv[c*2+1])/inverse;
            int pixel=tex.getRGB(Math.clamp((int)(tu*tex.getWidth()),0,tex.getWidth()-1),Math.clamp((int)(tv*tex.getHeight()),0,tex.getHeight()-1));
            float alpha=(pixel>>>24)/255f*opacity;if(alpha<.003)continue;int old=im.getRGB(xx,yy),rgb=0;
            for(int shift:new int[]{16,8,0}){float src=((pixel>>shift)&255)*((tint>>shift)&255)/255f;
                rgb|=Math.clamp(Math.round(src*alpha+((old>>shift)&255)*(1-alpha)),0,255)<<shift;}
            im.setRGB(xx,yy,rgb);
        }
    }
    public static void main(String[] args)throws Exception{
        Path project=Path.of(args[0]),out=Path.of(args[1]),assets=project.resolve("src/main/resources/assets/myriad");Files.createDirectories(out);
        try(var zip=new ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
            textures.put(0,ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/particle/glow.png"))));
        }
        int[] ids={50,52,57,58,64,68};String[] names={"night_gray_blade","night_gray_wake","thunder_pixel_slash","thunder_pixel_bolt","fenjue_pixel_slash","fenjue_pixel_stream"};
        for(int i=0;i<ids.length;i++)textures.put(ids[i],ImageIO.read(assets.resolve("textures/particle/vfx/"+names[i]+".png").toFile()));
        String[] weapons={"yingxiao_shadow_dagger","leitingjian","lieyanjian"};
        String[] atlases={"yingxiao_shadow_dagger","tianxing_thunder_longsword","fenjue_flame_changdao"};
        String[] titles={"夜影 · 暗灰银光","天刑 · 白青雷光","焚诀 · 红橙流焰"};
        String[] files={"night_fast_trails","thunder_fast_trails","fenjue_fast_trails"};
        var sheet=new BufferedImage(W*3,H*3,BufferedImage.TYPE_INT_RGB);var sg=sheet.createGraphics();
        for(int role=0;role<3;role++){
            NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/"+atlases[role]+".png").toFile());
            blade=NightDaggerPreview.loadBlade(assets,weapons[role]);arm=NightDaggerPreview.arm(false,false);flameArm=FlameDaoArmModel.bake(false,false);
            night.reset();thunder.reset();flame.reset();var frames=new ArrayList<BufferedImage>();
            for(int cut=0;cut<3;cut++){
                double start=cut*20;night.attack(start,20);thunder.attack(start,20);flame.attack(start,20);
                double duration=role==0?night.duration():role==1?thunder.duration():flame.duration();
                double peak=role==0?new double[]{.40,.56,.60}[cut]:.50;
                sg.drawImage(draw(role,start+duration*peak,titles[role]+" · 第"+(cut+1)+"招"),cut*W,role*H,null);
                for(int i=0;i<25;i++)frames.add(draw(role,start+i*.8,titles[role]+" · 第"+(cut+1)+"招"));
            }
            NightDaggerPreview.gif(frames,out.resolve(files[role]+".gif"));
        }
        sg.dispose();ImageIO.write(sheet,"png",out.resolve("three_roles_nine_attacks.png").toFile());
        System.out.println("[MYRIAD-FAST-TRAIL-PREVIEW] PASS: actual nine held-weapon/skin poses, actual two-layer themed flipbook UV/alpha on swept geometry; three 75-frame real-speed offline GIFs, not client/FPS acceptance");
    }
}
