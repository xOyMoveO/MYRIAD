package com.myriad.client.firstperson;

import com.myriad.network.AbilityVfxPayload;
import com.myriad.client.firstperson.FlameDaoMotion.Frame;
import com.myriad.client.firstperson.FlameDaoMotion.Pose;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Only the server-confirmed Flame Slash: two quick opposite descending cuts, no hit scheduling. */
public final class FlameSlashMotion {
    private record Key(double t,Pose p){}
    private static final Pose IDLE=FlameDaoMotion.IDLE;
    private static final Pose LEFT_HIGH=new Pose(-.36,.01,-1.48,-10,-8,128);
    private static Key k(double t,double x,double y,double z,double pitch,double yaw,double roll){return new Key(t,new Pose(x,y,z,pitch,yaw,roll));}
    private static final Key[][] CUTS={
        {new Key(0,IDLE),k(.20,.48,.01,-1.48,-10,8,18),k(.42,.08,-.24,-1.93,-42,0,64),
            k(.65,-.40,-.56,-1.81,-67,-8,128),new Key(1,LEFT_HIGH)},
        {new Key(0,LEFT_HIGH),new Key(.20,LEFT_HIGH),k(.42,.08,-.24,-1.93,-42,0,64),
            k(.65,.48,-.72,-1.81,-80,8,18),new Key(1,IDLE)}
    };
    private double start=Double.NEGATIVE_INFINITY,duration;
    private long latest=Long.MIN_VALUE;
    private Pose entry=IDLE;
    private double right,left;
    private static final Frame IDLE_FRAME=new Frame(IDLE,IDLE,IDLE,-1,-1,0,0,0,0);
    public static boolean owns(AbilityVfxPayload p){return p.valid()&&p.theme()==2&&p.variant()==2&&p.kind()==ARC;}
    public boolean accept(AbilityVfxPayload p,double now,double elapsed,Frame fallback){
        if(!p.valid()||!Double.isFinite(now)||!Double.isFinite(elapsed))return false;
        if(p.kind()==CANCEL&&(p.angle()==0||p.angle()==1&&p.theme()==2&&p.variant()==2)){
            if(p.started()<latest)return false;latest=p.started();interrupt();return true;
        }
        double gestureTicks=Math.min(8,p.life());
        if(!owns(p)||p.started()<=latest||elapsed<0||elapsed>=gestureTicks)return false;
        entry=fallback.pose();right=FlameDaoRig.turn(fallback,false);left=FlameDaoRig.turn(fallback,true);
        latest=p.started();duration=gestureTicks;start=now-elapsed;return true;
    }
    public boolean active(double now){return Double.isFinite(now)&&now>=start&&now<start+duration;}
    public double started(){return start;}
    public double duration(){return duration;}
    public void interrupt(){start=Double.NEGATIVE_INFINITY;duration=0;}
    public void reset(){interrupt();latest=Long.MIN_VALUE;entry=IDLE;right=left=0;}
    private Pose pose(int cut,double t){
        var keys=CUTS[cut];
        for(int i=1;i<keys.length;i++)if(t<=keys[i].t){
            double u=Math.clamp((t-keys[i-1].t)/(keys[i].t-keys[i-1].t),0,1);u=u*u*(3-2*u);
            var a=i==1&&cut==0?entry:keys[i-1].p;var b=keys[i].p;
            return new Pose(mix(a.x(),b.x(),u),mix(a.y(),b.y(),u),mix(a.z(),b.z(),u),
                mix(a.pitch(),b.pitch(),u),mix(a.yaw(),b.yaw(),u),mix(a.roll(),b.roll(),u));
        }return keys[keys.length-1].p;
    }
    private Frame raw(int cut,double t,double r,double l){
        double at=Math.clamp(t,.205,.645);var p=pose(cut,t);
        return new Frame(p,pose(cut,at-.002),pose(cut,at+.002),t,cut,r,l,0,0);
    }
    public Frame frame(double now){
        if(!active(now))return IDLE_FRAME;
        double progress=(now-start)/duration;int cut=progress<.45?0:1;
        double t=cut==0?progress/.45:(progress-.45)/.55;
        var anchor=raw(cut,.42,0,0);double r=right,l=left;
        if(cut==1){var p=LEFT_HIGH;var idle=new Frame(p,p,p,-1,-1,0,0,0,0);r=FlameDaoRig.turn(idle,false);l=FlameDaoRig.turn(idle,true);}
        var f=raw(cut,t,r,l);
        return new Frame(f.pose(),f.before(),f.after(),t,cut,r,l,FlameDaoRig.target(anchor,false),FlameDaoRig.target(anchor,true));
    }
    private static double mix(double a,double b,double t){return a+(b-a)*t;}
}
