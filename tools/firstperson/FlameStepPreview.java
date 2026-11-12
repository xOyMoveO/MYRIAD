package com.myriad.tools;

import com.myriad.client.firstperson.*;
import com.myriad.character.skills.fenjue.rework.*;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Actual production skin, weapon, poses and native image meshes; CPU preview, not Minecraft footage. */
public final class FlameStepPreview {
    private static final Map<Integer,BufferedImage> textures=new HashMap<>();
    private static List<NightDaggerPreview.Quad> blade,standing;
    private static final List<NightDaggerPreview.Quad> terrain=new ArrayList<>();
    private static FlameDaoArmModel.Skin arm;
    private record Vertex(Vector3f p,float u,float v){Vertex mix(Vertex b,float t){return new Vertex(new Vector3f(p).lerp(b.p,t),u+(b.u-u)*t,v+(b.v-v)*t);}}
    private static AbilityVfxPayload sides(Vec3 feet,long time){return new AbilityVfxPayload(1,1,2,3,SHIELD,feet,feet.add(0,0,1),.8f,0,3,time,false);}
    private static final AbilityVfxPayload END=new AbilityVfxPayload(1,-1,2,3,ARC,new Vec3(0,1.62,8),new Vec3(0,1.62,9),2.2f,1,8,106,false);
    private static Vector3f vec(Vec3 p){return new Vector3f((float)p.x,(float)p.y,(float)p.z);}
    private static BufferedImage draw(double tick,boolean observer){
        var image=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(42,63,86),0,360,new Color(92,120,91)));g.fillRect(0,0,640,360);g.dispose();
        var depth=new float[640*360];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        double travel=Math.min(8,tick*8/6);var feet=new Vec3(0,0,travel);var eye=feet.add(0,1.62,0);
        var target=observer?feet.add(0,1,1):eye.add(0,0,1);
        var camera=observer?feet.add(6,3.5,6):eye;
        var view=new Matrix4f().lookAt(vec(camera),vec(target),new Vector3f(0,1,0));
        NightDaggerPreview.submit(image,depth,terrain,view);
        if(observer)NightDaggerPreview.submit(image,depth,standing,new Matrix4f(view).translate(0,1.35f,(float)travel).scale(.9f,-.9f,-.9f));
        var step=new FlameStepMotion();step.accept(sides(Vec3.ZERO,100),0,0,new FlameDaoMotion().frame(0));
        if(tick>=6)step.accept(END,6,0,step.frame(6));
        if(!observer){
            var frame=step.frame(tick);arm.pose(frame.pose(),0);
            NightDaggerPreview.submit(image,depth,FastTrailPreview.model(arm.model()),FlameDaoRig.hand(frame.pose(),0,false).mul(FlameDaoRig.arm(false,false)));
            var matrix=FlameDaoRig.wrist(frame.pose(),0,false).mul(FlameDaoRig.blade(frame,false));
            NightDaggerPreview.submit(image,depth,blade,matrix);
            for(var q:blade)for(var p:q.p())NightDaggerPreview.require(matrix.transformPosition(new Vector3f(p)).z<-.051,"Step blade crosses first-person near plane");
        }
        var world=new ArrayList<Quad>();
        if(tick<7){double refresh=Math.min(4,Math.floor(tick/2)*2);world.addAll(FlameStepVfx.build(sides(feet,100+(long)refresh),(float)(tick-refresh),false));}
        if(tick>=6)world.addAll(FlameStepVfx.build(END,(float)(tick-6),false));
        world.sort(Comparator.comparingDouble(q->view.transformPosition(vec(q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25))).z));
        for(var q:world)paint(image,depth,q,view);
        if(!observer)for(var q:FlameStepTrail.mesh(step,tick,false,0))paint(image,depth,q,new Matrix4f());
        g=image.createGraphics();g.setColor(new Color(9,17,28,220));g.fillRect(0,0,640,43);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(255,208,139));
        g.drawString("炎步 · "+(observer?"双侧流光 / 终点单斩":"刀尖向左蓄势 / 停步即斩")+String.format(" · %.2fs",tick/20),12,19);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.setColor(new Color(180,198,194));
        g.drawString("真实角色手臂/武器/网格与贴图 · CPU离线预览，非实机录屏",12,36);g.dispose();return image;
    }
    private static void paint(BufferedImage image,float[] depth,Quad q,Matrix4f view){
        var p=new Vector3f[]{view.transformPosition(vec(q.a())),view.transformPosition(vec(q.b())),view.transformPosition(vec(q.c())),view.transformPosition(vec(q.d()))};
        var normal=new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0]));
        if(normal.dot(new Vector3f(p[0]).add(p[1]).add(p[2]).add(p[3]))>=0)return;
        float v0=q.startV(),v1=q.endV();if(q.reverseV()){float swap=v0;v0=v1;v1=swap;}
        var vertices=List.of(new Vertex(p[0],q.startU(),v1),new Vertex(p[1],q.endU(),v1),new Vertex(p[2],q.endU(),v0),new Vertex(p[3],q.startU(),v0));
        for(int[] indices:new int[][]{{0,1,2},{0,2,3}}){
            var clipped=new ArrayList<Vertex>();var input=List.of(vertices.get(indices[0]),vertices.get(indices[1]),vertices.get(indices[2]));
            for(int i=0;i<3;i++){
                var a=input.get(i);var b=input.get((i+1)%3);boolean ai=a.p.z<-.06f,bi=b.p.z<-.06f;
                if(ai)clipped.add(a);if(ai!=bi)clipped.add(a.mix(b,(-.06f-a.p.z)/(b.p.z-a.p.z)));
            }
            for(int i=1;i+1<clipped.size();i++){
                var a=clipped.get(0);var b=clipped.get(i);var c=clipped.get(i+1);
                FastTrailPreview.alphaTriangle(image,depth,new Vector3f[]{a.p,b.p,c.p},new float[]{a.u,a.v,b.u,b.v,c.u,c.v},textures.get(q.material()),q.alpha(),q.color(),0,1,2);
            }
        }
    }
    private static void tile(BufferedImage texture,float x,float z){terrain.add(new NightDaggerPreview.Quad(
        new Vector3f[]{new Vector3f(x,0,z),new Vector3f(x,0,z+1),new Vector3f(x+1,0,z+1),new Vector3f(x+1,0,z)},new float[]{0,0,1,0,1,1,0,1},texture));}
    public static void main(String[] args)throws Exception{
        var project=Path.of(args[0]);var out=Path.of(args[1]);Files.createDirectories(out);var assets=project.resolve("src/main/resources/assets/myriad");
        NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/fenjue_flame_changdao.png").toFile());blade=NightDaggerPreview.loadBlade(assets,"lieyanjian");
        for(var entry:Map.of(64,"fenjue_pixel_slash",68,"fenjue_pixel_stream",70,"fenjue_crisp_slash").entrySet())textures.put(entry.getKey(),ImageIO.read(assets.resolve("textures/particle/vfx/"+entry.getValue()+".png").toFile()));
        try(var zip=new ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
            var grass=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/block/grass_block_top.png")));
            var stone=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/block/stone_bricks.png")));
            textures.put(0,ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/particle/glow.png"))));
            for(int x=-9;x<10;x++)for(int z=-5;z<22;z++)tile(Math.abs(x)<2?stone:grass,x,z);
        }
        arm=FlameDaoArmModel.bake(false,false);
        var root=net.minecraft.client.model.geom.builders.LayerDefinition.create(net.minecraft.client.model.player.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE,false),64,64).bakeRoot();
        standing=FastTrailPreview.model(root);
        var sheet=new BufferedImage(2560,1440,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        double[] times={1,3,5.6,6.25,7,8.1,9.25,11};
        for(int i=0;i<8;i++){g.drawImage(draw(times[i],false),i%4*640,i/4*360,null);g.drawImage(draw(times[i],true),i%4*640,720+i/4*360,null);}
        g.dispose();ImageIO.write(sheet,"png",out.resolve("flame_step_contact.png").toFile());
        var first=new ArrayList<BufferedImage>();var third=new ArrayList<BufferedImage>();
        for(int i=0;i<24;i++){first.add(draw(i*.8,false));third.add(draw(i*.8,true));}
        NightDaggerPreview.gif(first,out.resolve("flame_step_firstperson.gif"));NightDaggerPreview.gif(third,out.resolve("flame_step_world.gif"));
        System.out.println("[MYRIAD-FLAME-STEP-PREVIEW] PASS: actual hand/weapon/side streams/endpoint image, camera clipping checked; CPU preview, not live-client acceptance");
    }
}
