package com.myriad.character.skills.qingluo.rework;
import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
/** Jade leaves, raised lotus petals and medicinal droplets, with an open readable centre. */
public final class HerbVfxGeometry {
 private static final int L=0xF4F3C7,B=0x84CF78,D=0x285A46,G=0xE9CA79;
 public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
  var b=new AbilityMeshBuilder(p,age,reduced);Vec3 at=p.from(),f=direction(at,p.to());double r=Math.max(.3,p.radius());
  switch(p.kind()){
   case FLIGHT -> {leaf(b,p.to(),f,.65);b.plane(p.to(),side(f),UP,.27,.35,HERB_DEW,L,.95);b.trail(at,p.to(),.12,HERB_LEAF,B,.85);}
   case BEAM,TRAIL -> {b.trail(at,p.to(),Math.max(.09,r),HERB_LEAF,B,.95);b.rod(at,p.to(),.025,L,B,D,.9);}
   case ARC -> {b.bladeArc(at,r,Math.atan2(f.z,f.x)-.7,1.4,.15,.14,L,D);leaf(b,at.add(f.scale(r*.6)),f,.5);}
   case SEAL -> {b.ring(at,r,.065,B,.95);b.decal(at.add(0,.015,0),r,HERB_SEAL,L,.9);if(p.angle()!=359)lotus(b,at,Math.min(r*.45,1.1),reduced?3:5);}
   case SHIELD -> {int n=reduced?3:5;for(int i=0;i<n;i++){double a=i*Math.PI*2/n+age*.04;Vec3 pos=circle(at,r,a,.7);leaf(b,pos,new Vec3(-Math.sin(a),.7,Math.cos(a)).normalize(),.55);b.plane(pos,new Vec3(-Math.sin(a),0,Math.cos(a)),UP,.25,.35,HERB_LEAF,L,.9);}b.ring(at.add(0,.1,0),r,.045,G,.95);}
   case BURST -> {lotus(b,at,.4+r*.25,reduced?3:5);b.decal(at,r*.7,HERB_LOTUS,L,.95);b.sparks(at,r,G);}
   case CHARGE -> {b.decal(at,r,HERB_SEAL,L,.92);lotus(b,at,.3+b.progress*.6,reduced?3:5);}
   case PILLAR -> {b.rod(at,p.to(),.045,L,B,D,.95);lotus(b,p.to(),.7,reduced?3:5);}
   default -> {lotus(b,at,Math.min(1,r),reduced?3:5);b.decal(at,r,HERB_SEAL,B,.85);}
  }return b.result();
 }
 private static void lotus(AbilityMeshBuilder b,Vec3 at,double r,int n){for(int i=0;i<n;i++){double a=i*Math.PI*2/n;leaf(b,circle(at,r*.45,a,.22),new Vec3(Math.cos(a),.45,Math.sin(a)).normalize(),r*.8);}}
 private static void leaf(AbilityMeshBuilder b,Vec3 at,Vec3 f,double k){
  Vec3 s=side(f),tip=at.add(f.scale(k)),heel=at.subtract(f.scale(k*.5)),l=at.add(s.scale(k*.32)),r=at.subtract(s.scale(k*.32)),ridge=at.add(0,k*.18,0);
  b.triangle(heel,l,ridge,B,.98);b.triangle(l,tip,ridge,L,.98);b.triangle(tip,r,ridge,B,.98);b.triangle(r,heel,ridge,D,.95);
  b.triangle(heel,r,tip,D,.92);b.triangle(heel,tip,l,D,.92);b.line(heel,tip,s,.025,G,.95);
 }
}

