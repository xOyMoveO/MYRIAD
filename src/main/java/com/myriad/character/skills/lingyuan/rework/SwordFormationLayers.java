package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.AbilityMeshBuilder;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Role-specific silhouettes using the existing animated, transparent sword atlases. */
final class SwordFormationLayers {
    private static final int ICE=0xE2FCFF, CYAN=0x64DFFF, GOLD=0xFFD987, VIOLET=0x9B85F4;
    private SwordFormationLayers(){}

    static boolean append(AbilityVfxPayload p,AbilityMeshBuilder b,SwordPixelLayers px,List<Quad> out,double phase){
        Vec3 at=p.from(),f=direction(at,p.to());
        switch(p.variant()){
            case 0 -> {
                if(p.kind()!=SHIELD||p.angle()<1||p.angle()>4)return false;
                for(int i=0;i<(int)p.angle();i++){
                    Vec3 slot=at.add(SwordVisuals.passiveSlot(i));
                    SwordModelAsset.append(out,"body_lod",slot,UP,.61,0,b.fade);
                    px.chip(slot.add(0,-.30,0),.065,CYAN,.8);
                    b.line(slot.add(0,-.44,0),slot.add(0,-.29,0),f,.025,ICE,.9);
                }
            }
            case 1 -> {
                if(p.kind()!=SHIELD)return false;
                Vec3 flat=horizontal(f),right=side(flat),base=at.add(right.scale(.63)).add(flat.scale(.23)).add(0,1.04,0);
                if(p.angle()==360||p.angle()==0&&p.life()>1){
                    // The shield from the empowered attack is separate from the three charge pips.
                    for(int i=0;i<3;i++){
                        Vec3 edge=base.add(right.scale(-.16*i)).add(0,.12*i,0);
                        px.bar(edge,UP.add(right.scale(.8)),.21,.035,.025,GOLD,.7);
                    }
                }else{
                    int count=Math.clamp((int)p.angle(),0,3);
                    for(int i=0;i<count;i++){
                        Vec3 pip=base.add(0,i*.14,0);
                        b.line(pip.subtract(flat.scale(.085)),pip.add(flat.scale(.085)),right,.05,GOLD,.95);
                        b.line(pip.subtract(flat.scale(.09)).add(0,.018,0),pip.add(flat.scale(.09)).add(0,.018,0),UP,.018,ICE,.9);
                    }
                    if(count==3)px.wake(base.add(0,-.13,0),base.add(0,.57,0),.065,phase);
                }
            }
            case 2 -> {
                if(p.kind()!=TRAIL&&p.kind()!=ARC)return false;
                if(p.kind()==ARC){px.slash(at.add(f.scale(.24)),f,.56,Math.PI/2);break;}
                Vec3 to=p.to(),right=side(f);
                Vec3 from=at.lerp(to,Math.clamp(b.progress*.8,0,.8));
                px.clippedWake(from,to,.14,phase);
                Vec3 lift=right.cross(f).normalize().scale(.07);
                b.line(from,to,UP,.035,ICE,.99);
                b.line(from.add(lift).add(right.scale(.06)),to.add(lift).add(right.scale(.06)),UP,.045,VIOLET,.73);
                if(!b.reduced){
                    double size=Math.min(.24,from.distanceTo(to)*.43);
                    // The highlight's second image is slightly offset; leave that margin too.
                    if(size>.08)px.slash(to.subtract(f.scale(size+.06)),f,size,Math.PI/2);
                }
            }
            case 3 -> {
                if(p.kind()==SHIELD&&p.anchor()>=0)return true; // Replaced by the timed shield fan.
                if(p.kind()!=WALL)return false;
                Vec3 flat=horizontal(f),right=side(flat);
                boolean guarding=p.angle()<2;
                double opening=p.angle()==0?.3+.7*Math.min(1,b.age/3):1;
                for(int i=-2;i<=2;i++){
                    if((!guarding||b.reduced)&&i%2!=0)continue;
                    double angle=i*opening*.43;
                    Vec3 rim=flat.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
                    Vec3 position=at.add(rim.scale(.78+Math.abs(i)*.105)).add(0,.87-Math.abs(i)*.045,0);
                    Vec3 blade=UP.add(right.scale(i*.31*opening)).add(flat.scale(.22)).normalize();
                    SwordModelAsset.appendOriented(out,"body_lod",position,blade,rim.cross(UP),i==0?.94:.78,
                            b.fade*(guarding?.92f:.42f));
                    if(guarding){
                        Vec3 heel=position.subtract(blade.scale(.37)),tip=position.add(blade.scale(.4));
                        px.wake(heel,tip,.07,phase);
                        b.line(heel.add(rim.scale(.065)),tip.add(rim.scale(.065)),rim,.021,ICE,.93);
                    }else if(!b.reduced)px.chip(position.subtract(UP.scale(.34)),.08,VIOLET,.6);
                }
            }
            case 5 -> {
                if(p.kind()==SEAL&&p.angle()!=359&&p.life()>=20){
                    for(int i=0;i<4;i++){
                        Vec3 end=stake(at,p.radius(),i),rim=direction(at,end);
                        Vec3 tip=end.add(0,.9*Math.pow(1-Math.min(1,b.age/5),2),0);
                        Vec3 blade=UP.scale(-1);
                        SwordModelAsset.appendOriented(out,"body_lod",SwordModelAsset.centerAtTip(tip,blade,.96),
                                blade,side(rim),.96,b.fade);
                        b.line(end.subtract(rim.scale(.27)),end.add(rim.scale(.27)),UP,.045,GOLD,.95);
                        if(!b.reduced||i%2==0)px.chip(tip.add(0,.03,0),.12,GOLD,.8);
                    }
                }else if(p.kind()==BURST&&p.radius()>3){
                    for(int i=0;i<4;i++){
                        Vec3 a=stake(at,p.radius(),i),z=stake(at,p.radius(),i+1);
                        Vec3 end=a.lerp(z,Math.min(1,.16+b.progress*2.6));
                        b.line(a,end,UP,.058,ICE,.98);
                        px.wake(a.add(0,.035,0),end.add(0,.035,0),.10,phase);
                        if(!b.reduced)b.line(a.add(0,.105,0),end.add(0,.105,0),UP,.035,VIOLET,.74);
                    }
                }else return false;
            }
            case 6 -> {
                if(p.kind()==SEAL&&p.angle()!=359&&p.life()>=20){
                    // Broken edge only: never fill the playable area with a decal.
                    for(int i=0;i<16;i++){
                        double a=i*Math.PI/8;
                        Vec3 left=circle(at,p.radius(),a,0),right=circle(at,p.radius(),a+.19,0);
                        b.line(left,right,UP,.069,i%4==0?GOLD:CYAN,.86);
                        if(i%2==0)b.line(left,circle(at,p.radius()-.52,a,0),UP,.044,ICE,.86);
                        if(i%(b.reduced?8:4)==0)px.chip(left.add(0,.1,0),.15,CYAN,.62);
                    }
                }else if(p.kind()==PILLAR){
                    double fall=Math.clamp(.56+b.age*.20,0,1);
                    Vec3 tip=at.lerp(p.to(),fall);
                    if(fall<1)SwordModelAsset.append(out,"body_lod",SwordModelAsset.centerAtTip(tip,f,1.45),f,1.45,0,b.fade);
                    px.wake(at.lerp(tip,.29),tip,.22,phase);
                    px.impact(p.to(),f,p.radius(),b.progress);
                    if(!b.reduced)px.chip(p.to().add(0,.2+.5*b.progress,0),.19,CYAN,.8);
                }else if(p.kind()==BEAM){
                    px.impact(p.to(),f,.46,b.progress);
                }else return false;
            }
            default -> {return false;}
        }
        return true;
    }

    private static Vec3 stake(Vec3 at,double r,int i){return circle(at,r*.85,Math.PI/4+i*Math.PI/2,.047);}
    private static Vec3 horizontal(Vec3 direction){
        Vec3 f=direction.multiply(1,0,1).normalize();return f.lengthSqr()<1E-8?Z:f;
    }
}
