package com.myriad.character.ability;

import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Original finite mesh recipes; independent of renderer, gameplay and third-party libraries. */
public final class AbilityVfxGeometry {
    public static final int RETICLE=0,INVALID=1,SEAL=2,BURST=3,ARC=4,BEAM=5,PILLAR=6,
            SHIELD=7,TRAIL=8,FLIGHT=9,CHARGE=10,WALL=11,CANCEL=12;
    private static final int[] BODY={0x777777,0x42BDFF,0xFF762A,0x6489AA,0x40EAC1,0x62C9E6,0x63B9F5,0x60B960,0xD76F3C,0x69CEC8,0xB7DAF5,0x42D9BD,0x5DDEFF};
    private static final int[] EDGE={0xD8D8D8,0xF4FCFF,0xFFE0A0,0xFAE9B8,0xF0FFF9,0xF6F3D1,0xF1FCFF,0xF1F2BE,0xFFDB9D,0xFFF7CE,0xF3FCFF,0xF1C76B,0xF1FCFF};
    public record Quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha,int material,boolean reverseV,float startU,float endU,float startV,float endV) {
        public Quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha,int material,boolean reverseV,float startU,float endU){this(a,b,c,d,color,alpha,material,reverseV,startU,endU,0,1);}
        public Quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha){this(a,b,c,d,color,alpha,0,false,0,1);}
        public Quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha,int material){this(a,b,c,d,color,alpha,material,false,0,1);}
        public Quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float alpha,int material,boolean reverseV){this(a,b,c,d,color,alpha,material,reverseV,0,1);}
    }
    /** Complex closed models have their own budget; other role recipes stay at 192. */
    public static int maxQuads(AbilityVfxPayload p) {
        return p.theme()==5&&p.kind()!=RETICLE&&p.kind()!=INVALID&&p.kind()!=CANCEL?4096
                :p.theme()==12&&p.kind()==FLIGHT?512:192;
    }
    private final List<Quad> quads=new ArrayList<>();
    private final int theme,variant,color,bright;private final float fade;private final double time;
    private AbilityVfxGeometry(AbilityVfxPayload p,float age) {
        theme=p.theme();variant=p.variant();color=BODY[theme];bright=EDGE[theme];
        // Hold the body at full intensity. Quadratic fading made frequently renewed
        // 2-5 tick previews/projectiles almost invisible after their first tick.
        time=age;
        fade=Math.min(1,(age+1)/1.5f)*Math.min(1,(p.life()-age)/Math.max(1,p.life()*.3f));
    }
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean reduced) {
        if(!p.valid()||age<0||age>=p.life())return List.of();
        if(com.myriad.player.LevelUpBeamGeometry.owns(p))return com.myriad.player.LevelUpBeamGeometry.build(p,age,reduced);
        if(p.theme()==0&&p.kind()!=RETICLE&&p.kind()!=INVALID&&p.kind()!=CANCEL)
            return com.myriad.character.skills.yingxiao.rework.NightVfxGeometry.build(p,age,reduced);
        if(p.kind()!=RETICLE&&p.kind()!=INVALID&&p.kind()!=CANCEL){
            return switch(p.theme()){
                case 1->com.myriad.character.skills.tianxing.rework.ThunderVfxGeometry.build(p,age,reduced);
                case 2->com.myriad.character.skills.fenjue.rework.FlameVfxGeometry.build(p,age,reduced);
                case 3->com.myriad.character.skills.xuanchen.rework.InkVfxGeometry.build(p,age,reduced);
                case 4->com.myriad.character.skills.sulan.rework.WindVfxGeometry.build(p,age,reduced);
                case 5->com.myriad.character.skills.lingyuan.rework.SwordVfxGeometry.build(p,age,reduced);
                case 6->com.myriad.character.skills.hanze.rework.IceVfxGeometry.build(p,age,reduced);
                case 7->com.myriad.character.skills.qingluo.rework.HerbVfxGeometry.build(p,age,reduced);
                case 8->com.myriad.character.skills.chixiao.rework.WarVfxGeometry.build(p,age,reduced);
                case 9->com.myriad.character.skills.yunsheng.rework.SpiritVfxGeometry.build(p,age,reduced);
                case 11->com.myriad.character.skills.keheng.KehengVfx.build(p,age,reduced);
                case 10->com.myriad.character.skills.shuo.ShuoVfx.build(p,age,reduced);
                case 12->com.myriad.character.skills.yike.YikeVfxGeometry.build(p,age,reduced);
                default->List.of();
            };
        }
        var g=new AbilityVfxGeometry(p,age);g.shape(p,reduced);
        return p.theme()==10?com.myriad.character.skills.shuo.ShuoBrightness.apply(g.quads)
                :p.theme()==11||p.theme()==12?RosterVfxTuning.brighten(g.quads):List.copyOf(g.quads);
    }
    private void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int rgb,float alpha) {
        if(quads.size()<192&&alpha>0)quads.add(new Quad(a,b,c,d,rgb,Math.clamp(alpha,0,1)));
    }
    private void line(Vec3 a,Vec3 b,Vec3 normal,double width,int rgb,float opacity) {
        Vec3 axis=b.subtract(a);if(axis.lengthSqr()<1E-8)return;
        Vec3 side=axis.cross(normal);if(side.lengthSqr()<1E-8)side=axis.cross(new Vec3(1,0,0));
        if(side.lengthSqr()<1E-8)side=axis.cross(new Vec3(0,0,1));
        side=side.normalize().scale(width*.5);
        quad(a.add(side),a.subtract(side),b.subtract(side),b.add(side),rgb,opacity);
        // Opposite winding makes planar effects readable from either side, with depth testing intact.
        quad(b.add(side),b.subtract(side),a.subtract(side),a.add(side),rgb,opacity);
    }
    private void glowLine(Vec3 a,Vec3 b,Vec3 n,double width,int rgb) {
        line(a,b,n,width*2.8,rgb,fade*.26f);line(a,b,n,width*1.45,rgb,fade*.9f);
        line(a,b,n,width*.35,bright,fade);
    }
    /** Closed four-sided prism: a readable body from above, beside and in front. */
    private void tube(Vec3 a,Vec3 b,double width) {
        Vec3 axis=b.subtract(a);if(axis.lengthSqr()<1E-8)return;
        Vec3 forward=axis.normalize(),side=forward.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<1E-8)side=forward.cross(new Vec3(1,0,0));
        side=side.normalize();Vec3 vertical=side.cross(forward).normalize();
        for(int i=0;i<4;i++) {
            double t=i*Math.PI/2+Math.PI/4,u=t+Math.PI/2;
            Vec3 left=side.scale(Math.cos(t)*width).add(vertical.scale(Math.sin(t)*width));
            Vec3 right=side.scale(Math.cos(u)*width).add(vertical.scale(Math.sin(u)*width));
            int rgb=i==0?bright:color;float opacity=fade*(i==0?.95f:.72f);
            quad(a.add(left),a.add(right),b.add(right),b.add(left),rgb,opacity);
            quad(b.add(left),b.add(right),a.add(right),a.add(left),rgb,opacity);
        }
    }
    /** Faceted energy core, not a camera-facing flat disc. */
    private void core(Vec3 at,double radius) {
        Vec3 top=at.add(0,radius,0),bottom=at.add(0,-radius,0);
        for(int i=0;i<4;i++) {
            double a=i*Math.PI/2+time*.1,b=a+Math.PI/2;
            Vec3 left=circle(at,radius,a,0),right=circle(at,radius,b,0);
            int rgb=i%2==0?bright:color;
            quad(top,left,right,right,rgb,fade*.88f);quad(right,left,top,top,rgb,fade*.88f);
            quad(bottom,right,left,left,color,fade*.76f);quad(left,right,bottom,bottom,color,fade*.76f);
        }
    }
    /** Extruded arc rim leaves the centre open and does not enlarge its radius. */
    private void rim(Vec3 at,double radius,double start,double sweep,double height,int steps) {
        for(int i=0;i<steps;i++) {
            double a=start+sweep*i/steps,b=start+sweep*(i+1)/steps;
            Vec3 left=circle(at,radius,a,0),right=circle(at,radius,b,0);
            Vec3 upperLeft=left.add(0,height,0),upperRight=right.add(0,height,0);
            float opacity=fade*(i%3==0?.58f:.3f);
            quad(left,right,upperRight,upperLeft,color,opacity);
            quad(upperLeft,upperRight,right,left,color,opacity);
        }
    }
    private Vec3 circle(Vec3 at,double r,double a,double y){return at.add(Math.cos(a)*r,y,Math.sin(a)*r);}
    private void ring(Vec3 at,double r,double start,double sweep,double width,int rgb,int steps,float opacity) {
        for(int i=0;i<steps;i++) {
            double a=start+sweep*i/steps,b=start+sweep*(i+1)/steps;
            double jitter=1;
            Vec3 a0=circle(at,(r-width*.5)*jitter,a,0),a1=circle(at,(r+width*.5)*jitter,a,0);
            Vec3 b0=circle(at,(r-width*.5)*jitter,b,0),b1=circle(at,(r+width*.5)*jitter,b,0);
            quad(a0,a1,b1,b0,rgb,opacity);quad(b0,b1,a1,a0,rgb,opacity);
        }
    }
    private void sigil(Vec3 at,double r,int steps) {
        double spin=time*(theme==3?.006:.02)*(variant%2==0?1:-1);
        ring(at,r,spin,Math.PI*2,.095,color,steps,fade*.9f);
        ring(at.add(0,.025,0),r*.82,-spin,Math.PI*2,.045,bright,steps,fade*.8f);
        int marks=theme==3?8:theme==1?6:4;
        for(int i=0;i<marks;i++) {
            double a=i*Math.PI*2/marks+spin;
            Vec3 tip=circle(at,r*.94,a,.02),inner=circle(at,r*.74,a,.02);
            line(tip,inner,new Vec3(0,1,0),.045,bright,fade*.7f);
            if(theme==3)line(inner,circle(at,r*.74,a+.14,.02),new Vec3(0,1,0),.03,color,fade*.7f);
        }
    }
    private void shape(AbilityVfxPayload p,boolean reduced) {
        Vec3 at=p.from(),end=p.to(),up=new Vec3(0,1,0);
        if((p.kind()==BEAM||p.kind()==TRAIL||p.kind()==CHARGE||p.kind()==FLIGHT)&&at.distanceToSqr(end)<1E-8)return;
        Vec3 direction=end.subtract(at).normalize();
        if(direction.lengthSqr()<.01)direction=new Vec3(0,0,1);
        double radius=Math.max(.08,p.radius()),progress=Math.clamp(time/p.life(),0,1);
        int steps=reduced?16:28;
        switch(p.kind()) {
            case RETICLE,INVALID -> {
                int rgb=p.kind()==INVALID?0xFF535B:bright;
                ring(at,radius,0,Math.PI*2,.025,rgb,steps,.7f*fade);
                for(int i=0;i<4;i++) {
                    double a=Math.PI*.5*i+(p.kind()==INVALID?Math.PI*.25:0);
                    line(circle(at,.12,a,0),circle(at,.4,a,0),up,.045,rgb,.9f*fade);
                }
            }
            case SEAL -> {
                rim(at,radius,0,Math.PI*2,p.life()>=20?.38:.18,reduced?8:12);
                sigil(at,radius,reduced?12:16);
                if(p.life()>=20) {
                    if(theme==2)for(int i=0;i<(reduced?5:8);i++) {
                        double a=i*Math.PI*2/(reduced?5:8),r=radius*.89;
                        Vec3 base=circle(at,r,a,.05),tip=base.add(Math.sin(time*.21+i)*.2,.6+.55*(.5+.5*Math.sin(time*.17+i)),0);
                        tube(base,tip,.08);
                    }
                    else if(theme==3)for(int i=0;i<4;i++) {
                        double a=i*Math.PI/2;
                        line(circle(at,radius*.58,a,.025),circle(at,radius*.58,a+Math.PI/2,.025),up,.025,bright,fade*.4f);
                        tube(circle(at,radius*.95,a,.02),circle(at,radius*.95,a,1.05),.06);
                    }
                    else if(theme==4)ring(at.add(0,.18+.1*Math.sin(time*.15),0),radius*.92,time*.025,Math.PI*1.7,.025,bright,steps,fade*.4f);
                }
            }
            case BURST -> {
                core(at.add(0,.4+progress*.25,0),Math.min(.48,radius*.32)*(1-progress*.45));
                ring(at,radius*(.3+.7*progress),0,Math.PI*2,.075,color,steps,fade*.6f);
                int rays=reduced?6:10;
                for(int i=0;i<rays;i++) {
                    double a=i*2.399963+variant*.31,r=radius*(.25+.7*progress);
                    Vec3 a0=circle(at,r*.65,a,.12+progress*.25),b0=circle(at,r,a,.16+progress*.6);
                    glowLine(a0,b0,up,.085,i%3==0?bright:color);
                }
                if(theme==0) { // distinct crossing cuts, not an opaque smoke cloud
                    glowLine(at.add(-radius*.5,.2,0),at.add(radius*.5,.9,0),new Vec3(0,0,1),.05,color);
                    glowLine(at.add(radius*.5,.2,0),at.add(-radius*.5,.9,0),new Vec3(0,0,1),.05,bright);
                }
            }
            case ARC -> {
                double yaw=Math.atan2(direction.z,direction.x),sweep=Math.toRadians(p.angle());
                double sweepStart=yaw-sweep*.5+progress*sweep*.32;
                rim(at.add(0,-.12,0),radius,sweepStart,sweep*(1-progress*.25),.32,reduced?10:18);
                ring(at,radius,sweepStart,sweep*(1-progress*.25),radius*.17,color,steps,fade*.88f);
                ring(at.add(0,.016,0),radius*1.02,sweepStart,sweep*(1-progress*.25),.028,bright,steps,fade*.9f);
            }
            case BEAM,TRAIL,CHARGE -> {
                tube(at,end,Math.max(.055,radius*.65));
                Vec3 side=direction.cross(up);if(side.lengthSqr()<.01)side=new Vec3(1,0,0);side=side.normalize();
                if(theme==1) {
                    Vec3 last=at;int segments=12;
                    for(int i=1;i<=segments;i++) {
                        Vec3 next=at.lerp(end,i/(double)segments).add(side.scale(i==segments?0:Math.sin(i*8.17+variant)*radius*.65));
                        glowLine(last,next,up,Math.max(.035,radius*.3),color);last=next;
                        if(!reduced&&i%3==0)glowLine(next,next.add(side.scale(radius*1.8)).add(0,.3,0),up,.025,bright);
                    }
                } else {
                    glowLine(at,end,up,Math.max(.035,radius),color);
                    glowLine(at,end,side,Math.max(.02,radius*.42),bright);
                    if(!reduced)for(int i=0;i<6;i++) {
                        double a=time*.3+i;
                        Vec3 mid=at.lerp(end,i/6.0).add(side.scale(Math.sin(a)*radius));
                        line(mid,mid.add(direction.scale(.2)),up,.025,bright,fade*.5f);
                    }
                }
            }
            case PILLAR -> {
                tube(at,end,.17);core(at.add(0,.4,0),.27);
                sigil(at,Math.max(.4,radius),reduced?10:12);
                Vec3 top=end;
                if(theme==1){Vec3 last=at;for(int j=1;j<=9;j++){
                    Vec3 next=at.lerp(top,j/9.0).add(j==9?0:Math.sin(j*9.1+variant)*.28,0,j==9?0:Math.cos(j*9.1+variant)*.22);
                    glowLine(last,next,new Vec3(0,0,1),.08,color);last=next;}}
                glowLine(at,top,new Vec3(0,0,1),.12,color);glowLine(at,top,new Vec3(1,0,0),.06,bright);
                if(!reduced)for(int i=0;i<4;i++) {
                    Vec3 a=at.lerp(top,i/4.0).add(Math.sin(i*2+variant)*.3,0,Math.cos(i*2+variant)*.3);
                    Vec3 b=at.lerp(top,(i+1)/4.0).add(Math.sin((i+1)*2+variant)*.3,0,Math.cos((i+1)*2+variant)*.3);
                    glowLine(a,b,up,.035,color);
                }
            }
            case SHIELD -> {
                rim(at.add(0,.25,0),radius,0,Math.PI*2,1.15,reduced?8:12);
                ring(at.add(0,.12,0),radius,0,Math.PI*2,.035,color,steps,fade*.55f);
                for(int i=0;i<(reduced?4:6);i++) {
                    double angle=i*Math.PI/3+time*.03;Vec3 last=circle(at,radius,angle,.15);
                    for(int j=1;j<=6;j++) {
                        double t=j/6.0;Vec3 next=circle(at,radius*Math.sin(Math.PI*t),angle,.15+t*1.6);
                        line(last,next,up,.06,i%2==0?color:bright,fade*.8f);last=next;
                    }
                }
            }
            case FLIGHT -> {
                end=at.lerp(end,Math.clamp(time,0,1));
                tube(at,end,Math.max(.05,radius*.24));
                glowLine(at,end,up,Math.max(.03,radius*.4),color);
                Vec3 side=direction.cross(up);if(side.lengthSqr()<.01)side=new Vec3(1,0,0);side=side.normalize();
                Vec3 vertical=side.cross(direction).normalize();
                double r=radius*(1+.06*Math.sin(time));
                if(theme==0){
                    Vec3 tip=end.add(direction.scale(.42)),tail=end.subtract(direction.scale(.28));
                    tube(tail,tip,.08);
                    quad(tip,end.add(side.scale(.13)),tail,end.subtract(side.scale(.13)),color,fade*.7f);
                    quad(tip,end.subtract(side.scale(.13)),tail,end.add(side.scale(.13)),color,fade*.7f);
                    glowLine(tail,tip,vertical,.025,bright);
                    return;
                }
                if(theme==3){
                    core(end,r*.65);
                    for(int i=0;i<4;i++){
                        double a=time*.12+i*Math.PI/2,b=a+Math.PI/2;
                        Vec3 left=end.add(side.scale(Math.cos(a)*r)).add(vertical.scale(Math.sin(a)*r));
                        Vec3 right=end.add(side.scale(Math.cos(b)*r)).add(vertical.scale(Math.sin(b)*r));
                        glowLine(left,right,direction,.035,color);
                    }
                    return;
                }
                if(theme==4){
                    rim(end.add(0,-.08,0),r,time*.25,Math.PI*.85,.16,10);
                    ring(end.add(0,.015,0),r,time*.25,Math.PI*.85,.08,color,12,fade*.7f);
                    ring(end.add(0,.025,0),r,time*.25+Math.PI,Math.PI*.85,.025,bright,12,fade*.9f);
                    return;
                }
                core(end,r*.65);
                for(int i=0;i<16;i++) {
                    double a=i*Math.PI/8+time*.28,b=(i+1)*Math.PI/8+time*.28;
                    Vec3 left=end.add(side.scale(Math.cos(a)*r)).add(vertical.scale(Math.sin(a)*r));
                    Vec3 right=end.add(side.scale(Math.cos(b)*r)).add(vertical.scale(Math.sin(b)*r));
                    line(left,right,direction,.04,i%4==0?bright:color,fade*.8f);
                }
                glowLine(end.subtract(direction.scale(.22)),end.add(direction.scale(.18)),up,.09,bright);
            }
            case WALL -> {
                Vec3 side=direction.cross(up).normalize();if(side.lengthSqr()<.01)side=new Vec3(1,0,0);
                for(int i=0;i<5;i++) {
                    Vec3 base=at.add(side.scale(radius*(i/2.0-1)));
                    tube(base,base.add(0,3.6,0),.045);
                }
                for(int i=0;i<8;i++) {
                    Vec3 a=at.subtract(side.scale(radius)).add(0,.1+i*.5,0);
                    Vec3 b=at.add(side.scale(radius)).add(0,.1+i*.4,0);
                    glowLine(a,b,direction,.11,color);
                }
            }
        }
    }
}
