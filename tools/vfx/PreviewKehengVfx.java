package com.myriad.tools;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.keheng.KehengVfx;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Calls production meshes for each frame; not a drawing of an intended effect or game capture. */
public final class PreviewKehengVfx {
    private static final String[] LABELS={"刻痕回响 · 逐层 / 三针交汇","恒步 · 三片盾弧 / 实际破裂阶段","迟针 · 针芯 / 双尾 / 接触针星",
            "错拍步 · 连续采样路径 / 残时圈","迟界 · 四节点 → 分段边界 → 薄层","随行秒域 · 开口层 / 自然收针",
            "一秒之外 · 透明竖弧 / 六组脉冲","终刻贯穿 · 倒流蓄势 / 16tick出剑","普攻 · 短刃光 / 三针接触"};
    private static void add(List<Quad> out,int variant,int kind,Vec3 from,Vec3 to,float radius,float stage,int life,long started,float age){
        var p=new AbilityVfxPayload(1,-1,11,variant,kind,from,to,radius,stage,life,started,false,kind==FLIGHT?7:0);
        out.addAll(AbilityVfxGeometry.build(p,age,false));
    }
    private static void field(List<Quad> out,int variant,double t,int warning,int duration,float radius,Vec3 at){
        if(t<warning){int sample=(int)(t/3)*3;add(out,variant,SEAL,at,at.add(0,1,0),radius,300+sample,5,100+sample,(float)(t-sample));}
        else if(t<warning+duration){int elapsed=(int)(t-warning)/3*3;add(out,variant,SEAL,at,at.add(0,1,0),radius,elapsed,5,100+warning+elapsed,(float)(t-warning-elapsed));}
        else add(out,variant,BURST,at,at.add(0,1,0),radius,KehengVfx.NATURAL_END,6,100+warning+duration,(float)(t-warning-duration));
    }
    private static List<Quad> mesh(int index,double unit,boolean still){
        var out=new ArrayList<Quad>();Vec3 origin=Vec3.ZERO;
        switch(index){
            case 0->{double t=still?12.8:unit*21;int stage=t<6?1:t<12?2:3;int kind=stage==3?BURST:SEAL;var at=stage==3?new Vec3(0,.9,0):origin;
                add(out,0,kind,at,at.add(0,0,1),.45f,stage,5,100,(float)(stage==3?t-12:t%3));}
            case 1->{double t=still?12:unit*40;
                if(t<30)add(out,1,SHIELD,origin,new Vec3(0,1,0),.65f,360,8,100+(long)t/6*6,(float)(t%6));
                else add(out,1,BURST,origin,new Vec3(0,0,1),.65f,KehengVfx.SHIELD_BREAK,5,130,(float)(t-30));}
            case 2->{double t=still?2.6:unit*11;var start=new Vec3(-2.4,.8,0);var dir=new Vec3(1,.16,.08).normalize();
                for(int n=0;n<4;n++){
                    var from=start.add(dir.scale(n*1.2));var to=start.add(dir.scale((n+1)*1.2));
                    if(t>=n&&t<n+1)add(out,2,FLIGHT,from,to,.22f,0,3,100+n,(float)(t-n));
                    if(t>=n)add(out,2,TRAIL,from,to,.12f,0,4,100+n,(float)(t-n));
                }
                if(t>=4){var hit=start.add(dir.scale(4.8));add(out,2,BURST,hit,hit.add(dir),.55f,0,5,104,(float)(t-4));}}
            case 3->{double t=still?4.5:unit*38;var start=new Vec3(-2,0,0);field(out,3,t,0,30,2,start);
                for(int n=0;n<5;n++)if(t>=n){var from=start.add(n*.9,.4,n*.12);var to=start.add((n+1)*.9,.4,(n+1)*.12);add(out,3,TRAIL,from,to,.18f,0,7,100+n,(float)(t-n));}}
            case 4->field(out,4,still?27:unit*78,9,60,3.5f,origin);
            case 5->field(out,5,still?25:unit*58,0,50,3,origin);
            case 6->{double t=still?34:unit*80;field(out,6,t,13,60,4,origin);
                for(int pulse=1;pulse<=6;pulse++){double at=13+pulse*10;if(t>=at)add(out,6,ARC,origin,new Vec3(0,1,0),4,0,5,100+(long)at,(float)(t-at));}}
            case 7->{double t=still?16.7:unit*25;
                if(t<16){int sample=(int)t/3*3;add(out,7,CHARGE,origin,new Vec3(0,0,12),1.5f,0,5,100+sample,(float)(t-sample));}
                else add(out,7,BEAM,new Vec3(0,1.3,0),new Vec3(0,1.3,12),1.5f,0,6,116,(float)(t-16));}
            case 8->{double t=still?1.2:unit*8;var at=new Vec3(0,.7,0);add(out,2,BEAM,at,at.add(0,0,.8),.03f,KehengVfx.BASIC,3,100,(float)t);
                if(t>=1)add(out,2,BURST,at.add(0,0,.85),at.add(0,0,1.85),.3f,KehengVfx.BASIC,3,101,(float)(t-1));}
        }
        return out;
    }
    private static BufferedImage board(double time,boolean still,boolean dark){
        var panels=new ArrayList<PreviewRoleMeshes.Panel>();
        for(int i=0;i<9;i++){
            double scale=switch(i){case 0->135;case 1->105;case 2->60;case 3->44;case 4,5->38;case 6->32;case 7->29;default->125;};
            Vec3 focus=i==7?new Vec3(0,1.2,6):i==0||i==1?new Vec3(0,.8,0):i==6?new Vec3(0,1.3,0):i==8?new Vec3(0,.8,.4):new Vec3(0,.5,0);
            panels.add(new PreviewRoleMeshes.Panel(LABELS[i],mesh(i,time,still),scale,focus));
        }
        return PreviewRoleMeshes.board("刻衡 · 生产网格逐帧投影 / 独立阶段时间轴，长领域加速展示 / 非游戏截图",panels,3,dark);
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);
        PreviewRoleMeshes.png(out.resolve("keheng_mesh_dark.png"),board(0,true,true));
        PreviewRoleMeshes.png(out.resolve("keheng_mesh_grass.png"),board(0,true,false));
        var frames=new ArrayList<BufferedImage>();for(int i=0;i<40;i++)frames.add(board(i/40.0,false,true));
        PreviewRoleMeshes.gif(out.resolve("keheng_dynamic_preview.gif"),frames,60);
        var stages=new ArrayList<PreviewRoleMeshes.Panel>();
        for(int i=0;i<6;i++){
            double t=new double[]{0,3,6,9,25,70}[i];var m=new ArrayList<Quad>();field(m,4,t,9,60,3.5f,Vec3.ZERO);
            stages.add(new PreviewRoleMeshes.Panel("迟界 / tick "+(int)t,m,38,new Vec3(0,.5,0)));
        }
        var cut=new ArrayList<Quad>();add(cut,5,BURST,Vec3.ZERO,new Vec3(0,1,0),3,KehengVfx.INTERRUPTED_END,4,150,1.2f);
        stages.add(new PreviewRoleMeshes.Panel("随行域 / 打断散开",cut,38,new Vec3(0,.5,0)));
        var end=new ArrayList<Quad>();field(end,5,53,0,50,3,Vec3.ZERO);stages.add(new PreviewRoleMeshes.Panel("随行域 / 自然收针",end,38,new Vec3(0,.5,0)));
        PreviewRoleMeshes.png(out.resolve("keheng_phase_steps.png"),PreviewRoleMeshes.board("刻衡 / 实际网格阶段与两种结束",stages,4,true));
        System.out.println("[MYRIAD-KEHENG-PREVIEW] PASS: nine production mesh timelines, true stages/world phase, PNG dark/grass and 40-frame GIF; offline only");
    }
}
