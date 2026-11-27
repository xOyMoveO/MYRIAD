package com.myriad.tools;

import com.myriad.client.firstperson.FlameDaoMotion;
import com.myriad.client.firstperson.FlameDaoRig;
import com.myriad.client.firstperson.NightDaggerArmModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static com.myriad.tools.NightDaggerPreview.require;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual exported item + real vanilla skin arm + production rig; offline perspective, NOT game capture. */
public final class FlameDaoPreview {
    private static List<NightDaggerPreview.Quad> wide, slimLeft;

    private static List<ModelPart.Cube> cubes(ModelPart model, boolean sleeve) {
        var result = new ArrayList<ModelPart.Cube>();
        model.visit(new PoseStack(), (p, path, index, cube) -> {
            if (sleeve == !path.isEmpty()) result.add(cube);
        });
        return result;
    }

    private record Section(Vector3f proximal, Vector3f wrist, Vector3f fist) {}

    private static Section verifyLayer(ModelPart.Cube vanilla, List<ModelPart.Cube> segments) {
        require(segments.size() == 2, "Skin and sleeve each use a straight fist and forearm");
        var fist = segments.get(0);
        var forearm = segments.get(1);
        float bottom = Float.POSITIVE_INFINITY;
        float top = Float.NEGATIVE_INFINITY;
        for (int face = 0; face < vanilla.polygons.length; face++) {
            var original = vanilla.polygons[face];
            require(fist.polygons[face].normal().isFinite() && forearm.polygons[face].normal().isFinite(),
                    "Skin normals remain finite");
            if (Math.abs(original.normal().y()) > .5f) continue;
            for (int i = 0; i < original.vertices().length; i++) {
                var v = original.vertices()[i];
                var other = Arrays.stream(original.vertices())
                        .filter(o -> o.x() == v.x() && o.z() == v.z() && o.y() != v.y())
                        .findFirst().orElseThrow();
                var outer = (v.y() < 6 ? forearm : fist).polygons[face].vertices()[i];
                var seam = (v.y() < 6 ? fist : forearm).polygons[face].vertices()[i];
                float t = (6-v.y()) / (other.y()-v.y());
                require(outer.x() == v.x() && outer.z() == v.z() &&
                        seam.x() == v.x() && seam.z() == v.z() && seam.y() == 6,
                        "Skin boundaries meet on the same straight section");
                require(outer.u() == v.u() && outer.v() == v.v() &&
                        Math.abs(seam.u() - (v.u() + t*(other.u()-v.u()))) < 1e-6 &&
                        Math.abs(seam.v() - (v.v() + t*(other.v()-v.v()))) < 1e-6,
                        "Skin and sleeve retain the vanilla UV at both ends and the wrist seam");
                if (v.y() < 6) {
                    require(outer.y() < v.y(), "Proximal continuation extends past the vanilla arm");
                    if (Float.isFinite(bottom)) require(outer.y() == bottom, "Proximal section is planar");
                    bottom = outer.y();
                } else {
                    require(outer.y() == v.y(), "Rigid fist retains the vanilla dimensions");
                    top = outer.y();
                }
            }
        }
        require(Float.isFinite(bottom) && top > 8, "Skin layer contains wrist and fist sections");
        float x = (vanilla.minX + vanilla.maxX) / 32;
        float z = (vanilla.minZ + vanilla.maxZ) / 32;
        return new Section(new Vector3f(x,bottom/16,z), new Vector3f(x,6f/16,z), new Vector3f(x,top/16,z));
    }

