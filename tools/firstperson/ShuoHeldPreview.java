package com.myriad.tools;
import com.mojang.blaze3d.vertex.PoseStack;
import com.myriad.client.firstperson.ShuoHeldView;
import com.myriad.character.skills.shuo.ShuoWeaponState;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
/** Original vanilla player/weapon meshes and production hand transforms. NOT a live game screenshot. */
public final class ShuoHeldPreview {
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void main(String[] args)throws Exception{
        var assets=Path.of(args[0]).resolve("src/main/resources/assets/myriad");var out=Path.of(args[1]);
        NightDaggerPreview.dagger=ImageIO.read(assets.resolve("textures/item/shuo_crescent.png").toFile());
        var blade=NightDaggerPreview.loadBlade(assets,"shuo_sword");var sheath=NightDaggerPreview.loadBlade(assets,"shuo_scabbard");
        net.minecraft.client.resources.model.cuboid.ItemTransforms transforms;
        try(var reader=Files.newBufferedReader(assets.resolve("models/item/shuo_sword.json"))){transforms=CuboidModel.fromStream(reader).transforms();}
        try(var zip=new java.util.zip.ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            NightDaggerPreview.skin=ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
            var bytes=zip.getInputStream(zip.getEntry("net/minecraft/client/renderer/entity/layers/ItemInHandLayer.class")).readAllBytes();
            var node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(node,0);
            NightDaggerPreview.require(node.methods.stream().filter(m->m.name.equals("submitArmWithItem")&&m.desc.equals("(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")).count()==1,"Actual MC26.2 client matches the cancellable HEAD injection ABI");
        }
        var sheet=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);var graphics=sheet.createGraphics();
        for(int i=0;i<6;i++){
            var model=new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,i>=3),64,64).bakeRoot(),i>=3);
            var state=new AvatarRenderState();model.leftArm.xRot=i%3==1?.55f:0;model.leftArm.zRot=i%3==2?.15f:0;
            var hand=new PoseStack();ShuoHeldView.leftHand((ArmedModel)model,state,hand,transforms);
            var sword=new Matrix4f(hand.last().pose());var scabbard=new Matrix4f(sword).translate(0,ShuoWeaponState.GRIP_TO_MOUTH,0);
            var root=sword.transformPosition(new Vector3f(0,ShuoWeaponState.GRIP_TO_MOUTH,0));
            NightDaggerPreview.require(root.distance(scabbard.transformPosition(new Vector3f()))<1E-6,"Blade root remains at real sheath mouth in walking/slim poses");
            var body=new ArrayList<NightDaggerPreview.Quad>();model.root().visit(new PoseStack(),(pose,path,index,cube)->{
                for(var face:cube.polygons){var vertices=new Vector3f[4];var uv=new float[8];for(int j=0;j<4;j++){
                    var v=face.vertices()[j];vertices[j]=pose.pose().transformPosition(new Vector3f(v.worldX(),v.worldY(),v.worldZ()));uv[j*2]=v.u();uv[j*2+1]=v.v();}
                    body.add(new NightDaggerPreview.Quad(vertices,uv,NightDaggerPreview.skin));}
            });
            var image=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(new Color(51,68,77));g.fillRect(0,0,640,360);g.dispose();
            var depth=new float[640*360];Arrays.fill(depth,Float.NEGATIVE_INFINITY);
            var camera=new Matrix4f().translate(0,.65f,-2.8f).rotateX((float)Math.PI).rotateY((i%3-1)*.6f);
            NightDaggerPreview.submit(image,depth,body,camera);
            NightDaggerPreview.submit(image,depth,blade,new Matrix4f(camera).mul(sword));
            NightDaggerPreview.submit(image,depth,sheath,new Matrix4f(camera).mul(scabbard));
            g=image.createGraphics();g.setColor(Color.WHITE);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,14));g.drawString("第三人称 · 左手持入鞘整刀 / "+(i>=3?"细臂":"宽臂"),10,20);
            g.setFont(new Font("Microsoft YaHei",Font.PLAIN,11));g.drawString("原版玩家与原生武器网格 / 生产左手矩阵 · 离线投影，非游戏截图",10,38);g.dispose();
            graphics.drawImage(image,(i%3)*640,(i/3)*360,null);
        }
        graphics.dispose();ImageIO.write(sheet,"png",out.resolve("shuo_closed_left_hand.png").toFile());
        System.out.println("[MYRIAD-SHUO-HELD-PREVIEW] PASS: actual client injection ABI, six real player/weapon mesh views, moving/slim left-hand anchors; offline only, not live acceptance.");
    }
}
