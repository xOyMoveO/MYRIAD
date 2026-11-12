package com.myriad.client.firstperson;

import com.myriad.client.firstperson.FlameDaoMotion.Pose;
import com.myriad.network.FlameSkillViewPayload;
import static com.myriad.network.FlameSkillViewPayload.*;

/** Four independent gestures, sampled every render frame. No cast prediction or full-body animation. */
public final class FlameSkillMotion {
    public static final int AIM=7;
    private static final Pose IDLE=FlameDaoMotion.IDLE,OFF=p(.9,-.8,-1.15,0,0,36),LOW=p(.65,-.65,-1.30,-8,-8,55),
            PALM=p(.28,.08,-1.32,-10,5,30),PUSH=p(.14,.02,-1.68,-6,0,30),
            CROSS=p(-.82,-.06,-1.38,-8,5,80),CROSS_OFF=p(-.82,.08,-1.28,-8,-5,80),
            OPEN=p(1.18,-.12,-1.42,-10,12,12),JOIN=p(-.35,-.10,-1.30,0,0,26),
            RAISED=p(.12,.52,-1.27,-15,0,65),SLAM=p(.10,.10,-1.65,-155,0,65);
    public record Frame(Pose main,Pose off,boolean showOff,boolean showBlade,int action,double progress){}
    private int action=-1,token=-1,ended=-1;private double start,duration,descendingAt=-1;
    private Pose entry=IDLE,offEntry=OFF;
    private static Pose p(double x,double y,double z,double pitch,double yaw,double roll){return new Pose(x,y,z,pitch,yaw,roll);}
    public static Pose blend(Pose a,Pose b,double u){double t=smooth(u);return p(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,
            a.z()+(b.z()-a.z())*t,a.pitch()+(b.pitch()-a.pitch())*t,a.yaw()+(b.yaw()-a.yaw())*t,a.roll()+(b.roll()-a.roll())*t);}
    public void reset(){action=-1;token=ended=-1;start=duration=0;descendingAt=-1;entry=IDLE;offEntry=OFF;}
    public void interrupt(){ended=Math.max(ended,token);action=-1;descendingAt=-1;}
    public boolean active(double tick){return action>=0&&Double.isFinite(tick)&&tick>=start&&tick<start+duration;}
    public boolean accept(FlameSkillViewPayload cue,double tick,double elapsed,Pose fallback,boolean aiming){
        if(!cue.valid()||!Double.isFinite(tick)||!Double.isFinite(elapsed)||elapsed<0||cue.token()<token||cue.token()<=ended)return false;
        if(cue.action()==CANCEL){token=cue.token();interrupt();return true;}
        if(elapsed>=cue.ticks())return false;
        if(cue.token()==token&&(cue.action()==BURST_READY&&action!=BURST_READY||cue.action()==SEA_FLIGHT&&action!=SEA_FLIGHT))return false;
        var previous=frame(tick);entry=active(tick)?previous.main:fallback;offEntry=active(tick)?previous.off:OFF;
        if(cue.action()==CAGE_FIRE&&aiming){entry=LOW;offEntry=PALM;}
        if(cue.token()==token&&cue.action()==BURST_FIRE){entry=CROSS;offEntry=CROSS_OFF;}
        if(cue.token()==token&&cue.action()==SEA_LAND){entry=RAISED;offEntry=second(RAISED);}
        action=cue.action();token=cue.token();duration=cue.ticks();start=tick-elapsed;descendingAt=-1;return true;
    }
    /** Only the real downward motion changes the flight pose. Never guess descent from a fixed timer. */
    public void vertical(double tick,double velocityY){if(active(tick)&&action==SEA_FLIGHT&&velocityY<-.01&&descendingAt<0)descendingAt=tick;}
    public Frame frame(double tick){
        if(!active(tick))return new Frame(IDLE,OFF,false,true,-1,1);
        double t=Math.clamp((tick-start)/duration,0,1);Pose main,off;boolean blade=true;
        switch(action){
            case CAGE_FIRE->{main=blend(entry,IDLE,t);off=t<.30?blend(offEntry,PUSH,t/.30):blend(PUSH,OFF,(t-.50)/.50);}
            case BURST_READY->{main=blend(entry,CROSS,t/.32);off=blend(offEntry,CROSS_OFF,t/.32);blade=false;}
            case BURST_FIRE->{main=t<.35?blend(entry,OPEN,t/.35):blend(OPEN,IDLE,(t-.55)/.45);
                off=t<.35?blend(offEntry,OPEN,t/.35):blend(OPEN,OFF,(t-.55)/.45);blade=t>.75;}
            case SEA_FLIGHT->{main=descendingAt<0?blend(entry,LOW,t/.22):blend(LOW,RAISED,(tick-descendingAt)/2.5);off=second(main);}
            case SEA_LAND->{main=t<.27?blend(entry,SLAM,t/.27):blend(SLAM,IDLE,(t-.52)/.48);off=second(main);}
            case ARMOR->{main=t<.26?blend(entry,JOIN,t/.26):t<.55?JOIN:t<.76?blend(JOIN,OPEN,(t-.55)/.21):blend(OPEN,IDLE,(t-.76)/.24);
                off=t<.26?blend(offEntry,JOIN,t/.26):t<.55?JOIN:t<.76?blend(JOIN,OPEN,(t-.55)/.21):blend(OPEN,OFF,(t-.76)/.24);blade=t>.85;}
            default->{main=IDLE;off=OFF;}
        }
        return new Frame(main,off,true,blade,action,t);
    }
    private static Pose second(Pose main){return p(.25,main.y()-.18,main.z()-.02,main.pitch(),-5,36);}
    public static Frame aim(double ticks){return new Frame(blend(IDLE,LOW,ticks/4),blend(OFF,PALM,ticks/4),true,true,AIM,smooth(ticks/4));}
    public static double smooth(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
}