    private static void verifySkin() {
        int poses = 0;
        for (boolean slim : new boolean[]{false,true}) for (boolean left : new boolean[]{false,true}) {
            String side = left ? "left_arm" : "right_arm";
            var arm = NightDaggerArmModel.bake(slim).getChild(side);
            var vanilla = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64)
                    .bakeRoot().getChild(side);
            var skin = verifyLayer(cubes(vanilla,false).getFirst(), cubes(arm,false));
            var sleeve = verifyLayer(cubes(vanilla,true).getFirst(), cubes(arm,true));
            var palm = new Vector3f(skin.wrist().x,8f/16,skin.wrist().z);
            var mesh = NightDaggerPreview.arm(slim,left);
            var motion = new FlameDaoMotion();
            for (int cut=0; cut<3; cut++) {
                double start = cut*22;
                motion.attack(start,20);
                for (int n=0; n<=40; n++) for (float equip : new float[]{0,1}) {
                    var frame = motion.frame(start+motion.duration()*n/40.0);
                    var matrix = FlameDaoRig.hand(frame.pose(),equip,left).mul(FlameDaoRig.arm(slim,left));
                    var grip = FlameDaoRig.wrist(frame.pose(),equip,left).mul(FlameDaoRig.blade(frame,left))
                            .getTranslation(new Vector3f());
                    require(matrix.transformPosition(new Vector3f(palm)).distance(grip) < 1e-5,
                            "Physical palm and weapon share the grip");
                    for (var section : List.of(skin,sleeve)) {
                        var wrist = matrix.transformPosition(new Vector3f(section.wrist()));
                        var lower = matrix.transformPosition(new Vector3f(section.proximal()));
                        var upper = matrix.transformPosition(new Vector3f(section.fist()));
                        var forearmAxis = wrist.sub(lower,new Vector3f()).normalize();
                        var fistAxis = upper.sub(wrist,new Vector3f()).normalize();
                        require(forearmAxis.dot(fistAxis) > .99999f, "Fist and forearm share a straight axis");
                    }
                    float sleeveEnd = sleeve.proximal().y;
                    float skinEnd = skin.proximal().y;
                    for (var quad : mesh) for (var vertex : quad.p()) {
                        var p = matrix.transformPosition(new Vector3f(vertex));
                        require(p.isFinite() && p.z < -.051, "Arm mesh clears the near plane: "+p);
                        if (Math.abs(vertex.y-skinEnd) < 1e-5 || Math.abs(vertex.y-sleeveEnd) < 1e-5) {
                            require(p.y/-p.z < -Math.tan(Math.toRadians(55)),
                                    "Proximal skin and sleeve caps stay below a 110-degree view: "+p);
                        }
                    }
                    poses++;
                }
            }
        }
        System.out.println("[MYRIAD-FLAME-SKIN] PASS: "+poses+
                " wide/slim, left/right, equipped/lowered pose samples; grip, skin/sleeve UVs, straight sections and near plane");
    }

    private static void verifyBlade() {
        var motion = new FlameDaoMotion();
        for (int cut=0; cut<3; cut++) {
            double start=cut*22;
            motion.attack(start,20);
            for (int i=0; i<=200; i++) for (boolean left : new boolean[]{false,true}) {
                var f=motion.frame(start+motion.duration()*i/200.0);
                var matrix=FlameDaoRig.wrist(f.pose(),0,left).mul(FlameDaoRig.blade(f,left));
                for (var q : NightDaggerPreview.blade) for (var v : q.p()) {
                    var p=matrix.transformPosition(new Vector3f(v));
                    require(p.isFinite() && p.z < -.051, "Actual dao clears the near plane: "+cut+" "+i+" "+p);
                }
            }
        }
        System.out.println("[MYRIAD-FLAME-BLADE] PASS: 1206 near-plane samples of the exported blade");
    }

    private static BufferedImage draw(FlameDaoMotion.Frame f,boolean left,String title){
        int w=NightDaggerPreview.W,h=NightDaggerPreview.H;
        var im=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=im.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(29,44,61),0,h,new Color(69,91,85)));g.fillRect(0,0,w,h);
        g.setColor(new Color(180,200,195));g.drawLine(w/2-5,h/2,w/2+5,h/2);g.drawLine(w/2,h/2-5,w/2,h/2+5);g.dispose();
        float[] depth=new float[w*h];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        var wrist=FlameDaoRig.wrist(f.pose(),0,left);
        var skin=left?slimLeft:wide;
        NightDaggerPreview.submit(im,depth,skin,FlameDaoRig.hand(f.pose(),0,left).mul(FlameDaoRig.arm(left,left)));
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
        Path project=Path.of(args[0]);
        Path assets=project.resolve("src/main/resources/assets/myriad");
        if (args.length == 2 && args[1].equals("--geometry-only")) {
            verifySkin();
            NightDaggerPreview.blade=NightDaggerPreview.loadBlade(assets,"lieyanjian");
            verifyBlade();
            return;
        }
        Path out=Path.of(args[1]);
        Files.createDirectories(out);
        NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/fenjue_flame_changdao.png").toFile());
        NightDaggerPreview.blade=NightDaggerPreview.loadBlade(assets,"lieyanjian");
        try(var zip=MinecraftPreviewResources.open()){
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
        }
        verifySkin();
        verifyBlade();
        wide=NightDaggerPreview.arm(false,false);slimLeft=NightDaggerPreview.arm(true,true);
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
        }
        g.dispose();ImageIO.write(sheet,"png",out.resolve("fenjue_three_cuts.png").toFile());
        NightDaggerPreview.gif(frames,out.resolve("fenjue_three_cuts.gif"));
        System.out.println("[MYRIAD-FLAME-PREVIEW] PASS: real 423-cube item/UV and skin arm; central-clear idle; 1206 actual-mesh near-plane scans; 84-frame three-cut GIF. Offline only.");
    }
}
