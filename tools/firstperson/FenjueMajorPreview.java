package com.myriad.tools;

import com.myriad.client.firstperson.*;
import com.myriad.client.particle.FlameSurfaceProjection;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.*;
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
import static com.myriad.network.FlameSkillViewPayload.*;

/** Actual production skin, blade, hand curves and VFX. CPU preview, not a game screenshot. */
public final class FenjueMajorPreview {
    private static final Map<Integer,BufferedImage> textures=new HashMap<>();
    private static List<NightDaggerPreview.Quad> blade;private static final List<NightDaggerPreview.Quad> terrain=new ArrayList<>();
    private static FlameDaoArmModel.Skin main,off;
    private static final List<NightDaggerPreview.Quad> dummy=new ArrayList<>();
    private static boolean closeWorld;
    private record Vertex(Vector3f p,float u,float v){Vertex mix(Vertex b,float t){return new Vertex(new Vector3f(p).lerp(b.p,t),u+(b.u-u)*t,v+(b.v-v)*t);}}
    private static Vector3f vec(Vec3 p){return new Vector3f((float)p.x,(float)p.y,(float)p.z);}
    private static FlameSkillMotion.Frame pose(int role,double tick){
        var m=new FlameSkillMotion();
        if(role==0){if(tick<7)return FlameSkillMotion.aim(tick);
            m.accept(new FlameSkillViewPayload(CAGE_FIRE,10,100,1),7,0,FlameDaoMotion.IDLE,true);}
        if(role==1){m.accept(new FlameSkillViewPayload(BURST_READY,13,100,1),0,0,FlameDaoMotion.IDLE,false);
            if(tick>=13)m.accept(new FlameSkillViewPayload(BURST_FIRE,10,113,1),13,0,FlameDaoMotion.IDLE,false);}
        if(role==2){m.accept(new FlameSkillViewPayload(SEA_FLIGHT,32,100,1),0,0,FlameDaoMotion.IDLE,false);
            if(tick>=10)m.vertical(10,-.15);if(tick>=18)m.accept(new FlameSkillViewPayload(SEA_LAND,10,118,1),18,0,FlameDaoMotion.IDLE,false);}
        if(role==3)m.accept(new FlameSkillViewPayload(ARMOR,18,100,1),0,0,FlameDaoMotion.IDLE,false);
        return m.frame(tick);
    }
    private static BufferedImage draw(int role,double tick,boolean world){
        var image=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(75,109,142),0,360,new Color(106,129,94)));g.fillRect(0,0,640,360);g.dispose();
        var depth=new float[640*360];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        var view=world?new Matrix4f().lookAt(closeWorld?new Vector3f(4,3.8f,4):new Vector3f(9,7,10),new Vector3f(0,.3f,0),new Vector3f(0,1,0))
                :new Matrix4f().lookAt(new Vector3f(0,1.62f,-2),new Vector3f(0,1.62f,0),new Vector3f(0,1,0));
        NightDaggerPreview.submit(image,depth,terrain,view);
        if(world){
            int variant=role+4,kind=role==1?AbilityVfxGeometry.BURST:role==3?AbilityVfxGeometry.SHIELD:AbilityVfxGeometry.SEAL;
            float radius=role==0?3.5f:role==1?6:role==2?5:.65f;int life=kind==AbilityVfxGeometry.BURST?9:80;
            var cue=new AbilityVfxPayload(1,-1,2,variant,kind,new Vec3(0,.055,0),new Vec3(0,.055,1),radius,360,life,100,false);
            var mesh=new ArrayList<Quad>();
            for(var q:AbilityVfxGeometry.build(cue,(float)(kind==AbilityVfxGeometry.BURST?tick%8.9:tick),false)){
                var projected=com.myriad.character.ability.AbilityVfxMaterials.flameGround(q.material())?FlameSurfaceProjection.project(q,v->0,true,.055):q.material()==71&&kind!=AbilityVfxGeometry.SHIELD?FlameSurfaceProjection.project(q,v->0,false,.055):q;
                if(projected!=null)mesh.add(projected);
            }
            mesh.sort(Comparator.comparingDouble(q->view.transformPosition(vec(q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25))).z));
            for(var q:mesh)paint(image,depth,q,view);
        }else{
            var f=pose(role,tick);var hand=FlameDaoRig.hand(f.main(),0,false);main.pose(hand);
            NightDaggerPreview.submit(image,depth,FastTrailPreview.model(main.model()),new Matrix4f(hand).mul(FlameDaoRig.arm(false,false)));
            if(f.showBlade()){
                var matrix=FlameSkillRig.main(f,false).mul(FlameSkillRig.blade(f,false));NightDaggerPreview.submit(image,depth,blade,matrix);
                for(var q:blade)for(var p:q.p())NightDaggerPreview.require(matrix.transformPosition(new Vector3f(p)).z<-.051,"Blade crosses near plane: role="+role+" t="+tick);
            }
            if(f.showOff()){
                var matrix=FlameSkillRig.off(f,false);off.pose(matrix);
                NightDaggerPreview.submit(image,depth,FastTrailPreview.model(off.model()),new Matrix4f(matrix).mul(FlameDaoRig.arm(false,true)));
            }
            for(var q:FlameSkillHandVfx.build(f,100+tick,false))paint(image,depth,q,new Matrix4f());
        }
        g=image.createGraphics();g.setColor(new Color(12,20,29,225));g.fillRect(0,0,640,42);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(255,206,130));
        g.drawString(new String[]{"熔岩囚笼 · 左手选点/前推","焚诀爆发 · 交叉/张开","焚天火海 · 下降蓄势/落地砸","赤焰战甲 · 合十/附火/张开"}[role]+String.format("  %.2fs",tick/20),12,18);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.setColor(new Color(185,201,198));g.drawString(world?"生产网格/原生高清贴图 · CPU离线投影，非游戏截图/光影验收":"实际皮肤/武器/生产手部曲线 · CPU离线预览，非实机录屏",12,35);g.dispose();return image;
    }
    private static void paint(BufferedImage image,float[] depth,Quad q,Matrix4f view){
        var p=new Vector3f[]{view.transformPosition(vec(q.a())),view.transformPosition(vec(q.b())),view.transformPosition(vec(q.c())),view.transformPosition(vec(q.d()))};
        var normal=new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0]));if(normal.dot(new Vector3f(p[0]).add(p[1]).add(p[2]).add(p[3]))>=0)return;
        float v0=q.startV(),v1=q.endV();if(q.reverseV()){float swap=v0;v0=v1;v1=swap;}
        var vertices=List.of(new Vertex(p[0],q.startU(),v1),new Vertex(p[1],q.endU(),v1),new Vertex(p[2],q.endU(),v0),new Vertex(p[3],q.startU(),v0));
        for(int[] indices:new int[][]{{0,1,2},{0,2,3}}){
            var clipped=new ArrayList<Vertex>();var input=List.of(vertices.get(indices[0]),vertices.get(indices[1]),vertices.get(indices[2]));
            for(int i=0;i<3;i++){var a=input.get(i);var b=input.get((i+1)%3);boolean ai=a.p.z<-.06f,bi=b.p.z<-.06f;
                if(ai)clipped.add(a);if(ai!=bi)clipped.add(a.mix(b,(-.06f-a.p.z)/(b.p.z-a.p.z)));}
            for(int i=1;i+1<clipped.size();i++){var a=clipped.get(0);var b=clipped.get(i);var c=clipped.get(i+1);
                FastTrailPreview.alphaTriangle(image,depth,new Vector3f[]{a.p,b.p,c.p},new float[]{a.u,a.v,b.u,b.v,c.u,c.v},textures.get(q.material()),q.alpha(),q.color(),0,1,2);}
        }
    }
    private static BufferedImage hit(double tick,boolean critical,boolean periodic,boolean side){
        var image=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setColor(new Color(67,91,106));g.fillRect(0,0,640,360);g.dispose();
        var depth=new float[640*360];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        var view=new Matrix4f().lookAt(side?new Vector3f(2.4f,1.8f,-3):new Vector3f(0,1.5f,-2.7f),new Vector3f(0,1,0),new Vector3f(0,1,0));
        NightDaggerPreview.submit(image,depth,terrain,view);NightDaggerPreview.submit(image,depth,dummy,view);
        var p=com.myriad.character.skills.fenjue.rework.FlameHitVisuals.recipe(1,new Vec3(0,1,-.335),new Vec3(0,0,1),critical,periodic,100);
        var mesh=new ArrayList<>(AbilityVfxGeometry.build(p,(float)tick,false));
        mesh.sort(Comparator.comparingDouble(q->view.transformPosition(vec(q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25))).z));
        for(var q:mesh)paint(image,depth,q,view);
        g=image.createGraphics();g.setColor(new Color(12,20,29,230));g.fillRect(0,0,640,42);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(255,211,132));
        g.drawString("焚诀专属命中 · "+(periodic?"灼烧余焰":critical?"暴击焰芯":"普通命中")+String.format("  %.2fs",tick/20),12,18);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.setColor(new Color(190,204,203));
        g.drawString("生产网格/贴图 · 1.8格目标柱作尺度参照 · CPU预览，非游戏截图",12,35);g.dispose();return image;
    }
    public static void main(String[] args)throws Exception{
        boolean flowOnly=args.length>2&&args[2].equals("--ground-flow");
        closeWorld=flowOnly||args.length>2&&args[2].equals("--world-only");
        var project=Path.of(args[0]);var out=Path.of(args[1]);Files.createDirectories(out);var assets=project.resolve("src/main/resources/assets/myriad");
        NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/fenjue_flame_changdao.png").toFile());blade=NightDaggerPreview.loadBlade(assets,"lieyanjian");
        textures.put(71,ImageIO.read(assets.resolve("textures/particle/vfx/fenjue_hd_fire.png").toFile()));
        textures.put(72,ImageIO.read(assets.resolve("textures/particle/vfx/fenjue_ground_ring.png").toFile()));
        textures.put(73,ImageIO.read(assets.resolve("textures/particle/vfx/fenjue_ground_field.png").toFile()));
        textures.put(74,ImageIO.read(assets.resolve("textures/particle/vfx/fenjue_hit.png").toFile()));
        textures.put(69,ImageIO.read(assets.resolve("textures/particle/vfx/fenjue_pixel_embers.png").toFile()));
        textures.put(68,ImageIO.read(assets.resolve("textures/particle/vfx/fenjue_pixel_stream.png").toFile()));
        try(var zip=new ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
            var grass=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/block/moss_block.png")));var stone=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/block/stone_bricks.png")));
            textures.put(0,ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/particle/glow.png"))));
            for(int x=-8;x<=8;x++)for(int z=-7;z<=10;z++)terrain.add(new NightDaggerPreview.Quad(new Vector3f[]{new Vector3f(x,0,z),new Vector3f(x,0,z+1),new Vector3f(x+1,0,z+1),new Vector3f(x+1,0,z)},new float[]{0,0,1,0,1,1,0,1},Math.abs(x)<2?stone:grass));
            var corners=new Vector3f[]{new Vector3f(-.3f,0,-.3f),new Vector3f(.3f,0,-.3f),new Vector3f(.3f,1.8f,-.3f),new Vector3f(-.3f,1.8f,-.3f),
                new Vector3f(-.3f,0,.3f),new Vector3f(.3f,0,.3f),new Vector3f(.3f,1.8f,.3f),new Vector3f(-.3f,1.8f,.3f)};
            for(int[] face:new int[][]{{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}})
                dummy.add(new NightDaggerPreview.Quad(new Vector3f[]{corners[face[0]],corners[face[1]],corners[face[2]],corners[face[3]]},new float[]{0,1,0,0,1,0,1,1},stone));
        }
        if(args.length>2&&args[2].equals("--hit-flow")){
            var contact=new BufferedImage(2560,1080,BufferedImage.TYPE_INT_RGB);var g=contact.createGraphics();double[] ages={0,2.5,4.5,7};
            for(int role=0;role<3;role++)for(int i=0;i<4;i++)g.drawImage(hit(ages[i],role==1,role==2,false),i*640,role*360,null);
            g.dispose();ImageIO.write(contact,"png",out.resolve("hit_contact_phases.png").toFile());
            for(boolean side:new boolean[]{false,true}){var frames=new ArrayList<BufferedImage>();for(int i=0;i<22;i++)frames.add(hit(i*.8,false,false,side));
                NightDaggerPreview.gif(frames,out.resolve(side?"hit_oblique.gif":"hit_front.gif"));}
            System.out.println("[MYRIAD-FLAME-HIT-PREVIEW] PASS: actual contact mesh/atlas, ordinary/critical/periodic phases and two real-time-speed projections against terrain/target; CPU only");return;
        }
        main=FlameDaoArmModel.bake(false,false);off=FlameDaoArmModel.bake(false,true);
        var worlds=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);var wg=worlds.createGraphics();
        if(closeWorld){
            for(int role=0;role<4;role++)wg.drawImage(draw(role,role==1?3:18,true),role%2*640,role/2*360,null);
            wg.dispose();ImageIO.write(worlds,"png",out.resolve("fissure_close_worlds.png").toFile());
            if(flowOnly){
                var phases=new BufferedImage(2560,1440,BufferedImage.TYPE_INT_RGB);var pg=phases.createGraphics();
                for(int role=0;role<4;role++)for(int phase=0;phase<4;phase++)pg.drawImage(draw(role,role==1?1+phase*2:8+phase*4,true),phase*640,role*360,null);
                pg.dispose();ImageIO.write(phases,"png",out.resolve("ground_flow_phases.png").toFile());
                System.out.println("[MYRIAD-GROUND-FLOW-PREVIEW] PASS: four skills x four actual-mesh phases on grass and stone; native eight-frame alpha decals, CPU only");
            }
            System.out.println("[MYRIAD-FISSURE-PREVIEW] PASS: actual single-surface production geometry in close grass/stone scene; sorted alpha without depth writes, CPU only");return;
        }
        double[][] samples={{1,4,6.8,8,10,14},{1,5,11,13.5,16,20},{2,8,11,15,19,23},{1,5,8,11,14,17}};
        for(int role=0;role<4;role++){
            var sheet=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
            for(int i=0;i<6;i++)g.drawImage(draw(role,samples[role][i],false),i%3*640,i/3*360,null);g.dispose();ImageIO.write(sheet,"png",out.resolve("hands_"+role+".png").toFile());
            var frames=new ArrayList<BufferedImage>();for(int i=0;i<38;i++)frames.add(draw(role,i*.8,false));NightDaggerPreview.gif(frames,out.resolve("hands_"+role+".gif"));
            wg.drawImage(draw(role,role==1?3:18,true),role%2*640,role/2*360,null);
        }
        wg.dispose();ImageIO.write(worlds,"png",out.resolve("major_worlds.png").toFile());
        System.out.println("[MYRIAD-FENJUE-MAJOR-PREVIEW] PASS: actual skin/dao/hand transforms, 4 gestures, descent/landing stages, near-plane checked, native fire and grounded scene; CPU only");
    }
}
