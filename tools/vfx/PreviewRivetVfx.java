package com.myriad.tools;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.rivet.RivetVfx;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Rasterises production meshes and packaged texture UVs. All output is offline, never a game capture. */
public final class PreviewRivetVfx {
    private static List<Quad> mesh(int variant,int kind,float radius,float stage,int life,float age,Vec3 from,Vec3 to){
        return AbilityVfxGeometry.build(new AbilityVfxPayload(1,-1,13,variant,kind,from,to,radius,stage,life,100,false),age,false);
    }
    private static PreviewRoleMeshes.Panel panel(String label,int variant,int kind,float stage,int life,float age,double scale){
        return new PreviewRoleMeshes.Panel(label,mesh(variant,kind,.8f,stage,life,age,Vec3.ZERO,new Vec3(0,0,kind==WALL?-1:1)),scale,Vec3.ZERO);
    }
    private static List<PreviewRoleMeshes.Panel> contacts(){
        var out=new ArrayList<PreviewRoleMeshes.Panel>();
        for(int anchored=0;anchored<2;anchored++)for(float age:new float[]{.5f,2.5f,5.5f,10.5f})
            out.add(panel((anchored==0?"撞墙 / 压痕与金属碎片":"锚击 / 压缩→夹爪→锁紧")+" / tick "+age,2,BURST,anchored,14,age,130));
        return out;
    }
    private static List<PreviewRoleMeshes.Panel> hammer(){
        var out=new ArrayList<PreviewRoleMeshes.Panel>();
        for(float progress:new float[]{0,.5f,1})out.add(new PreviewRoleMeshes.Panel("冲击锤 / 服务端起手进度 "+progress,
                mesh(2,CHARGE,3,progress,3,1,Vec3.ZERO,new Vec3(0,0,3)),90,new Vec3(0,1,.9)));
        for(float age:new float[]{.5f,2.5f,5.5f})out.add(new PreviewRoleMeshes.Panel("冲击锤 / 实际挥锤阶段 tick "+age,
                mesh(2,ARC,3,110,7,age,Vec3.ZERO,new Vec3(0,0,3)),70,new Vec3(0,.55,1.5)));
        return out;
    }
    private static List<PreviewRoleMeshes.Panel> anchors(){
        var out=new ArrayList<PreviewRoleMeshes.Panel>();
        for(int stage=0;stage<2;stage++)out.add(panel(stage==0?"战术锚点 / 恢复黄灯":"战术锚点 / 就绪四角青灯",3,WALL,stage,5,1,200));
        for(float age:new float[]{.5f,3.5f})out.add(panel("战术锚点 / 夹爪接合 tick "+age,3,BURST,RivetVfx.ANCHOR_SET,10,age,170));
        return out;
    }
    private static List<PreviewRoleMeshes.Panel> cable(){
        var out=new ArrayList<PreviewRoleMeshes.Panel>();Vec3 start=new Vec3(-1.8,.1,0),end=new Vec3(1.8,1.0,.6);
        for(float age:new float[]{.5f,2})out.add(new PreviewRoleMeshes.Panel("牵引索 / 双股金属与受力游标 "+age,
                mesh(5,BEAM,.045f,0,3,age,start,end),95,new Vec3(0,.4,.3)));
        for(float age:new float[]{.5f,2.5f,5})out.add(new PreviewRoleMeshes.Panel("松索 / 真实终点回收 tick "+age,
                mesh(5,BEAM,.045f,1,6,age,start,end),95,new Vec3(0,.4,.3)));
        return out;
    }
    private static List<PreviewRoleMeshes.Panel> branches(){
        var out=new ArrayList<PreviewRoleMeshes.Panel>();
        for(int variant:new int[]{0,4,6})out.add(new PreviewRoleMeshes.Panel(new String[]{"驻地装甲","应急撑架","固守协议"}[variant==0?0:variant==4?1:2]+" / 独立护板结构",
                mesh(variant,SHIELD,.75f,0,12,3,Vec3.ZERO,new Vec3(0,0,1)),125,new Vec3(0,.55,0)));
        out.add(panel("破阵节拍 / 实际强化命中",1,BURST,RivetVfx.RHYTHM_HIT,8,2,150));
        for(float stage:new float[]{0,8,60,112})out.add(new PreviewRoleMeshes.Panel("固守协议 / 固定4格球形边界 tick "+stage,
                mesh(6,SEAL,4,stage,6,1,Vec3.ZERO,new Vec3(0,1,0)),31,Vec3.ZERO));
        for(float progress:new float[]{.2f,.9f})out.add(new PreviewRoleMeshes.Panel("破城冲击 / 分段压缩预警 "+progress,
                mesh(7,CHARGE,.7f,progress,3,1,Vec3.ZERO,new Vec3(0,0,5)),50,new Vec3(0,.4,2)));
        out.add(new PreviewRoleMeshes.Panel("破城冲击 / 双轨实际运动残迹",
                mesh(7,TRAIL,.8f,0,7,2,new Vec3(0,.3,-1),new Vec3(0,.3,2)),95,new Vec3(0,.3,.5)));
        out.add(panel("破城冲击 / 首敌实体接触",7,BURST,RivetVfx.CHARGE_HIT,10,2,120));
        return out;
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);PreviewRoleMeshes.loadMaterials(Path.of("src/main/resources"));
        for(boolean dark:new boolean[]{true,false}){
            String suffix=dark?"dark":"grass";
            PreviewRoleMeshes.png(out.resolve("rivet_contact_stages_"+suffix+".png"),PreviewRoleMeshes.board("铆钉 / 撞墙与锚击分阶段 / 同一生产网格，非游戏截图",contacts(),4,dark));
            PreviewRoleMeshes.png(out.resolve("rivet_hammer_stages_"+suffix+".png"),PreviewRoleMeshes.board("铆钉 / 起手与横扫 / 保留模型实体与短弧尾迹",hammer(),3,dark));
            PreviewRoleMeshes.png(out.resolve("rivet_anchor_cable_"+suffix+".png"),PreviewRoleMeshes.board("铆钉 / 墙锚状态与牵引回收 / 模型、真实UV、真实阶段",concat(anchors(),cable()),3,dark));
            PreviewRoleMeshes.png(out.resolve("rivet_branch_stages_"+suffix+".png"),PreviewRoleMeshes.board("铆钉 / 分支护板、固守区域与破城冲击 / 离线生产网格",branches(),3,dark));
        }
        var frames=new ArrayList<java.awt.image.BufferedImage>();
        for(int i=0;i<28;i++){
            float age=i/28f*14;var panels=new ArrayList<PreviewRoleMeshes.Panel>();
            panels.add(panel("普通撞墙 / tick "+String.format(Locale.ROOT,"%.1f",age),2,BURST,0,14,age,160));
            panels.add(panel("锚击锁紧 / tick "+String.format(Locale.ROOT,"%.1f",age),2,BURST,1,14,age,160));
            frames.add(PreviewRoleMeshes.board("铆钉 / 实际接触网格连续阶段 / offline only",panels,2,true));
        }
        PreviewRoleMeshes.gif(out.resolve("rivet_contact_dynamic.gif"),frames,50);
        System.out.println("[MYRIAD-RIVET-PREVIEW] PASS: 8 production mesh/UV boards on dark and grass backgrounds, 28-frame contact timeline; offline only, no first-person or game acceptance");
    }
    private static List<PreviewRoleMeshes.Panel> concat(List<PreviewRoleMeshes.Panel> a,List<PreviewRoleMeshes.Panel> b){var out=new ArrayList<>(a);out.addAll(b);return out;}
}
