package com.myriad.tools;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.yike.YikeVfx;
import com.myriad.character.skills.yike.YikeVfxGeometry;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static com.myriad.character.skills.yike.YikeVfx.*;

/** Production recipes sampled on a real-tick timeline; offline CPU projection, never a game capture. */
public final class PreviewYikeVfx {
    private static final Vec3 O=Vec3.ZERO,F=new Vec3(1,0,0);
    private static AbilityVfxPayload packet(int variant,int kind,int signal,Vec3 from,Vec3 to,float radius,int life,long start){
        return new AbilityVfxPayload(1,-1,THEME,variant,kind,from,to,radius,signal,life,start,false,7);
    }
    private static List<Quad> mesh(int variant,int kind,int signal,Vec3 from,Vec3 to,float radius,int life,float age,long start){
        return YikeVfxGeometry.build(packet(variant,kind,signal,from,to,radius,life,start),age,false);
    }
    private static List<Quad> prep(int variant,int signal,float progress,Vec3 from){
        var p=new AbilityVfxPayload(1,-1,THEME,variant,AbilityVfxGeometry.CHARGE,from,from.add(F),.35f,signal+.4f*progress,3,100,false,7);
        return YikeVfxGeometry.build(p,.5f,false);
    }
    private static List<Quad> flight(int signal,Vec3 from,Vec3 to,float r,long start){
        var p=packet(signal==HEAVY?6:2,AbilityVfxGeometry.FLIGHT,signal,from,to,r,3,start);
        var result=new ArrayList<>(YikeVfxGeometry.build(p,.9f,false));var wake=YikeVfxGeometry.wakeForFlight(p);
        if(wake!=null)result.addAll(YikeVfxGeometry.build(wake,.9f,false));return result;
    }
    private static List<Quad> sample(int index,int tick,boolean still){
        int t=tick;var out=new ArrayList<Quad>();
        switch(index){
            case 0 -> out.addAll(flight(BASIC,new Vec3(-.6,.6,0),new Vec3(.7,.6,0),.065f,100+t));
            case 1 -> {int phase=still?8:t%24;int signal=phase<8?MARK1:phase<16?MARK2:MARK_BURST;out.addAll(mesh(0,AbilityVfxGeometry.SHIELD,signal,O,F,.7f,8,phase%8,100+t));}
            case 2 -> out.addAll(mesh(1,AbilityVfxGeometry.SHIELD,BARRIER,O,F,.7f,50,still?12:t%50,100));
            case 3 -> {int phase=still?9:t%24;
                if(phase<5)out.addAll(prep(2,MUZZLE_CHARGE,phase/5f,O));
                else if(phase<13){double x=(phase-5)*1.5;out.addAll(flight(0,new Vec3(x,.5,0),new Vec3(x+1.5,.5,0),.14f,100+t));if(phase<10)out.addAll(mesh(2,AbilityVfxGeometry.BURST,MUZZLE,O,F,.5f,5,phase-5,100+t));}
                else if(phase<21)out.addAll(mesh(2,AbilityVfxGeometry.BURST,IMPACT,new Vec3(8,0,0),new Vec3(8,0,0),1.5f,8,phase-13,100+t));}
            case 4 -> {int phase=still?8:t%22;out.addAll(mesh(3,phase<11?AbilityVfxGeometry.SEAL:AbilityVfxGeometry.BURST,phase<11?DENIAL_WARNING:DENIAL_BURST,O,O,2.5f,phase<11?11:9,phase<11?phase:phase-11,100));}
            case 5,6 -> {boolean mobile=index==6;int phase=still?15:t%36,deploy=mobile?7:9;
                if(phase<deploy)out.addAll(prep(mobile?5:4,DEPLOY,phase/(float)deploy,new Vec3(0,1.5,0)));
                else out.addAll(mesh(mobile?5:4,AbilityVfxGeometry.BURST,mobile?MOBILE:FIXED,O,F,mobile?.45f:.65f,120,phase-deploy,100));}
            case 7 -> {int phase=still?18:t%42;Vec3 ground=new Vec3(3,.055,0);
                if(phase<22)out.addAll(mesh(6,AbilityVfxGeometry.SEAL,HEAVY_WARNING,ground,ground,4,3,.5f,100+t));
                if(phase>=12&&phase<22){double a=(phase-12)/10.0,b=(phase-11)/10.0;Vec3 begin=new Vec3(-3,1.6,0),end=new Vec3(3,.1,0);
                    Vec3 from=begin.lerp(end,a).add(0,4*2.5*a*(1-a),0),to=begin.lerp(end,b).add(0,4*2.5*b*(1-b),0);out.addAll(flight(HEAVY,from,to,.36f,100+t));}
                if(phase>=22&&phase<36)out.addAll(mesh(6,AbilityVfxGeometry.BURST,HEAVY_IMPACT,ground,ground,4,14,phase-22,100+t));}
            case 8 -> {int phase=still?30:t%82;
                if(phase<12)out.addAll(prep(7,BEAM_CHARGE,phase/12f,O));
                else if(phase<72){out.addAll(mesh(7,AbilityVfxGeometry.BEAM,YikeVfx.BEAM,O,new Vec3(16,0,0),.625f,3,.5f,100+t));
                    if((phase-12)%10<4)out.addAll(mesh(7,AbilityVfxGeometry.SHIELD,BEAM_HIT,new Vec3(14,0,0),new Vec3(15,0,0),.4f,4,(phase-12)%10,100+t));}
                else if(phase<76)out.addAll(mesh(7,AbilityVfxGeometry.BEAM,BEAM_END,O,new Vec3(16,0,0),.625f,4,phase-72,100+t));}
            case 9 -> {int phase=still?3:t%12;if(phase<5)out.addAll(prep(2,MUZZLE_CHARGE,phase/5f,O));else if(phase<10)out.addAll(mesh(2,AbilityVfxGeometry.BURST,MUZZLE,O,F,.5f,5,phase-5,100+t));}
            case 10 -> out.addAll(mesh(6,AbilityVfxGeometry.BURST,HEAVY_IMPACT,O,O,4,14,still?3:t%20,100));
            case 11 -> out.addAll(mesh(7,AbilityVfxGeometry.BEAM,BEAM_END,O,new Vec3(16,0,0),.625f,4,still?1:t%10,100+t));
        }
        return out;
    }
    private static BufferedImage board(int tick,boolean still,boolean dark){
        String[] names={"Basic / tiny ivory crystal","Passive A / calibration marks","Passive B / three barrier facets","Q / charge, flight, layered hit","W / sparse warning then petals","Major A / fixed support lights","Major B / mobile support lights","Ultimate A / real parabolic direction","Ultimate B / clipped flowing beam","Muzzle / inward charge then discharge","Heavy impact / ivory, coral, fragments","Beam cancellation / 0.20s contraction"};
        double[] scales={190,150,145,27,58,190,190,32,23,270,42,23};
        Vec3[] focus={new Vec3(0,.6,0),O,O,new Vec3(5,.4,0),O,O,O,new Vec3(1,1,0),new Vec3(8,0,0),O,new Vec3(0,.3,0),new Vec3(8,0,0)};
        var panels=new ArrayList<PreviewRoleMeshes.Panel>();for(int i=0;i<12;i++)panels.add(new PreviewRoleMeshes.Panel(names[i],sample(i,tick,still),scales[i],focus[i]));
        return PreviewRoleMeshes.board("Yike / all eight candidates + basic / no bloom",panels,4,dark);
    }
    private static BufferedImage stages(){
        var panels=new ArrayList<PreviewRoleMeshes.Panel>();
        for(float age:new float[]{0,1.5f,3,6})panels.add(new PreviewRoleMeshes.Panel("Q impact / tick "+age,mesh(2,AbilityVfxGeometry.BURST,IMPACT,O,O,1.5f,8,age,100),92,O));
        for(float age:new float[]{0,2,5,11})panels.add(new PreviewRoleMeshes.Panel("Heavy impact / tick "+age,mesh(6,AbilityVfxGeometry.BURST,HEAVY_IMPACT,O,O,4,14,age,100),42,new Vec3(0,.3,0)));
        for(float age:new float[]{0,1,2,3})panels.add(new PreviewRoleMeshes.Panel("Beam cancel / tick "+age,mesh(7,AbilityVfxGeometry.BEAM,BEAM_END,O,new Vec3(16,0,0),.625f,4,age,100),23,new Vec3(8,0,0)));
        return PreviewRoleMeshes.board("Yike / actual production impact and cancellation stages",panels,4,true);
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args[0]);Files.createDirectories(out);
        PreviewRoleMeshes.png(out.resolve("yike_vfx_meshes_dark.png"),board(0,true,true));
        PreviewRoleMeshes.png(out.resolve("yike_vfx_meshes_grass.png"),board(0,true,false));
        PreviewRoleMeshes.png(out.resolve("yike_vfx_stages.png"),stages());
        var frames=new ArrayList<BufferedImage>();for(int i=0;i<84;i++)frames.add(board(i,false,true));
        PreviewRoleMeshes.gif(out.resolve("yike_vfx_preview.gif"),frames,50);
        for(int t:new int[]{0,5,12,22,28,72,74})PreviewRoleMeshes.png(out.resolve("yike_vfx_frame_"+t+".png"),frames.get(t));
        System.out.println("Yike: production geometry, 84 x 50ms frames, fixed cameras/scales, all candidates + basic; offline CPU projection only");
    }
}
