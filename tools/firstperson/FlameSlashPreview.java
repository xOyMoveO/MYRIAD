package com.myriad.tools;

import com.myriad.client.firstperson.*;
import com.myriad.character.skills.fenjue.rework.FlameCrossSlashVfx;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static com.myriad.character.ability.AbilityVfxGeometry.ARC;

/** Production poses and VFX, with UV-aware near-plane clipping. Not Minecraft footage. */
public final class FlameSlashPreview {
    private static List<NightDaggerPreview.Quad> blade;
    private static FlameDaoArmModel.Skin arm;
    private static BufferedImage texture,core,emberTexture;
    private static List<NightDaggerPreview.Quad> standing;
    private static final List<NightDaggerPreview.Quad> terrain=new ArrayList<>();
    private static final FlameSlashMotion motion=new FlameSlashMotion();
    private static final AbilityVfxPayload cue=com.myriad.character.skills.fenjue.rework.FlameSlashAim.recipe(1,new net.minecraft.world.phys.Vec3(0,1.62,0),new net.minecraft.world.phys.Vec3(0,0,1),0,100);
    private record Vertex(Vector3f p,float u,float v){Vertex mix(Vertex b,float t){return new Vertex(new Vector3f(p).lerp(b.p,t),u+(b.u-u)*t,v+(b.v-v)*t);}}
    private static BufferedImage draw(double tick,boolean observer){
        return draw(tick,observer,cue);
    }
    private static BufferedImage draw(double tick,boolean observer,AbilityVfxPayload recipe){
        return draw(tick,observer,recipe,false);
    }
    private static BufferedImage draw(double tick,boolean observer,AbilityVfxPayload recipe,boolean sideView){
        var im=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(26,39,53),0,360,new Color(67,86,79)));g.fillRect(0,0,640,360);g.dispose();
        var depth=new float[640*360];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        var basis=com.myriad.character.skills.fenjue.rework.FlameSlashAim.basis(recipe.to().subtract(recipe.from()),recipe.angle());
        var target=sideView?recipe.from().add(basis.forward().scale(4)).add(basis.up().scale(FlameCrossSlashVfx.LIFT)):observer?recipe.from().add(basis.forward().scale(3)):recipe.to();
        var camera=sideView?target.add(basis.side().scale(8)):observer?recipe.from().add(basis.side().scale(-5)).add(basis.up().scale(3)).subtract(basis.forward().scale(5)):recipe.from();
        var view=new Matrix4f().lookAt(vec(camera),vec(target),vec(basis.up()));
        NightDaggerPreview.submit(im,depth,terrain,view);
        if(observer){
            NightDaggerPreview.submit(im,depth,standing,new Matrix4f(view).translate(0,1.35f,0).scale(.9f,-.9f,-.9f));
            NightDaggerPreview.submit(im,depth,standing,new Matrix4f(view).translate(0,1.35f,4.5f).scale(.9f,-.9f,-.9f));
        }
        if(!observer){
            var f=motion.frame(tick);arm.pose(f.pose(),0);
            NightDaggerPreview.submit(im,depth,FastTrailPreview.model(arm.model()),FlameDaoRig.hand(f.pose(),0,false).mul(FlameDaoRig.arm(false,false)));
            var matrix=FlameDaoRig.wrist(f.pose(),0,false).mul(FlameDaoRig.blade(f,false));
            NightDaggerPreview.submit(im,depth,blade,matrix);
            for(var q:blade)for(var p:q.p())NightDaggerPreview.require(matrix.transformPosition(new Vector3f(p)).z<-.051,"Actual skill blade crosses near plane");
        }
        var quads=FlameCrossSlashVfx.build(recipe,(float)tick,false).stream().sorted(Comparator.comparingDouble(q->{
            var p=new Vector3f((float)q.a().x,(float)q.a().y,(float)q.a().z);return view.transformPosition(p).z;})).toList();
        for(var q:quads)paint(im,depth,q,view);
        g=im.createGraphics();g.setColor(new Color(9,17,28,220));g.fillRect(0,0,640,46);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(new Color(255,208,139));
        g.drawString("火焰斩 · "+(sideView?"侧视 / 月牙仅向前凸出":observer?"旁观角度 / 清晰薄刃":"第一人称 / XY正面刃线")+String.format(" · %.2fs · 俯仰%.0f°",tick/20,-Math.toDegrees(Math.asin(basis.forward().y))),12,20);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.setColor(new Color(166,190,190));
        g.drawString("实际技能网格/贴图 · 人形约1.8格身高参照 · 离线投影，非实机光影",12,38);g.dispose();return im;
    }
    private static Vector3f vec(net.minecraft.world.phys.Vec3 p){return new Vector3f((float)p.x,(float)p.y,(float)p.z);}
    /** Look straight onto the actual slash plane, with its forward convex axis drawn upwards. */
    private static BufferedImage referenceArc(){
        var im=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();
        g.setColor(new Color(16,25,34));g.fillRect(0,0,640,360);
        g.setColor(new Color(43,58,68));g.drawOval(211,71,218,218);g.drawLine(190,180,450,180);g.drawLine(320,55,320,310);g.dispose();
        var basis=com.myriad.character.skills.fenjue.rework.FlameSlashAim.basis(cue.to().subtract(cue.from()),cue.angle());
        double a=Math.toRadians(FlameCrossSlashVfx.CUT_ANGLE);
        var d=basis.side().scale(Math.cos(a)).add(basis.up().scale(Math.sin(a)));var f=basis.forward();
        var center=FlameCrossSlashVfx.center(cue,0,3.8);
        var view=new Matrix4f().lookAt(vec(center.add(d.cross(f).scale(10))),vec(center),vec(f));
        var depth=new float[640*360];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        for(var q:FlameCrossSlashVfx.build(cue,3.8f,false))paint(im,depth,q,view);
        g=im.createGraphics();g.setFont(new Font("Microsoft YaHei",Font.PLAIN,16));g.setColor(new Color(255,208,139));
        g.drawString("刀光平面展开 · 描红段：0°～120°上圆弧",18,28);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,12));g.setColor(new Color(166,190,190));
        g.drawString("实际生产网格与贴图 · 灰圆仅供轮廓对照，不会出现在游戏中",18,337);g.dispose();return im;
    }
    private static void tile(BufferedImage tex,float... coordinates){
        var points=new Vector3f[4];for(int i=0;i<4;i++)points[i]=new Vector3f(coordinates[i*3],coordinates[i*3+1],coordinates[i*3+2]);
        terrain.add(new NightDaggerPreview.Quad(points,new float[]{0,0,1,0,1,1,0,1},tex));
    }
    private static void paint(BufferedImage im,float[] depth,Quad q,Matrix4f view){
        var points=new net.minecraft.world.phys.Vec3[]{q.a(),q.b(),q.c(),q.d()};var p=new Vector3f[4];
        for(int i=0;i<4;i++)p[i]=view.transformPosition(new Vector3f((float)points[i].x,(float)points[i].y,(float)points[i].z));
        var normal=new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0]));
        if(normal.dot(new Vector3f(p[0]).add(p[1]).add(p[2]).add(p[3]))>=0)return;
        float v0=q.startV(),v1=q.endV();if(q.reverseV()){float swap=v0;v0=v1;v1=swap;}
        var vertices=List.of(new Vertex(p[0],q.startU(),v1),new Vertex(p[1],q.endU(),v1),new Vertex(p[2],q.endU(),v0),new Vertex(p[3],q.startU(),v0));
        for(int[] ix:new int[][]{{0,1,2},{0,2,3}}){
            var clipped=new ArrayList<Vertex>();var input=List.of(vertices.get(ix[0]),vertices.get(ix[1]),vertices.get(ix[2]));
            for(int i=0;i<3;i++){
                var a=input.get(i);var b=input.get((i+1)%3);boolean ai=a.p.z<-.06f,bi=b.p.z<-.06f;
                if(ai)clipped.add(a);if(ai!=bi)clipped.add(a.mix(b,(-.06f-a.p.z)/(b.p.z-a.p.z)));
            }
            for(int i=1;i+1<clipped.size();i++){
                var a=clipped.get(0);var b=clipped.get(i);var c=clipped.get(i+1);
                FastTrailPreview.alphaTriangle(im,depth,new Vector3f[]{a.p,b.p,c.p},new float[]{a.u,a.v,b.u,b.v,c.u,c.v},q.material()==0?core:q.material()==69?emberTexture:texture,q.alpha(),q.color(),0,1,2);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        var project=Path.of(args[0]);var out=Path.of(args[1]);Files.createDirectories(out);var assets=project.resolve("src/main/resources/assets/myriad");
        NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/fenjue_flame_changdao.png").toFile());
        blade=NightDaggerPreview.loadBlade(assets,"lieyanjian");texture=ImageIO.read(assets.resolve("textures/particle/vfx/fenjue_crisp_slash.png").toFile());
        emberTexture=ImageIO.read(assets.resolve("textures/particle/vfx/fenjue_pixel_embers.png").toFile());
        try(var zip=new ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            var glow=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/particle/glow.png")));
            core=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);core.setRGB(0,0,glow.getRGB(glow.getWidth()/2,glow.getHeight()/2));
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
            var grass=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/block/grass_block_top.png")));
            var stone=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/block/stone_bricks.png")));
            for(int x=-9;x<10;x++)for(int z=-8;z<14;z++)tile(grass,x,0,z,x,0,z+1,x+1,0,z+1,x+1,0,z);
            for(int x=-5;x<6;x++)for(int y=0;y<4;y++)tile(stone,x,y,8,x+1,y,8,x+1,y+1,8,x,y+1,8);
            for(int y=0;y<3;y++){
                tile(stone,1.7f,y,4.2f,2.7f,y,4.2f,2.7f,y+1,4.2f,1.7f,y+1,4.2f);
                tile(stone,1.7f,y,4.2f,1.7f,y+1,4.2f,1.7f,y+1,5.2f,1.7f,y,5.2f);
                tile(stone,2.7f,y,4.2f,2.7f,y,5.2f,2.7f,y+1,5.2f,2.7f,y+1,4.2f);
            }
            tile(stone,1.7f,3,4.2f,2.7f,3,4.2f,2.7f,3,5.2f,1.7f,3,5.2f);
        }
        var playerRoot=net.minecraft.client.model.geom.builders.LayerDefinition.create(net.minecraft.client.model.player.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE,false),64,64).bakeRoot();
        standing=FastTrailPreview.model(playerRoot);
        arm=FlameDaoArmModel.bake(false,false);motion.accept(cue,0,0,new FlameDaoMotion().frame(0));
        ImageIO.write(referenceArc(),"png",out.resolve("flame_reference_arc.png").toFile());
        var sheet=new BufferedImage(2560,720,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        double[] times={1.6,3,5.2,8.4};
        for(int i=0;i<4;i++){g.drawImage(draw(times[i],false),i*640,0,null);g.drawImage(draw(times[i],true),i*640,360,null);}
        g.dispose();ImageIO.write(sheet,"png",out.resolve("flame_cross_slash.png").toFile());
        var pitchSheet=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);var pg=pitchSheet.createGraphics();
        float[] pitches={-65,0,65};
        for(int i=0;i<3;i++){
            var aimed=com.myriad.character.skills.fenjue.rework.FlameSlashAim.recipe(1,cue.from(),net.minecraft.world.phys.Vec3.directionFromRotation(pitches[i],0),0,100);
            pg.drawImage(draw(4.5,false,aimed),i*640,0,null);pg.drawImage(draw(4.5,true,aimed),i*640,360,null);
        }
        pg.dispose();ImageIO.write(pitchSheet,"png",out.resolve("flame_full_pitch.png").toFile());
        var frames=new ArrayList<BufferedImage>();for(int i=0;i<35;i++)frames.add(draw(i*.8,false));
        NightDaggerPreview.gif(frames,out.resolve("flame_cross_slash.gif"));
        var worldFrames=new ArrayList<BufferedImage>();for(int i=0;i<35;i++)worldFrames.add(draw(i*.8,true));
        NightDaggerPreview.gif(worldFrames,out.resolve("flame_thirdperson_scene.gif"));
        ImageIO.write(draw(4.5,true,cue,true),"png",out.resolve("flame_side_profile.png").toFile());
        var heightPair=new BufferedImage(1280,360,BufferedImage.TYPE_INT_RGB);var hg=heightPair.createGraphics();
        hg.drawImage(draw(3.8,true,cue,true),0,0,null);hg.drawImage(draw(7.4,true,cue,true),640,0,null);
        hg.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));hg.setColor(new Color(255,208,139));
        hg.drawString("第一斩 · 原生120°圆弧",18,345);hg.drawString("第二斩 · 同一身前高度基准",658,345);hg.dispose();
        ImageIO.write(heightPair,"png",out.resolve("flame_player_height.png").toFile());
        var sideFrames=new ArrayList<BufferedImage>();for(int i=0;i<35;i++)sideFrames.add(draw(i*.8,true,cue,true));
        NightDaggerPreview.gif(sideFrames,out.resolve("flame_side_profile.gif"));
        System.out.println("[MYRIAD-FLAME-CROSS-PREVIEW] PASS: actual skill poses/item/skin and crossed animated atlas with near-plane clipping; offline, not live-client acceptance");
    }
}
