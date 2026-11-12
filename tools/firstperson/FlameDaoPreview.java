package com.myriad.tools;

import com.myriad.client.firstperson.FlameDaoMotion;
import com.myriad.client.firstperson.FlameDaoRig;
import com.myriad.client.firstperson.FlameDaoArmModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual exported item + real vanilla skin arm + production rig; offline perspective, NOT game capture. */
public final class FlameDaoPreview {
    private static FlameDaoArmModel.Skin wide,slimLeft;
    private static java.util.List<ModelPart.Cube> cubes(ModelPart model,boolean cloth){
        var result=new ArrayList<ModelPart.Cube>();
        model.visit(new PoseStack(),(p,path,index,cube)->{if(path.isEmpty()!=cloth)result.add(cube);});return result;
    }
    private static void verifySkin(){
        int poses=0;
        for(boolean slim:new boolean[]{false,true})for(boolean left:new boolean[]{false,true}){
            var skin=FlameDaoArmModel.bake(slim,left);var rest=mesh(skin.model());
            var body=cubes(skin.model(),false);var cloth=cubes(skin.model(),true);
            var sideFaces=new ArrayList<Integer>();
            for(int i=0;i<body.getFirst().polygons.length;i++)if(Math.abs(body.getFirst().polygons[i].normal().y())<.1)sideFaces.add(i);
            NightDaggerPreview.require(body.size()==12&&cloth.size()==12&&sideFaces.size()==4,"Twelve shared-ring bands, not two intersecting arm caps");
            var motion=new FlameDaoMotion();
            for(int cut=0;cut<3;cut++){
                double start=cut*22;motion.attack(start,20);
                for(int n=0;n<=40;n++)for(float equip:new float[]{0,1}){
                    var f=motion.frame(start+motion.duration()*n/40.0);skin.pose(f.pose(),equip);
                    for(var chain:java.util.List.of(body,cloth)){
                        for(int j=0;j<chain.size()-1;j++)for(int face:sideFaces){
                            var a=chain.get(j).polygons[face].vertices();var b=chain.get(j+1).polygons[face].vertices();int shared=0;
                            for(var x:a)for(var y:b)if(Math.abs(x.u()-y.u())<1E-6&&Math.abs(x.v()-y.v())<1E-6){
                                float dx=x.x()-y.x(),dy=x.y()-y.y(),dz=x.z()-y.z();
                                NightDaggerPreview.require(dx*dx+dy*dy+dz*dz<1E-8,"Shared UV ring has no positional crack");shared++;
                            }
                            NightDaggerPreview.require(shared>=2,"Adjacent bands share their complete skin edge");
                        }
                        for(var cube:chain)for(var poly:cube.polygons){
                            NightDaggerPreview.require(poly.normal().isFinite(),"Deformed normals remain finite");
                        }
                    }
                    var posed=mesh(skin.model());var matrix=FlameDaoRig.hand(f.pose(),equip,left).mul(FlameDaoRig.arm(slim,left));
                    for(int q=0;q<posed.size();q++)for(int v=0;v<4;v++){
                        var source=rest.get(q).p()[v];var local=posed.get(q).p()[v];
                        var world=matrix.transformPosition(new Vector3f(local));
                        NightDaggerPreview.require(world.isFinite()&&world.z<-.051,"Actual skinned arm avoids camera near plane");
                        if(source.y>=6f/16)NightDaggerPreview.require(source.distance(local)<1E-6,"Fist/palm stay rigid; grip trajectory unchanged");
                        if(source.y<=-1.99f/16)NightDaggerPreview.require(world.y/-world.z<-Math.tan(Math.toRadians(55)),"Complete proximal forearm end stays below a 110-degree view, no floating cap");
                    }
                    poses++;
                }
            }
        }
        System.out.println("[MYRIAD-FLAME-SKIN] PASS: "+poses+" actual skin/equip/hand poses, shared UV/position rings joined, rigid fist, finite normals/near-plane clearance; forearm end fully below a 110-degree view");
    }
    private static java.util.List<NightDaggerPreview.Quad> mesh(ModelPart part){
        var result=new ArrayList<NightDaggerPreview.Quad>();
        part.visit(new PoseStack(),(pose,path,index,cube)->{
            for(var polygon:cube.polygons){
                var vertices=new Vector3f[4];var uv=new float[8];
                for(int i=0;i<4;i++){var v=polygon.vertices()[i];
                    vertices[i]=pose.pose().transformPosition(new Vector3f(v.worldX(),v.worldY(),v.worldZ()));uv[i*2]=v.u();uv[i*2+1]=v.v();}
                result.add(new NightDaggerPreview.Quad(vertices,uv,NightDaggerPreview.skin));
            }
        });return result;
    }
    private static BufferedImage draw(FlameDaoMotion.Frame f,boolean left,String title){
        int w=NightDaggerPreview.W,h=NightDaggerPreview.H;
        var im=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(29,44,61),0,h,new Color(69,91,85)));g.fillRect(0,0,w,h);
        g.setColor(new Color(180,200,195));g.drawLine(w/2-5,h/2,w/2+5,h/2);g.drawLine(w/2,h/2-5,w/2,h/2+5);g.dispose();
        float[] depth=new float[w*h];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        var wrist=FlameDaoRig.wrist(f.pose(),0,left);
        var skin=left?slimLeft:wide;skin.pose(f.pose(),0);
        NightDaggerPreview.submit(im,depth,mesh(skin.model()),FlameDaoRig.hand(f.pose(),0,left).mul(FlameDaoRig.arm(left,left)));
        NightDaggerPreview.submit(im,depth,NightDaggerPreview.blade,new Matrix4f(wrist).mul(FlameDaoRig.blade(f,left)));
        if(f.cut()<0){int total=0,central=0;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(Float.isFinite(depth[y*w+x])){
                total++;if(x>=w*.4&&x<w*.6&&y>=h*.3&&y<h*.7)central++;
            }
            NightDaggerPreview.require(total>1500,"Actual hand and dao visible in idle");
            NightDaggerPreview.require(central<100,"Idle central aiming window clear: "+central);
            System.out.println("[MYRIAD-FLAME-IDLE] left/slim="+left+" total pixels="+total+" central="+central);
        }
        g=im.createGraphics();g.setColor(new Color(10,19,30,220));g.fillRect(0,0,w,47);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(255,197,137));g.drawString(title,12,20);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.setColor(new Color(170,190,190));
        g.drawString("实际模型 / 皮肤手臂 / 生产变换 · 离线投影，非游戏截图",12,39);g.dispose();return im;
    }
    public static void main(String[] args)throws Exception{
        Path project=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        Path assets=project.resolve("src/main/resources/assets/myriad");
        NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/fenjue_flame_changdao.png").toFile());
        NightDaggerPreview.blade=NightDaggerPreview.loadBlade(assets,"lieyanjian");
        try(var zip=new ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
        }
        verifySkin();
        wide=FlameDaoArmModel.bake(false,false);slimLeft=FlameDaoArmModel.bake(true,true);
        var idle=new BufferedImage(1280,360,BufferedImage.TYPE_INT_RGB);var ig=idle.createGraphics();
        var motion=new FlameDaoMotion();
        ig.drawImage(draw(motion.frame(0),false,"焚诀 · 低位待机 / 刃口朝前"),0,0,null);
        ig.drawImage(draw(motion.frame(0),true,"左惯用手 / 细臂适配"),640,0,null);ig.dispose();
        ImageIO.write(idle,"png",out.resolve("fenjue_idle.png").toFile());
        String[] names={"右上斜劈 → 左下","左下反撩 → 右上","横斩 → 收势"};
        var sheet=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        var frames=new ArrayList<BufferedImage>();
        for(int cut=0;cut<3;cut++){
            double start=cut*22;motion.attack(start,20);
            for(int i=0;i<3;i++)g.drawImage(draw(motion.frame(start+motion.duration()*new double[]{.20,.42,.65}[i]),false,
                    "第"+(cut+1)+"招 · "+names[cut]+" · "+new String[]{"起势","挥斩","随势"}[i]),i*640,cut*360,null);
            for(int i=0;i<28;i++)frames.add(draw(motion.frame(start+i*.8),false,"第"+(cut+1)+"招 · "+names[cut]));
            // Dense actual geometry scan for both hands (not only the wrist centre).
            for(int i=0;i<=200;i++)for(boolean left:new boolean[]{false,true}){
                var f=motion.frame(start+motion.duration()*i/200.0);
                var b=FlameDaoRig.wrist(f.pose(),0,left).mul(FlameDaoRig.blade(f,left));
                for(var q:NightDaggerPreview.blade)for(var v:q.p()){
                    var p=b.transformPosition(new Vector3f(v));
                    NightDaggerPreview.require(p.isFinite()&&p.z<-.051,"Actual dao stays beyond near plane: "+cut+" "+i+" "+p);
                }
            }
        }
        g.dispose();ImageIO.write(sheet,"png",out.resolve("fenjue_three_cuts.png").toFile());
        NightDaggerPreview.gif(frames,out.resolve("fenjue_three_cuts.gif"));
        System.out.println("[MYRIAD-FLAME-PREVIEW] PASS: real 423-cube item/UV and skin arm; central-clear idle; 1206 actual-mesh near-plane scans; 84-frame three-cut GIF. Offline only.");
    }
}
