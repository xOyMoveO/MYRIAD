package com.myriad.tools;

import com.myriad.client.firstperson.*;
import com.myriad.network.NightSkillViewPayload;
import com.myriad.character.ability.AbilityVfxGeometry;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual skin meshes, dagger, local quad/UV layers and timelines. Offline, not Minecraft capture. */
public final class NightSkillPreview {
    private static final int W=640,H=360;
    private static final String[] NAMES={"影杀 · 甩手投针","化影 · 双手合印","影袭 · 正握刃前突","回影 · 正握流光","灵盾 · 轻触副手引盾","绝影追击 · 起手转握","绝影追击 · 背后前刺"};
    private static final int[] TICKS={9,14,10,9,14,16,10};
    static final Map<Integer,BufferedImage> TEXTURES=new HashMap<>();
    private static final Map<String,BufferedImage> TINTS=new HashMap<>();
    private static NightSkillMotion motion(int action){
        var m=new NightSkillMotion();
        if(action==6)m.accept(new NightSkillViewPayload(5,16,84,1),84,0,NightDaggerMotion.IDLE);
        m.accept(new NightSkillViewPayload(action,TICKS[action],100,1),100,0,NightDaggerMotion.IDLE);return m;
    }
    private static BufferedImage tint(AbilityVfxGeometry.Quad q){
        int alpha=(int)(q.alpha()*255);String key=q.material()+":"+q.color()+":"+alpha;
        return TINTS.computeIfAbsent(key,k->{
            var src=TEXTURES.get(q.material());var out=new BufferedImage(src.getWidth(),src.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<src.getHeight();y++)for(int x=0;x<src.getWidth();x++){
                int p=src.getRGB(x,y);int a=(p>>>24)*alpha/255;
                int r=((p>>16)&255)*((q.color()>>16)&255)/255,g=((p>>8)&255)*((q.color()>>8)&255)/255,b=(p&255)*(q.color()&255)/255;
                out.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);
            }return out;
        });
    }
    static void vfx(BufferedImage image,float[] depth,List<AbilityVfxGeometry.Quad> quads,Matrix4f matrix){
        var list=new ArrayList<>(quads);list.sort(Comparator.comparingDouble(q->{
            var c=q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25);
            return matrix.transformPosition(new Vector3f((float)c.x,(float)c.y,(float)c.z)).z;
        }));
        for(var q:list){
            var raw=new net.minecraft.world.phys.Vec3[]{q.a(),q.b(),q.c(),q.d()};var p=new Vector3f[4];
            for(int i=0;i<4;i++)p[i]=matrix.transformPosition(new Vector3f((float)raw[i].x,(float)raw[i].y,(float)raw[i].z));
            float v0=q.startV(),v1=q.endV();if(q.reverseV()){float swap=v0;v0=v1;v1=swap;}
            float[] uv={q.startU(),v1,q.endU(),v1,q.endU(),v0,q.startU(),v0};var tex=tint(q);
            triangle(image,depth,p,uv,tex,0,1,2);triangle(image,depth,p,uv,tex,0,2,3);
        }
    }
    private static void triangle(BufferedImage image,float[] depth,Vector3f[] p,float[] uv,BufferedImage tex,int a,int b,int c){
        int[] ix={a,b,c};float[] x=new float[3],y=new float[3],z=new float[3];float focal=(float)(H/2/Math.tan(Math.toRadians(35)));
        for(int i=0;i<3;i++){
            if(p[ix[i]].z>=-.05)return;
            z[i]=1/-p[ix[i]].z;x[i]=W/2+p[ix[i]].x*z[i]*focal;y[i]=H/2-p[ix[i]].y*z[i]*focal;
        }
        float area=(y[1]-y[2])*(x[0]-x[2])+(x[2]-x[1])*(y[0]-y[2]);if(Math.abs(area)<1E-5)return;
        int minX=Math.max(0,(int)Math.floor(Math.min(x[0],Math.min(x[1],x[2])))),maxX=Math.min(W-1,(int)Math.ceil(Math.max(x[0],Math.max(x[1],x[2]))));
        int minY=Math.max(0,(int)Math.floor(Math.min(y[0],Math.min(y[1],y[2])))),maxY=Math.min(H-1,(int)Math.ceil(Math.max(y[0],Math.max(y[1],y[2]))));
        for(int py=minY;py<=maxY;py++)for(int px=minX;px<=maxX;px++){
            float u=((y[1]-y[2])*(px+.5f-x[2])+(x[2]-x[1])*(py+.5f-y[2]))/area;
            float v=((y[2]-y[0])*(px+.5f-x[2])+(x[0]-x[2])*(py+.5f-y[2]))/area,w=1-u-v;
            if(u<0||v<0||w<0)continue;
            float d=u*z[0]+v*z[1]+w*z[2];int at=py*W+px;if(d<=depth[at])continue;
            float tu=(u*z[0]*uv[a*2]+v*z[1]*uv[b*2]+w*z[2]*uv[c*2])/d,tv=(u*z[0]*uv[a*2+1]+v*z[1]*uv[b*2+1]+w*z[2]*uv[c*2+1])/d;
            int color=tex.getRGB(Math.clamp((int)(tu*tex.getWidth()),0,tex.getWidth()-1),Math.clamp((int)(tv*tex.getHeight()),0,tex.getHeight()-1));
            float alpha=(color>>>24)/255f;if(alpha<=0)continue;int under=image.getRGB(px,py);
            int r=(int)(((color>>16)&255)*alpha+((under>>16)&255)*(1-alpha)),g=(int)(((color>>8)&255)*alpha+((under>>8)&255)*(1-alpha)),bb=(int)((color&255)*alpha+(under&255)*(1-alpha));
            image.setRGB(px,py,(r<<16)|(g<<8)|bb);
        }
    }
    private static BufferedImage draw(NightSkillMotion.Frame frame,double time,boolean slim,boolean left,String title){
        var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(29,44,61),0,H,new Color(69,91,85)));g.fillRect(0,0,W,H);
        g.setColor(new Color(190,201,194,100));g.drawLine(W/2-5,H/2,W/2+5,H/2);g.drawLine(W/2,H/2-5,W/2,H/2+5);g.dispose();
        float[] depth=new float[W*H];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        var main=NightDaggerRig.wrist(frame.main(),left);
        NightDaggerPreview.submit(image,depth,NightDaggerPreview.arm(slim,left),new Matrix4f(main).mul(NightDaggerRig.arm(slim,left)));
        if(frame.showOff())NightDaggerPreview.submit(image,depth,NightDaggerPreview.arm(slim,!left),NightDaggerRig.wrist(frame.off(),!left).mul(NightDaggerRig.arm(slim,!left)));
        var knife=new Matrix4f(main).mul(NightDaggerRig.blade(left,frame.main().grip()));
        if(frame.showBlade())NightDaggerPreview.submit(image,depth,NightDaggerPreview.blade,knife);
        vfx(image,depth,NightSkillHandVfx.needle(frame),new Matrix4f(main).scale(.55f));
        vfx(image,depth,NightSkillHandVfx.blade(frame,time),knife);
        vfx(image,depth,NightSkillHandVfx.camera(frame,time,left),new Matrix4f());
        g=image.createGraphics();g.setColor(new Color(12,20,28,220));g.fillRect(0,0,W,48);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(225,218,180));g.drawString(title,12,20);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.setColor(new Color(170,190,190));g.drawString("实际手/刀/贴图与时间线的离线投影 · 非游戏截图 · 无第三人称动作",12,39);g.dispose();return image;
    }
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(args[0]).resolve("src/main/resources/assets/myriad"),out=Path.of(args[1]);Files.createDirectories(out);
        NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/yingxiao_shadow_dagger.png").toFile());NightDaggerPreview.blade=NightDaggerPreview.loadBlade(assets);
        try(var zip=new ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
        }
        String[] names={"blade","slash","wake","smoke","seal","impact"};
        for(int i=0;i<6;i++)TEXTURES.put(50+i,ImageIO.read(assets.resolve("textures/particle/vfx/night_gray_"+names[i]+".png").toFile()));
        TEXTURES.put(56,ImageIO.read(assets.resolve("textures/particle/vfx/night_shadow_needle.png").toFile()));
        var sheet=new BufferedImage(W*2,H*4,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        var all=new ArrayList<BufferedImage>();int checked=0;
        double[] peaks={.26,.43,.34,.35,.43,.60,.32};
        for(int action=0;action<7;action++){
            var m=motion(action);var pose=m.sample(100+TICKS[action]*peaks[action]);
            g.drawImage(draw(pose,100+TICKS[action]*peaks[action],false,false,NAMES[action]),action%2*W,action/2*H,null);
            var frames=new ArrayList<BufferedImage>();
            for(double age=0;age<TICKS[action]+4;age+=.8)frames.add(draw(m.sample(100+age),100+age,false,false,NAMES[action]));
            NightDaggerPreview.gif(frames,out.resolve("night_skill_"+action+".gif"));all.addAll(frames);
            for(boolean left:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true})for(int i=0;i<101;i++){
                var f=m.sample(100+TICKS[action]*i/101.0);if(!f.showBlade())continue;
                var matrix=NightDaggerRig.wrist(f.main(),left).mul(NightDaggerRig.blade(left,f.main().grip()));
                for(var q:NightDaggerPreview.blade)for(var p:q.p()){
                    var at=matrix.transformPosition(new Vector3f(p));
                    NightDaggerPreview.require(at.isFinite()&&at.z<-.051,"Skill knife near-plane clipping: "+action+" / "+i+" / "+at);
                }checked++;
            }
        }
        var left=motion(4);g.drawImage(draw(left.sample(106),106,true,true,"左惯用手 / 细臂 · 灵盾镜像"),W,H*3,null);g.dispose();
        ImageIO.write(sheet,"png",out.resolve("night_skill_pose_sheet.png").toFile());NightDaggerPreview.gif(all,out.resolve("night_skills_firstperson.gif"));
        var combo=new NightSkillMotion();combo.accept(new NightSkillViewPayload(5,16,100,1),100,0,NightDaggerMotion.IDLE);var ultimate=new ArrayList<BufferedImage>();
        for(double t=0;t<16;t+=.8)ultimate.add(draw(combo.sample(100+t),100+t,false,false,"绝影追击 · 0.8秒转握起手"));
        combo.accept(new NightSkillViewPayload(6,10,116,1),116,0,NightDaggerMotion.IDLE);
        for(double t=0;t<14;t+=.8)ultimate.add(draw(combo.sample(116+t),116+t,false,false,"绝影追击 · 到达背后后向前刺"));
        NightDaggerPreview.gif(ultimate,out.resolve("night_execution_firstperson.gif"));
        System.out.println("[MYRIAD-NIGHT-SKILL-PREVIEW] PASS: actual two arms, approved blade/needle, gray layers/UV/timelines; "+checked+" full knife poses, seven gesture GIFs and linked ultimate; offline only");
    }
}
