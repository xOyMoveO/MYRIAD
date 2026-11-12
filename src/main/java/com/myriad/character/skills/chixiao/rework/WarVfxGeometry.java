package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
/** Heavy copper-red edges, dark fractures and weighted impact; deliberately not a flame clone. */
public final class WarVfxGeometry {
 private static final int L=0xFFDB9E,B=0xD9824E,D=0x603A38;
 public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
  var b=new AbilityMeshBuilder(p,age,reduced);Vec3 at=p.from(),f=direction(at,p.to()),s=side(f);double r=Math.max(.3,p.radius());
  switch(p.kind()){
   case ARC -> {double sweep=Math.toRadians(Math.max(50,p.angle())),a=Math.atan2(f.z,f.x);b.bladeArc(at,r,a-sweep*.5,sweep,Math.min(.7,r*.2),.28,L,D);b.decal(at.add(f.scale(r*.4)),r*.65,WAR_SLASH,B,.95);}
   case FLIGHT -> {Vec3 mid=p.to(),left=mid.add(s.scale(.9)).subtract(f.scale(.18)),right=mid.subtract(s.scale(.9)).subtract(f.scale(.18)),tip=mid.add(f.scale(.45)),top=mid.add(0,.16,0),bottom=mid.add(0,-.12,0);b.triangle(left,tip,top,L,.99);b.triangle(top,tip,right,B,.99);b.triangle(left,bottom,tip,D,.98);b.triangle(tip,bottom,right,D,.98);b.triangle(left,top,right,B,.95);b.triangle(left,right,bottom,D,.95);b.plane(mid,s,f,.9,.45,WAR_SLASH,L,.92);b.trail(at,p.to(),.25,WAR_DUST,B,.75);}
   case BEAM,TRAIL -> {b.trail(at,p.to(),Math.max(.15,r),WAR_DUST,B,.9);b.rod(at,p.to(),.04,L,B,D,.95);}
   case BURST -> {b.decal(at,r,WAR_CRACK,L,.98);if(p.angle()!=1){b.sparks(at,r,B);for(int i=0;i<(reduced?2:4);i++){double a=i*2.4;Vec3 pos=circle(at,r*.45,a,.15+b.progress*.6);b.rod(pos,pos.add(Math.cos(a)*.18,.3,Math.sin(a)*.18),.09,L,B,D,.95);}}}
   case SEAL -> {b.ring(at,r,.08,B,.96);b.decal(at,r,WAR_CRACK,L,.9);if(p.angle()!=359&&p.variant()>=6)b.bladeArc(at,r*.8,age*.04,Math.PI*1.2,.18,.15,L,D);}
   case SHIELD -> {int n=reduced?2:3;for(int i=0;i<n;i++){double a=i*Math.PI*2/n+age*.03;Vec3 pos=circle(at,r,a,.9),side=new Vec3(-Math.sin(a),0,Math.cos(a));b.plane(pos,UP,side,.6,.28,WAR_BLADE,L,.97);b.rod(pos.add(0,-.5,0),pos.add(0,.5,0),.045,L,B,D,.97);}}
   case CHARGE -> {b.decal(at,r,WAR_CRACK,L,.9);b.rod(at,at.add(0,.2+b.progress*.8,0),.12,L,B,D,.98);}
   case PILLAR -> {b.rod(at,p.to(),.18,L,B,D,.98);b.decal(at,r,WAR_CRACK,B,.98);}
   default -> {b.decal(at,r,WAR_SLASH,L,.95);b.ring(at,r,.1,B,.96);}
  }return b.result();
 }
}

