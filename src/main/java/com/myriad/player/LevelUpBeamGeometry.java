package com.myriad.player;

import com.myriad.character.ability.AbilityMeshBuilder;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityMeshBuilder.*;

/** Finite multi-layer gold/jade light: fast rise, held peak, gentle breakup. No beacon entity. */
public final class LevelUpBeamGeometry {
    private LevelUpBeamGeometry(){}
    public static boolean owns(AbilityVfxPayload p){
        return p.theme()==0&&p.variant()==7&&p.kind()==AbilityVfxGeometry.PILLAR&&p.angle()==358&&p.anchor()==p.owner();
    }
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low){
        if(!owns(p)||!p.valid()||age<0||age>=p.life())return List.of();
        var b=new AbilityMeshBuilder(p,age,low);var at=p.from().add(0,.05,0);
        double rise=Math.min(1,(age+1)/5.0),height=4.8*(1-Math.pow(1-rise,3));
        double pulse=.94+.06*Math.sin(age*.55),radius=.65*pulse;
        // High-value narrow core, translucent jade sleeve, then an airy outer gold fringe.
        column(b,at,height,.085,6,0xFFF8CF,.54);
        if(low){
            column(b,at,height,.30,4,0xA9E8DC,.16);
            ring(b,at,radius,.055,0xFFD987,8,.8);
        }else{
            for(int band=0;band<3;band++){
                double start=height*band/3;
                column(b,at.add(0,start,0),height/3,.26+band*.03,8,0xB5EDE0,.26*(1-band*.25));
            }
            column(b,at,height,.45,6,0xFFE6A8,.075);
            ring(b,at,radius,.065,0xFFE4A3,12,.88);
            ring(b,at.add(0,.035,0),radius*.74,.035,0xBFEFE4,12,.78);
        }
        // Four small open rune ticks rather than a solid disk under the player.
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2+age*.012;var near=circle(at,radius*.82,a,.055);var far=circle(at,radius*1.18,a,.055);
            b.line(near,far,UP,.035,0xFFF6D0,.85);
        }
        int count=low?3:7;
        for(int i=0;i<count;i++){
            double t=(age*.055+i/(double)count)%1,a=i*2.399963+age*.06;
            var dot=circle(at,.34+i%3*.14,a,.2+t*3.5);double size=.035+.02*Math.sin(t*Math.PI);
            b.line(dot.add(0,-size*2,0),dot.add(0,size*2,0),X,.035,0xFFF2B9,.75*(1-t));
            if(!low)b.line(dot.add(-size,0,0),dot.add(size,0,0),Z,.025,0xD2FFF0,.65*(1-t));
        }
        return b.result();
    }
    private static void column(AbilityMeshBuilder b,Vec3 at,double height,double radius,int sides,int color,double alpha){
        for(int i=0;i<sides;i++){
            double a=i*Math.PI*2/sides,c=(i+1)*Math.PI*2/sides;
            var x=circle(at,radius,a,0);var y=circle(at,radius,c,0);
            b.quad(x,y,y.add(0,height,0),x.add(0,height,0),color,alpha,0);
        }
    }
    private static void ring(AbilityMeshBuilder b,Vec3 at,double radius,double width,int color,int sides,double alpha){
        for(int i=0;i<sides;i++){
            double a=i*Math.PI*2/sides,c=(i+1)*Math.PI*2/sides;
            b.quad(circle(at,radius-width,a,0),circle(at,radius-width,c,0),circle(at,radius,c,0),circle(at,radius,a,0),color,alpha,0);
        }
    }
}
