package com.myriad.character.skills.yunsheng.rework;

import com.myriad.character.ability.AbilityMeshBuilder;
import com.myriad.character.ability.WarSpiritModels;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Folded ivory paper, celadon glaze, and champagne-gold silk with sparse ground coverage. */
public final class SpiritVfxGeometry {
    private static final int IVORY=0xFFF9E0, JADE=0xA6EDD7, GOLD=0xF1C982;

    public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced) {
        var b=new AbilityMeshBuilder(p,age,reduced,maxQuads(p));
        Vec3 at=p.from(),end=p.to(),f=direction(at,end),s=side(f),up=s.cross(f).normalize();
        double r=Math.max(.12,p.radius()),t=b.progress,clock=p.started()%120+age;
        int k=(int)(clock*.75);
        switch(p.kind()) {
            case FLIGHT -> {
                if(at.distanceToSqr(end)<1e-8)break;
                Vec3 head=at.lerp(end,Math.clamp(age,0,1));
                if(WarSpiritModels.flight(p))WarSpiritModels.crane(b,head,f,s.scale(-1),clock);
                Vec3 tail=at.subtract(f.scale(.64)),tip=head.subtract(f.scale(.26));
                ribbon(b,tail,tip,s,.19,k,.96);
                if(!reduced) {
                    ribbon(b,tail.add(up.scale(.075)),tip.add(up.scale(.075)),up,.15,k+2,.79);
                    b.frame(head.subtract(f.scale(.49)),s,up,.72,.29,SPIRIT_HD_FLAKES,k,.84);
                }
            }
            case BEAM -> {
                if(at.distanceToSqr(end)<1e-8)break;
                b.rod(at,end,p.angle()==1?.034:.021,IVORY,JADE,GOLD,.98);
                ribbon(b,at.add(up.scale(.033)),end.add(up.scale(.033)),s,p.angle()==1?.22:.13,k,.93);
                int n=reduced?1:3;
                for(int i=0;i<n;i++) {
                    double fraction=(i+1.0)/(n+1),slide=p.angle()==1?t*(1-fraction)*.8:.08*Math.sin(clock*.46+i);
                    Vec3 pos=at.lerp(end,fraction+slide);
                    b.frame(pos,f,up,.26,.18,SPIRIT_HD_SIGIL,k+i,.96);
                }
                if(!reduced)b.frame(end.add(0,.12,0),s,up,.32,.49,SPIRIT_HD_SIGIL,k,.92);
            }
            case TRAIL -> {
                if(at.distanceToSqr(end)<1e-8)break;
                for(int sign:new int[]{-1,1}) {
                    Vec3 off=s.scale(sign*.40).add(0,.10*sign,0);
                    ribbon(b,at.add(off),end.add(off),up,.20,k+sign,.94);
                }
                if(!reduced)b.frame(at.lerp(end,.5),s,up,.53,.32,SPIRIT_HD_FLAKES,k,.83);
            }
            case SHIELD -> {
                int n=p.variant()==7&&!reduced?4:2;
                double opening=p.angle()==360?Math.min(1,(age+1)*.42):1;
                for(int i=0;i<n;i++) {
                    // Fixed sides read as contract pages, not an opaque rotating shell.
                    double a=(i+(n==2?.5:0))*Math.PI*2/n+.29;
                    Vec3 pos=circle(at,(r+.17)*opening,a,.83+i%2*.18);
                    Vec3 tangent=new Vec3(-Math.sin(a),0,Math.cos(a));
                    double height=p.variant()==7?.65:p.variant()==0?.46:.37;
                    b.frame(pos,tangent,UP,.31,height,SPIRIT_HD_SIGIL,k+i,.94);
                    if(p.angle()==360&&!reduced)b.frame(pos.add(0,.08,.025),tangent,UP,.40,height*.78,SPIRIT_HD_FLAKES,k+i,.67);
                }
                rim(b,at,r+.23,k,.79);
            }
            case SEAL -> {
                if(WarSpiritModels.owns(p)) {
                    WarSpiritModels.beacon(b,at,f,s.scale(-1));
                    rim(b,at,r,k,.89);
                    b.frame(at.add(0,1.47,.014),s,UP,.26,.29,SPIRIT_HD_SIGIL,k,.92);
                    if(!reduced)b.frame(at.add(0,.061,0),X,Z,r*.84,r*.84,SPIRIT_HD_FLAKES,k,.52);
                } else if(p.variant()==1) {
                    double expanding=r*(.55+.45*t);
                    rim(b,at,expanding,k,.98);
                    rim(b,at.add(0,.065,0),expanding*.69,k+2,.78);
                } else if(p.variant()==6&&p.angle()==359) {
                    double settle=1-t*.1;
                    rim(b,at,r*settle,k,.90);
                    b.frame(at.add(0,.12,0),X,Z,r*.52,r*.52,SPIRIT_HD_FLAKES,k,.83);
                } else if(p.variant()==6&&p.life()>20) {
                    rim(b,at,r,k,.84);
                    int n=reduced?3:6;
                    for(int i=0;i<n;i++) {
                        double a=i*Math.PI*2/n;
                        b.frame(circle(at,r*.94,a,.51),new Vec3(-Math.sin(a),0,Math.cos(a)),UP,
                                .25,.35,SPIRIT_HD_SIGIL,k+i,.80);
                    }
                } else if(p.variant()==6) {
                    double size=r*(.18+t*.82);
                    rim(b,at,size,k,.98);
                    rim(b,at.add(0,.058,0),size*.74,k+2,.80);
                    int n=reduced?3:6;
                    for(int i=0;i<n;i++) {
                        double a=(i+.25)*Math.PI*2/n;
                        Vec3 tangent=new Vec3(-Math.sin(a),0,Math.cos(a));
                        Vec3 pos=circle(at,size*.91,a,.12+Math.sin(t*Math.PI)*.46);
                        b.frame(pos,tangent,UP,.33,.21,SPIRIT_HD_FLAKES,k+i,.86);
                    }
                } else {
                    double size=r*(.61+t*.39);
                    rim(b,at,size,k,.94);
                    if(p.variant()==2||p.variant()==7)b.frame(at.add(0,.057,0),s,f,size*.55,size*.55,SPIRIT_HD_SIGIL,k,.83);
                }
            }
            case BURST -> {
                double radius=p.variant()==6||p.variant()==4&&r>2.5?r*(.14+t*.86):r*(.53+t*.47);
                rim(b,at,radius,k,.96);
                if(p.variant()==6||p.variant()==4&&r>2.5) {
                    rim(b,at.add(0,.06,0),radius*.82,k+1,.78);
                    if(!reduced)b.frame(at.add(0,.14,0),X,Z,radius*.9,radius*.9,SPIRIT_HD_FLAKES,k,.70);
                } else {
                    Vec3 pos=at.add(0,p.angle()==1?0:.38+t*.19,0);
                    b.frame(pos,s,up,r*.76,r*.45,SPIRIT_HD_FLAKES,k,.98);
                    if(p.variant()==2&&p.angle()!=1)
                        b.frame(pos.add(0,.074,.017),s,up,r*.44,r*.51,SPIRIT_HD_SIGIL,k+1,.86);
                    else if(p.variant()==3)b.frame(pos,s,up,r*.38,r*.64,SPIRIT_HD_SIGIL,k+1,.91);
                }
            }
            default -> {
                b.frame(at.add(0,.15,0),s,UP,r*.67,r*.45,SPIRIT_HD_SIGIL,k,.94);
                b.frame(at.add(0,.022,0),X,Z,r,r,SPIRIT_HD_RIM,k,.87);
            }
        }
        return b.result();
    }

    private static void rim(AbilityMeshBuilder b,Vec3 at,double size,int frame,double alpha) {
        b.frame(at.add(0,.026,0),X,Z,size,size,SPIRIT_HD_RIM,frame,alpha);
    }

    private static void ribbon(AbilityMeshBuilder b,Vec3 a,Vec3 z,Vec3 normal,double h,int frame,double alpha) {
        double length=a.distanceTo(z);
        if(length>.015)b.frame(a.lerp(z,.5),direction(a,z),normal,length*.53,h,SPIRIT_HD_RIBBON,frame,alpha);
    }
}
