package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;

/** Approved sword model plus richly painted, multi-plane pixel-art VFX. */
public final class SwordVfxGeometry {
    private static final int WHITE=0xF5FAF6,CYAN=0x30E5D9,BLUE=0x337CF3,GOLD=0xFFD45C,VIOLET=0x9A69E5;
    private SwordVfxGeometry(){}
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced){
        if(HeavenSwordVfx.owns(p))return HeavenSwordVfx.build(p,age,reduced);
        var b=new AbilityMeshBuilder(p,age,reduced);var out=new ArrayList<Quad>();
        Vec3 at=p.from(),f=direction(at,p.to());double r=Math.max(.25,p.radius());
        var pixels=new SwordPixelLayers(out,b.fade,reduced,p.started(),age);
        // Repeated short-life packets must share a phase instead of restarting the orbit every tick.
        double phase=((p.started()%80)+age)*Math.PI/40;
        switch(p.kind()){
            case FLIGHT->{
                // A volley packet describes ONE tick of travel, not a sword parked at its endpoint
                // for the entire three-tick trail lifetime. A missed renewal leaves only fading light.
                boolean volley=p.variant()==4;
                Vec3 tip=volley?at.lerp(p.to(),Math.clamp(age,0,1)):p.to();
                double length=p.variant()==7?3.2:1.6;Vec3 center=SwordModelAsset.centerAtTip(tip,f,length);
                if(!volley||age<1)sword(out,center,f,length,phase,b.fade,reduced);
                if(p.variant()==7)pixels.halo(center.subtract(f.scale(length*.25)),f,.7,phase*.3);
                pixels.wake(center.subtract(f.scale(length*.8)),tip,p.variant()==7?.24:.13,phase);
                if(p.variant()==7)pixels.crown(center,f,.4,phase,4);
            }
            case BEAM->{
                double t=Math.clamp(age/Math.max(1,p.life()*.75),0,1),length=p.variant()==0?.95:1.5;
                Vec3 tip=at.lerp(p.to(),t),center=SwordModelAsset.centerAtTip(tip,f,length);
                if(t<1)sword(out,center,f,length,phase,b.fade,reduced);
                pixels.wake(at,tip,.12,phase);
                if(p.variant()==6&&t>.65)pixels.impact(p.to(),f,.4,(t-.65)/.35);
            }
            case ARC->{
                pixels.slash(at.add(f.scale(r*.55)),f,r*.85,Math.PI/2+phase*.08);
                if(!reduced)pixels.slash(at.add(f.scale(r*.5)).add(0,.09,0),f,r*.5,-phase*.06);
                Vec3 center=at.add(f.scale(r*.55)).add(0,.12,0);
                sword(out,center,f,1.25,phase,b.fade,reduced);
                pixels.impact(at.add(f.scale(r*.65)),f,.4,b.progress);
                if(p.variant()==7)pixels.ring(at.add(0,-.3,0),r*.65,phase,GOLD);
            }
            case SEAL->{
                boolean warning=p.angle()==359||p.personal();
                b.ring(at,r,.065,warning?CYAN:p.variant()==6?BLUE:GOLD,.95);
                if(warning)b.decal(at.add(0,.015,0),r,SWORD_SIGIL,WHITE,.8);
                if(!warning&&p.variant()==4){
                    // Launch gate is light only. The three swords exist exclusively as real FLIGHT
                    // instances; do not leave a second, stationary copy of them at the cast point.
                    Vec3 center=at.add(f.scale(.65));
                    pixels.halo(center,f,1.15,phase*.2);
                    pixels.crown(center,f,.65,phase,3);
                }else if(!warning){
                    int n=reduced?2:3;
                    for(int i=0;i<n;i++){
                        double a=i*Math.PI*2/n+phase*(p.variant()==6?-.12:.15);
                        double descent=(b.age+i*7)%20/20.0;
                        Vec3 center=circle(at,r*.86,a,p.variant()==6?.55+3*(1-descent):.65);
                        sword(out,center,p.variant()==6?UP.scale(-1):UP,p.variant()==6?1.3:.9,phase,b.fade,reduced||p.variant()==6);
                    }
                    pixels.ring(at.add(0,.07,0),r*.94,phase*.08,p.variant()==6?BLUE:VIOLET);
                    pixels.crown(at,f,r*.68,phase*.3,6);
                    if(p.variant()==5){
                        for(int i=0;i<(reduced?2:4);i++){
                            double a=i*Math.PI/2;Vec3 edge=circle(at,r*.72,a,0);
                            pixels.bar(edge.add(0,.2,0),UP,.4,.12,.12,GOLD,.95);
                            pixels.bar(edge.add(0,.43,0),new Vec3(Math.cos(a),0,Math.sin(a)),.32,.07,.07,CYAN,.95);
                        }
                    }
                }
            }
            case SHIELD->{
                if(p.variant()==0){
                    // Passive blade count is real authoritative state, not a permanent four-sword decoration.
                    int n=Math.clamp((int)p.angle(),1,4);
                    for(int i=0;i<n;i++){
                        double a=i*Math.PI*2/n+phase*.35;Vec3 center=circle(at,r,a,.8+Math.sin(a)*.08);
                        sword(out,center,UP,.7,phase,b.fade,reduced);
                        if(!reduced)pixels.chip(center.add(0,-.4,0),.075,i%2==0?CYAN:GOLD,.95);
                    }
                }else if(p.variant()==1&&p.angle()>0){
                    int stacks=Math.clamp((int)p.angle(),1,3);
                    for(int i=0;i<stacks;i++){
                        double a=i*Math.PI*2/3+phase*.5;Vec3 center=circle(at,.7,a,.6);
                        sword(out,center,UP,stacks==3?.4:.3,phase,b.fade,true);
                        if(!reduced)pixels.chip(center.add(0,-.24,0),.045,stacks==3?GOLD:CYAN,.96);
                    }
                    pixels.halo(at.add(0,.65,0),f,.6,phase*.2);
                    if(stacks==3)pixels.ring(at.add(0,.09,0),.65,phase*.2,GOLD);
                }else{
                    int n=reduced?2:3;
                    for(int i=0;i<n;i++){
                        double a=i*Math.PI*2/n+phase;Vec3 center=circle(at,r+.3,a,.8);
                        sword(out,center,new Vec3(-Math.sin(a),.3,Math.cos(a)),.95,phase,b.fade,reduced);
                    }
                    pixels.ring(at.add(0,.12,0),r+.12,-phase*.25,GOLD);
                    pixels.crown(at,f,r+.18,phase,4);
                }
                b.ring(at.add(0,.075,0),r,.04,CYAN,.9);
            }
            case CHARGE->{
                // Generic fallback; Heaven Sword owns its overhead recipe in HeavenSwordVfx.
                double stage=Math.clamp(p.angle()/360.0,0,1),length=2.1+stage;
                Vec3 tip=at.add(0,1.15,0).add(f.scale(2.1+stage*.4));
                Vec3 center=SwordModelAsset.centerAtTip(tip,f,length);
                sword(out,center,f,length,phase,b.fade,reduced);
                pixels.halo(center.subtract(f.scale(length*.2)),f,.65+stage*.35,phase*.25);
                pixels.crown(center,f,.38+stage*.2,-phase,6);
                pixels.wake(at.add(0,1.15,0),tip,.25,phase);
            }
            case TRAIL->{
                pixels.wake(at,p.to(),.3,phase);
                if(!reduced)pixels.slash(p.to(),f,.35,phase*.1);
            }
            case PILLAR->{
                double fall=Math.clamp(b.progress*1.3,0,1),length=p.variant()==6?1.0:2.2;
                Vec3 tip=at.lerp(p.to(),fall),center=SwordModelAsset.centerAtTip(tip,f,length);
                if(fall<1)sword(out,center,f,length,phase,b.fade,reduced||p.variant()==6);
                pixels.wake(at,tip,.16,phase);
                if(fall>.7)pixels.impact(p.to(),f,.5,(b.progress-.7/1.3)/(1-.7/1.3));
            }
            case BURST->{
                pixels.impact(at.add(0,.2,0),f,p.angle()==1?r*.65:r,b.progress);
                if(p.variant()==1||p.variant()==3||p.variant()==7)pixels.ring(at,r*(.4+b.progress*.6),phase*.1,p.variant()==1?VIOLET:GOLD);
            }
            default->{b.ring(at,r,.07,CYAN,.9);b.decal(at,r,SWORD_SIGIL,WHITE,.9);}
        }
        out.addAll(b.result());
        if(out.size()>4096)throw new IllegalStateException("Lingyuan VFX composition budget exceeded");
        return List.copyOf(out);
    }
    private static void sword(List<Quad> out,Vec3 center,Vec3 f,double length,double phase,float alpha,boolean reduced){
        layer(out,reduced?"body_lod":"body",center,f,length,phase,alpha);
    }
    private static void layer(List<Quad> out,String name,Vec3 center,Vec3 f,double length,double phase,float alpha){
        SwordModelAsset.append(out,name,center,f,length,phase,alpha);
    }
}

