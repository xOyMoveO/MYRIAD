package com.myriad.character.skills.hanze.rework;
import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
/** Faceted translucent ice with opaque bevels; no cube projectile or snow-only cloud. */
public final class IceVfxGeometry {
 private static final int L=0xEBFAFF,B=0x7CCDEB,D=0x285B87;
 public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
  var b=new AbilityMeshBuilder(p,age,reduced);Vec3 at=p.from(),f=direction(at,p.to()),s=side(f);double r=Math.max(.25,p.radius());
  switch(p.kind()){
   case FLIGHT -> {double k=p.variant()==5?1.35:.8;crystal(b,p.to(),f,k,.17*k);b.trail(at.subtract(f.scale(.5)),p.to(),.18,ICE_MIST,B,.65);b.plane(p.to(),f,s,.55*k,.18*k,ICE_SHARD,L,.92);}
   case BEAM,TRAIL -> {b.trail(at,p.to(),Math.max(.12,r),ICE_MIST,B,.8);b.rod(at,p.to(),Math.max(.025,r*.2),L,B,D,.95);}
   case ARC -> {b.bladeArc(at,r,Math.atan2(f.z,f.x)-.8,1.6,.3,.16,L,D);b.decal(at,r*.7,ICE_CREST,B,.9);}
   case SEAL -> {b.decal(at,r,ICE_CRACK,L,.9);b.ring(at,r,.08,B,.95);if(p.angle()!=359){int n=reduced?3:5;for(int i=0;i<n;i++)crystal(b,circle(at,r*.83,i*Math.PI*2/n,.28),UP,.55+.2*Math.sin(age*.15+i),.16);}}
   case SHIELD -> {int n=reduced?3:5;for(int i=0;i<n;i++){double a=i*Math.PI*2/n+age*.025;Vec3 pos=circle(at,r,a,.9);crystal(b,pos,UP,.7,.16);b.plane(pos,new Vec3(-Math.sin(a),0,Math.cos(a)),UP,.3,.55,ICE_CREST,L,.85);}}
   case CHARGE -> {b.decal(at,r,ICE_CRACK,L,.9);crystal(b,at.add(0,.2+b.progress*.4,0),UP,.4+b.progress*.6,.16);}
   case BURST -> {b.decal(at,r,ICE_CRACK,L,.98);if(p.angle()!=1){int n=reduced?3:5;for(int i=0;i<n;i++)crystal(b,circle(at,r*.35,i*2.4,.2+b.progress*.5),new Vec3(Math.cos(i*2.4),.8,Math.sin(i*2.4)).normalize(),.6,.12);}b.sparks(at,r,B);}
   case PILLAR -> {crystal(b,at.lerp(p.to(),.45),UP,Math.min(2.8,at.distanceTo(p.to())*.75),.32);b.decal(at,.9,ICE_CRACK,B,.95);}
   default -> {b.decal(at,r,ICE_CRACK,L,.95);crystal(b,at.add(0,.5,0),UP,1,.25);}
  }return b.result();
 }
 private static void crystal(AbilityMeshBuilder b,Vec3 at,Vec3 f,double h,double w){
  Vec3 s=side(f),v=s.cross(f).normalize(),tip=at.add(f.scale(h*.7)),heel=at.subtract(f.scale(h*.3));
  for(int i=0;i<4;i++){double a=i*Math.PI/2;Vec3 x=at.add(s.scale(Math.cos(a)*w)).add(v.scale(Math.sin(a)*w)),y=at.add(s.scale(Math.cos(a+Math.PI/2)*w)).add(v.scale(Math.sin(a+Math.PI/2)*w));b.triangle(x,tip,y,i<2?L:B,.96);b.triangle(y,heel,x,D,.9);}
 }
}

