package com.myriad.character.skills.yunsheng.rework;
import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
/** Folded paper cranes, jade knots and taut silk; not an abstract green orb. */
public final class SpiritVfxGeometry {
 private static final int L=0xFFF4D0,B=0x86D6CE,D=0x365F72,G=0xDBB877;
 public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
  var b=new AbilityMeshBuilder(p,age,reduced);Vec3 at=p.from(),f=direction(at,p.to()),s=side(f);double r=Math.max(.3,p.radius());
  switch(p.kind()){
   case FLIGHT -> {crane(b,p.to(),f,age);b.trail(at,p.to(),.12,SPIRIT_THREAD,B,.9);}
   case BEAM,TRAIL -> {b.trail(at,p.to(),Math.max(.08,r),SPIRIT_THREAD,L,.9);b.rod(at,p.to(),.022,L,B,D,.94);b.plane(at.lerp(p.to(),.5),s,UP,.22,.3,SPIRIT_KNOT,L,.98);}
   case ARC -> {crane(b,at.add(f.scale(.4)),f,age);b.decal(at,r,SPIRIT_SEAL,B,.85);}
   case SEAL -> {b.ring(at,r,.06,B,.95);b.decal(at.add(0,.015,0),r,SPIRIT_SEAL,L,.9);if(p.angle()!=359){int n=reduced?2:3;for(int i=0;i<n;i++){double a=i*Math.PI*2/n+age*.02;Vec3 pos=circle(at,r*.85,a,.55);b.plane(pos,new Vec3(-Math.sin(a),0,Math.cos(a)),UP,.2,.4,SPIRIT_KNOT,L,.95);b.rod(pos.add(0,-.25,0),pos.add(0,.3,0),.025,L,B,D,.95);}}}
   case SHIELD -> {int n=reduced?2:3;for(int i=0;i<n;i++){double a=i*Math.PI*2/n+age*.045;Vec3 pos=circle(at,r,a,.9);b.plane(pos,new Vec3(-Math.sin(a),0,Math.cos(a)),UP,.3,.5,SPIRIT_KNOT,L,.95);crane(b,pos,new Vec3(-Math.sin(a),0,Math.cos(a)),age+i);}b.ring(at.add(0,.1,0),r,.04,G,.95);}
   case BURST -> {b.decal(at,r,SPIRIT_SEAL,L,.98);b.sparks(at,r,G);if(p.angle()!=1&&p.variant()!=2)crane(b,at.add(0,.4,0),f,age);}
   case CHARGE -> {b.decal(at,r,SPIRIT_SEAL,L,.95);b.plane(at.add(0,.3+b.progress*.6,0),s,UP,.3,.45,SPIRIT_KNOT,L,.98);}
   case PILLAR -> {b.rod(at,p.to(),.05,L,B,D,.95);crane(b,p.to(),f,age);}
   default -> {b.decal(at,r,SPIRIT_SEAL,L,.95);crane(b,at.add(0,.4,0),f,age);}
  }return b.result();
 }
 private static void crane(AbilityMeshBuilder b,Vec3 at,Vec3 f,double age){
  Vec3 s=side(f),up=s.cross(f).normalize(),nose=at.add(f.scale(.5)),tail=at.subtract(f.scale(.4)),ridge=at.add(up.scale(.14));
  b.triangle(tail,nose,ridge,L,.99);b.triangle(nose,tail,at.subtract(up.scale(.12)),D,.97);
  for(int side:new int[]{-1,1}){Vec3 wing=at.add(s.scale(side*.55)).add(up.scale(.15+Math.sin(age*.8)*.12));b.triangle(tail,wing,ridge,side>0?L:B,.99);b.triangle(nose,ridge,wing,side>0?B:D,.97);}
  b.plane(at,s,f,.5,.4,SPIRIT_CRANE,L,.9);
 }
}

