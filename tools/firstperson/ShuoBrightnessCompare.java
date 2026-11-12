package com.myriad.tools;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.shuo.*;
import com.myriad.client.firstperson.*;
import com.myriad.network.*;
import net.minecraft.world.phys.Vec3;
import java.net.*;
import java.nio.file.*;
import java.util.*;
/** Compares actual previous-JAR meshes to the current production code; no duplicated formulas. */
public final class ShuoBrightnessCompare {
    private static long quads,meshes;private static double before,after;
    private static final class Previous extends URLClassLoader {
        Previous(Path jar)throws Exception{super(new URL[]{jar.toUri().toURL()},ShuoBrightnessCompare.class.getClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            if(!name.startsWith("com.myriad.character.skills.shuo.")&&!name.startsWith("com.myriad.client.firstperson.Shuo"))return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)){var type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;}
        }
    }
    @SuppressWarnings("unchecked") private static List<Quad> mesh(Object value){return (List<Quad>)value;}
    private static void compare(List<Quad> a,List<Quad> b){
        NightDaggerPreview.require(a.size()==b.size(),"Brightness cannot add/remove geometry");meshes++;
        for(int i=0;i<a.size();i++){
            var x=a.get(i);var y=b.get(i);
            NightDaggerPreview.require(x.a().equals(y.a())&&x.b().equals(y.b())&&x.c().equals(y.c())&&x.d().equals(y.d())&&x.material()==y.material()&&x.reverseV()==y.reverseV()
                &&x.startU()==y.startU()&&x.endU()==y.endU()&&x.startV()==y.startV()&&x.endV()==y.endV(),"Geometry, footprint, texture mapping and layer count must match previous JAR");
            NightDaggerPreview.require(y.alpha()>=x.alpha()&&y.alpha()<=1,"Each layer is brighter or already saturated");
            double oldL=0,newL=0;int j=0;for(int shift:new int[]{16,8,0}){
                int c=x.color()>>shift&255,d=y.color()>>shift&255;NightDaggerPreview.require(d>=c,"No darkened tint channel");
                double weight=new double[]{.2126,.7152,.0722}[j++];oldL+=c*weight;newL+=d*weight;
            }before+=oldL*x.alpha();after+=newL*y.alpha();quads++;
        }
    }
    public static void main(String[] args)throws Exception{
        try(var old=new Previous(Path.of(args[0]))){
            var world=old.loadClass("com.myriad.character.skills.shuo.ShuoVfx").getMethod("build",AbilityVfxPayload.class,float.class,boolean.class);
            for(int signal=301;signal<=313;signal++)for(int index=0;index<8;index++)for(boolean low:new boolean[]{false,true})for(float age:new float[]{.1f,1,3.5f,6,9.9f}){
                var p=ShuoVisuals.recipe(1,-1,index,signal,new Vec3(0,1,0),new Vec3(0,1,3.5),4,10,0);
                compare(mesh(world.invoke(null,p,age,low)),ShuoVfx.build(p,age,low));
            }
            for(int action:new int[]{0,11,12})for(boolean drawn:new boolean[]{false,true})for(float age:new float[]{1,3.5f,5}){
                var p=ShuoVisuals.basicRecipe(1,Vec3.ZERO,new Vec3(0,0,1),drawn,action,0);
                compare(mesh(world.invoke(null,p,age,false)),ShuoVfx.build(p,age,false));
            }
            var motionClass=old.loadClass("com.myriad.client.firstperson.ShuoMotion");
            var accept=motionClass.getMethod("accept",ShuoViewPayload.class,double.class,double.class,boolean.class);
            var trail=old.loadClass("com.myriad.client.firstperson.ShuoSkillTrail").getMethod("build",motionClass,double.class,boolean.class);
            for(int action=1;action<=9;action++)for(boolean drawn:new boolean[]{false,true})for(boolean mirror:new boolean[]{false,true}){
                var m=motionClass.getConstructor().newInstance();var current=new ShuoMotion();var p=new ShuoViewPayload(action,10,100,1);
                accept.invoke(m,p,100d,0d,drawn);current.accept(p,100,0,drawn);
                for(int n=0;n<21;n++){double now=100+n*.5;compare(mesh(trail.invoke(null,m,now,mirror)),ShuoSkillTrail.build(current,now,mirror));}
            }
            var glow=old.loadClass("com.myriad.client.firstperson.ShuoBladeLight").getMethod("build",org.joml.Matrix4f.class,double.class,double.class,float.class,boolean.class);
            for(float alpha:new float[]{.25f,.5f,.9f})for(double exposed:new double[]{.15,.5,1}){
                var mat=new org.joml.Matrix4f().translate(0,0,-2);
                compare(mesh(glow.invoke(null,mat,exposed,2d,alpha,false)),ShuoBladeLight.build(mat,exposed,2,alpha,false));
            }
        }
        NightDaggerPreview.require(after>before*1.08,"Combined actual-layer intensity must measurably increase");
        System.out.printf(java.util.Locale.ROOT,"[MYRIAD-SHUO-BRIGHTNESS-COMPARE] PASS: %d previous/current mesh pairs, %d identical geometric quads; tint/opacity energy %.3fx; actual images still need live-client acceptance.%n",meshes,quads,after/before);
    }
}
