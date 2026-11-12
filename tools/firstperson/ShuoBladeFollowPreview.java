package com.myriad.tools;
import com.myriad.client.firstperson.*;
import com.myriad.network.ShuoViewPayload;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.world.phys.Vec3;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.util.*;
/** Exact first-person motion samples, packaged edge path and authored HD textures; NOT a game capture. */
public final class ShuoBladeFollowPreview {
    private static BufferedImage frame(int action,boolean drawn,boolean mirror,double t,String title){
        var motion=new ShuoMotion();motion.accept(new ShuoViewPayload(action,10,100,1),100,0,drawn);double now=100+t*10;
        var image=ShuoPreview.draw(motion.frame(now,drawn),drawn,mirror,title);
        ShuoSkillTrail.build(motion,now,mirror).stream().sorted(Comparator.comparingDouble(q->(q.a().z+q.b().z+q.c().z+q.d().z)/4)).forEach(q->{
            if(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).z<=0)return;
            float v0=q.startV(),v1=q.endV();if(q.reverseV()){float k=v0;v0=v1;v1=k;}
            float[] uv={q.startU(),v1,q.endU(),v1,q.endU(),v0,q.startU(),v0};var v=new Vec3[]{q.a(),q.b(),q.c(),q.d()};
            ShuoVfxPreview.triangle(image,q,v,uv,0,1,2);ShuoVfxPreview.triangle(image,q,v,uv,0,2,3);
        });return image;
    }
    public static void main(String[] args)throws Exception{
        ShuoPreview.main(args);Path assets=Path.of(args[0]).resolve("src/main/resources/assets/myriad"),out=Path.of(args[1]);
        ShuoVfxPreview.materials.put(75,ImageIO.read(assets.resolve("textures/particle/vfx/shuo_basic_hd.png").toFile()));
        ShuoVfxPreview.materials.put(77,ImageIO.read(assets.resolve("textures/particle/vfx/shuo_stream_hd.png").toFile()));
        try(var zip=new java.util.zip.ZipFile("E:/mod/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar")){
            try(var in=zip.getInputStream(zip.getEntry("net/minecraft/client/renderer/entity/layers/ItemInHandLayer.class"))){
                var node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(in).accept(node,0);
                var methods=node.methods.stream().filter(m->m.name.equals("submitArmWithItem")).toList();
                NightDaggerPreview.require(methods.size()==1,"One unambiguous current-client held-item hook target");
                int matches=0;for(var op:methods.getFirst().instructions)if(op instanceof org.objectweb.asm.tree.MethodInsnNode m
                    &&m.owner.equals("net/minecraft/client/renderer/item/ItemStackRenderState")&&m.name.equals("submit")
                    &&m.desc.equals("(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))matches++;
                NightDaggerPreview.require(matches==1,"Exact injection target descriptor exists once in real client bytecode");
            }
            var atlas=com.google.gson.JsonParser.parseString(Files.readString(assets.resolve("particles/ability_mesh.json"))).getAsJsonObject().getAsJsonArray("textures");
            String name=atlas.get(0).getAsString().split(":")[1];ShuoVfxPreview.materials.put(0,ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/particle/"+name+".png"))));
        }
        int[] actions={9,7,3,7,5,5,6,7,5,8,6,8};
        String[] names={"纳刀 · 鞘口流光","纳刀 · 斩击退步","鞘返 · 横鞘流光","流刃 · 贴刃刀光","一闪 · 拔刀流光","追风 · 贴刃突进","断流 · 贴刃宽斩","回澜 · 反向贴刃","无声一线 · 拔刀穿行","断空 · 终点下斩","残月 · 三式贴刃","回月 · 终斩"};
        var sheet=new BufferedImage(1280,2160,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();
        for(int i=0;i<12;i++)g.drawImage(frame(actions[i],i%2==1,false,.46,names[i]+" / 离线投影"),i%2*640,i/2*360,null);
        g.dispose();ImageIO.write(sheet,"png",out.resolve("shuo_skill_blade_12_forms.png").toFile());
        var animation=new ArrayList<BufferedImage>();for(int a:new int[]{2,6,7,8,1})for(int n=0;n<18;n++)animation.add(frame(a,a!=2,false,(n+.2)/18,"技能贴刃流光 / 实际模型采样 / 非游戏截图"));
        NightDaggerPreview.gif(animation,out.resolve("shuo_skill_blade_flow.gif"));
        ImageIO.write(frame(7,true,true,.46,"左惯用手 / 反向斩击贴刃 / 非游戏截图"),"png",out.resolve("shuo_skill_blade_mirrored.png").toFile());
        System.out.println("[MYRIAD-SHUO-BLADE-PREVIEW] PASS: actual blade/motion/entry-blend samples, twelve forms and mirrored grip; offline alpha composites, NOT live client acceptance.");
    }
}
