package com.myriad.tools;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.myriad.client.firstperson.NightDaggerMotion;
import com.myriad.client.firstperson.NightDaggerRig;
import com.myriad.client.firstperson.NightDaggerArmModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.resources.model.cuboid.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Offline CPU projection of the real dagger and vanilla arm mesh. Not a game screenshot. */
public final class NightDaggerPreview {
    record Quad(Vector3f[] p,float[] uv,BufferedImage texture) { }
    static final int W=640,H=360;
    static BufferedImage dagger,skin;
    static List<Quad> blade;
    static Path output;
    static double minBladeZ=0;
    static void require(boolean ok,String message) {if(!ok)throw new IllegalStateException(message);}
    static float[] floats(JsonArray a) {float[] v=new float[a.size()];for(int i=0;i<v.length;i++)v[i]=a.get(i).getAsFloat();return v;}

    static List<Quad> loadBlade(Path assets) throws Exception {
        return loadBlade(assets,"yingxiao_shadow_dagger");
    }
    static List<Quad> loadBlade(Path assets,String name) throws Exception {
        var path=assets.resolve("models/item/"+name+".json");
        var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        var out=new ArrayList<Quad>();
        try(var r=Files.newBufferedReader(path)) {
            require(CuboidModel.fromStream(r).geometry()!=null,"Real MC parser accepts the first-person mesh");
        }
        for(var e:json.getAsJsonArray("elements")) {
            var element=e.getAsJsonObject();
            var cube=CuboidModel.GSON.fromJson(element,CuboidModelElement.class);
            float[] a=floats(element.getAsJsonArray("from")), b=floats(element.getAsJsonArray("to"));
            for(var face:element.getAsJsonObject("faces").entrySet()) {
                int[][] c=switch(face.getKey()) {
                    case "north"->new int[][]{{1,1,0},{0,1,0},{0,0,0},{1,0,0}};
                    case "south"->new int[][]{{0,1,1},{1,1,1},{1,0,1},{0,0,1}};
                    case "east"->new int[][]{{1,1,1},{1,1,0},{1,0,0},{1,0,1}};
                    case "west"->new int[][]{{0,1,0},{0,1,1},{0,0,1},{0,0,0}};
                    case "up"->new int[][]{{1,1,1},{0,1,1},{0,1,0},{1,1,0}};
                    default->new int[][]{{1,0,0},{0,0,0},{0,0,1},{1,0,1}};
                };
                var p=new Vector3f[4];
                for(int i=0;i<4;i++) {
                    p[i]=new Vector3f(c[i][0]==0?a[0]:b[0],c[i][1]==0?a[1]:b[1],c[i][2]==0?a[2]:b[2]).div(16);
                    if(cube.rotation()!=null) {
                        var rot=cube.rotation();
                        p[i].sub(rot.origin());rot.transform().transformPosition(p[i]);p[i].add(rot.origin());
                    }
                    p[i].sub(.5f,.5f,.5f); // ItemTransform.NONE in the actual item renderer.
                }
                var uv=floats(face.getValue().getAsJsonObject().getAsJsonArray("uv"));
                out.add(new Quad(p,new float[]{uv[0]/16,uv[1]/16,uv[2]/16,uv[1]/16,
                    uv[2]/16,uv[3]/16,uv[0]/16,uv[3]/16},dagger));
            }
        }
        return out;
    }
    static List<Quad> arm(boolean slim,boolean left) {
        var root=NightDaggerArmModel.bake(slim);
        var arm=root.getChild(left?"left_arm":"right_arm");
        arm.resetPose();arm.setPos(0,0,0);arm.setRotation(0,0,0);
        var out=new ArrayList<Quad>();
        arm.visit(new PoseStack(),(pose,path,index,cube)->{
            for(var polygon:cube.polygons) {
                var p=new Vector3f[4];var uv=new float[8];
                for(int i=0;i<4;i++) {
                    var v=polygon.vertices()[i];
                    p[i]=pose.pose().transformPosition(new Vector3f(v.worldX(),v.worldY(),v.worldZ()));
                    uv[i*2]=v.u();uv[i*2+1]=v.v();
                }
                out.add(new Quad(p,uv,skin));
            }
        });
        return out;
    }
    static BufferedImage draw(NightDaggerMotion.Pose pose,boolean slim,boolean left,String title) {
        var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(29,44,61),0,H,new Color(69,91,85)));g.fillRect(0,0,W,H);
        g.setColor(new Color(100,128,125));g.drawLine(0,H/2,W,H/2);
        g.setColor(new Color(190,201,194,100));g.drawLine(W/2-5,H/2,W/2+5,H/2);g.drawLine(W/2,H/2-5,W/2,H/2+5);g.dispose();
        float[] depth=new float[W*H];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        var wrist=NightDaggerRig.wrist(pose,left);
        submit(image,depth,arm(slim,left),new Matrix4f(wrist).mul(NightDaggerRig.arm(slim,left)));
        submit(image,depth,blade,new Matrix4f(wrist).mul(NightDaggerRig.blade(left,pose.grip())));
        g=image.createGraphics();g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));
        g.setColor(new Color(12,20,28,210));g.fillRect(0,0,W,48);g.setColor(new Color(225,218,180));g.drawString(title,12,20);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.setColor(new Color(170,190,190));
        g.drawString("离线网格投影 · 非游戏截图 · 原版 Steve 示例皮肤",12,39);g.dispose();return image;
    }
    static void submit(BufferedImage im,float[] depth,List<Quad> mesh,Matrix4f matrix) {
        for(var q:mesh) {
            var p=new Vector3f[4];
            for(int i=0;i<4;i++)p[i]=matrix.transformPosition(new Vector3f(q.p[i]));
            var normal=new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0])).normalize();
            float shade=.62f+.38f*Math.abs(normal.dot(new Vector3f(.3f,.6f,1).normalize()));
            triangle(im,depth,p,q.uv,q.texture,0,1,2,shade);triangle(im,depth,p,q.uv,q.texture,0,2,3,shade);
        }
    }
    static int[] occlusion(NightDaggerMotion.Pose pose,boolean slim,boolean left,boolean previous) {
        var im=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        float[] depth=new float[W*H];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        var wrist=NightDaggerRig.wrist(pose,left);
        if(previous)wrist.scale(1/NightDaggerRig.VIEW_SCALE);
        var armMesh=arm(slim,left);
        if(previous)for(var q:armMesh)for(var p:q.p) {
            // Reconstruct the preceding release's 2.4x hidden tail from the current 3.6x tail.
            if(p.y<6f/16)p.y=6f/16+(p.y-6f/16)*(2.4f/3.6f);
        }
        submit(im,depth,armMesh,new Matrix4f(wrist).mul(NightDaggerRig.arm(slim,left)));
        submit(im,depth,blade,new Matrix4f(wrist).mul(NightDaggerRig.blade(left,pose.grip())));
        int all=0,centre=0;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(Float.isFinite(depth[y*W+x])) {
            all++;
            if(x>=W*.4 && x<W*.6 && y>=H*.3 && y<H*.7)centre++;
        }
        return new int[]{all,centre};
    }
    static void triangle(BufferedImage im,float[] depth,Vector3f[] p,float[] uv,BufferedImage tex,int a,int b,int c,float shade) {
        int[] ix={a,b,c};float[] x=new float[3],y=new float[3],z=new float[3];
        float focal=(float)(H/2/Math.tan(Math.toRadians(70/2.0)));
        for(int i=0;i<3;i++) {
            if(p[ix[i]].z>=-.05)return;
            z[i]=1/-p[ix[i]].z;x[i]=W/2+p[ix[i]].x*z[i]*focal;y[i]=H/2-p[ix[i]].y*z[i]*focal;
        }
        float area=(y[1]-y[2])*(x[0]-x[2])+(x[2]-x[1])*(y[0]-y[2]);if(Math.abs(area)<1E-5)return;
        int minX=Math.max(0,(int)Math.floor(Math.min(x[0],Math.min(x[1],x[2]))));
        int maxX=Math.min(W-1,(int)Math.ceil(Math.max(x[0],Math.max(x[1],x[2]))));
        int minY=Math.max(0,(int)Math.floor(Math.min(y[0],Math.min(y[1],y[2]))));
        int maxY=Math.min(H-1,(int)Math.ceil(Math.max(y[0],Math.max(y[1],y[2]))));
        for(int py=minY;py<=maxY;py++)for(int px=minX;px<=maxX;px++) {
            float u=((y[1]-y[2])*(px+.5f-x[2])+(x[2]-x[1])*(py+.5f-y[2]))/area;
            float v=((y[2]-y[0])*(px+.5f-x[2])+(x[0]-x[2])*(py+.5f-y[2]))/area,w=1-u-v;
            if(u<0||v<0||w<0)continue;
            float d=u*z[0]+v*z[1]+w*z[2];int at=py*W+px;if(d<=depth[at])continue;
            float tu=(u*z[0]*uv[a*2]+v*z[1]*uv[b*2]+w*z[2]*uv[c*2])/d;
            float tv=(u*z[0]*uv[a*2+1]+v*z[1]*uv[b*2+1]+w*z[2]*uv[c*2+1])/d;
            int color=tex.getRGB(Math.clamp((int)(tu*tex.getWidth()),0,tex.getWidth()-1),Math.clamp((int)(tv*tex.getHeight()),0,tex.getHeight()-1));
            if((color>>>24)<128)continue;
            int r=(int)(((color>>16)&255)*shade),gg=(int)(((color>>8)&255)*shade),bb=(int)((color&255)*shade);
            im.setRGB(px,py,(r<<16)|(gg<<8)|bb);depth[at]=d;
        }
    }
    static void gif(List<BufferedImage> frames,Path file) throws Exception {
        var writer=ImageIO.getImageWritersByFormatName("gif").next();
        try(var stream=ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(stream);writer.prepareWriteSequence(null);
            for(int i=0;i<frames.size();i++) {
                var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frames.get(i)),null);
                String format=metadata.getNativeMetadataFormatName();var tree=(IIOMetadataNode)metadata.getAsTree(format);
                var control=(IIOMetadataNode)tree.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("delayTime","4");control.setAttribute("disposalMethod","none");
                if(i==0) {
                    var apps=new IIOMetadataNode("ApplicationExtensions");var app=new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID","NETSCAPE");app.setAttribute("authenticationCode","2.0");app.setUserObject(new byte[]{1,0,0});apps.appendChild(app);tree.appendChild(apps);
                }
                metadata.setFromTree(format,tree);writer.writeToSequence(new IIOImage(frames.get(i),null,metadata),null);
            }
            writer.endWriteSequence();
        } finally {writer.dispose();}
    }
    public static void main(String[] args) throws Exception {
        Path project=Path.of(args[0]);output=Path.of(args[1]);Files.createDirectories(output);
        Path assets=project.resolve("src/main/resources/assets/myriad");
        dagger=ImageIO.read(assets.resolve("textures/item/yingxiao_shadow_dagger.png").toFile());blade=loadBlade(assets);
        try(var zip=new ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")) {
            skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
        }
        var motion=new NightDaggerMotion();var stills=new ArrayList<BufferedImage>();
        stills.add(draw(NightDaggerMotion.IDLE,false,false,"待机 · 横臂反手握持"));
        for(int attack=0;attack<3;attack++) {
            double t=attack*20;motion.attack(t,20);
            stills.add(draw(motion.sample(t+14*(attack==0?.40:attack==1?.56:.62)),false,false,
                    new String[]{"第一击 · 向前刺击","第二击 · 自上而下砸击","第三击 · 转正握划切"}[attack]));
        }
        var sheet=new BufferedImage(W*2,H*2,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        for(int i=0;i<4;i++)g.drawImage(stills.get(i),i%2*W,i/2*H,null);g.dispose();ImageIO.write(sheet,"png",output.resolve("pose_sheet.png").toFile());
        var frames=new ArrayList<BufferedImage>();motion.reset();
        for(int a=0;a<3;a++) {
            motion.attack(a*22,20);
            for(int i=0;i<27;i++)frames.add(draw(motion.sample(a*22+i*.8),false,false,
                    new String[]{"第一击 · 向前刺击","第二击 · 自上而下砸击","第三击 · 转正握划切"}[a]));
        }
        gif(frames,output.resolve("night_dagger_firstperson.gif"));
        ImageIO.write(draw(NightDaggerMotion.IDLE,true,true,"左手细臂适配 · 待机"),"png",output.resolve("left_slim_idle.png").toFile());
        var oldIdle=new NightDaggerMotion.Pose(.27,-.12,-.95,0,-15,76,0,0);
        var metrics=new JsonArray();
        for(boolean slim:new boolean[]{false,true})for(boolean left:new boolean[]{false,true}) {
            var old=occlusion(oldIdle,slim,left,true);
            var now=occlusion(NightDaggerMotion.IDLE,slim,left,false);
            require(now[1]==0,"Idle leaves the central 20% width / 40% height clear on both arms/hands");
            require(now[0]<old[0]*.65,"Idle occlusion must be materially lower than the preceding release");
            var item=new JsonObject();item.addProperty("slim",slim);item.addProperty("left",left);
            item.addProperty("oldPixels",old[0]);item.addProperty("newPixels",now[0]);
            item.addProperty("oldCentralPixels",old[1]);item.addProperty("newCentralPixels",now[1]);metrics.add(item);
        }
        Files.writeString(output.resolve("idle_occlusion.json"),new GsonBuilder().setPrettyPrinting().create().toJson(metrics));
        motion.reset();motion.attack(0,20);motion.attack(20,20);
        ImageIO.write(draw(motion.sample(24.2),false,false,"第二击 · 抬手蓄势"),"png",output.resolve("overhead_raised.png").toFile());
        motion.attack(40,20);
        ImageIO.write(draw(motion.sample(44.76),false,false,"第三击 · 已转为正握"),"png",output.resolve("forward_grip_ready.png").toFile());
        // Dense actual-mesh near-plane sweep, not just the wrist centre.
        motion.reset();int samples=0;
        for(int a=0;a<3;a++) {
            motion.attack(a*20,20);
            for(int i=0;i<=200;i++)for(boolean left:new boolean[]{false,true}) {
                var pose=motion.sample(a*20+14*i/200.0);
                var matrix=NightDaggerRig.wrist(pose,left).mul(NightDaggerRig.blade(left,pose.grip()));
                for(var q:blade)for(var point:q.p) {
                    var p=matrix.transformPosition(new Vector3f(point));
                    require(p.isFinite() && p.z<-.051,"Dagger never crosses the camera near plane: "+a+" / "+i+" / "+p);
                    minBladeZ=Math.min(minBladeZ,p.z);
                }
                samples++;
            }
        }
        System.out.println("[MYRIAD-NIGHT-PREVIEW] PASS: actual MC parser/rotations/vanilla arm mesh, "+samples+" dense dagger poses; four idle central-clearance/occlusion checks; offline sheets and 81-frame GIF, not a client playtest");
    }
}
