package com.myriad.client.firstperson;

import com.myriad.network.ThunderSkillViewPayload;
import com.myriad.client.firstperson.ThunderSwordMotion.Pose;
import static com.myriad.network.ThunderSkillViewPayload.*;

/** Six skill-specific own-camera gestures. No combat, input prediction or shared player-model pose. */
public final class ThunderSkillMotion {
    public static final int AIM=10;
    public static final Pose OFF_REST=p(.9,-.7,-1.2,0,0,35),LOW=p(.65,-.60,-1.32,-8,-8,55),
            LEFT_GUARD=p(-.34,.01,-1.48,-10,-8,110),WIPE_GUARD=p(.40,-.42,-1.50,-6,-4,65),
            LEFT_PALM=p(.34,-.18,-1.20,-8,5,38),AIM_PALM=p(.26,-.23,-1.35,-8,5,32);
    public record Frame(Pose main,Pose off,Pose before,Pose after,boolean showOff,boolean showBlade,int action,double progress,double bladeFlip){}
    private record Key(double t,Pose p){}
    private static Pose p(double x,double y,double z,double pitch,double yaw,double roll){return new Pose(x,y,z,pitch,yaw,roll);}
    private static Key k(double t,Pose p){return new Key(t,p);}
    private static final Pose IDLE=ThunderSwordMotion.IDLE;
    private static final Key[][] MAIN={
        {k(0,IDLE),k(.20,p(.17,-.20,-1.44,-4,0,65)),k(.36,p(.14,.04,-1.52,8,0,65)),
                k(.62,p(.13,-.56,-1.53,8,0,65)),k(.75,p(.13,-.56,-1.53,8,0,65)),k(1,IDLE)},
        {k(0,IDLE),k(.30,LOW),k(1,LOW)},
        {k(0,LOW),k(.35,LOW),k(1,IDLE)},
        {k(0,IDLE),k(.27,p(.105,-.26,-1.24,0,0,18)),k(.55,p(.105,-.26,-1.24,0,0,18)),
                k(.76,p(.33,-.28,-1.24,0,0,35)),k(1,IDLE)},
        {k(0,IDLE),k(.32,LEFT_GUARD),k(1,LEFT_GUARD)},
        {k(0,LEFT_GUARD),k(.25,p(-.15,-.04,-1.52,-20,-5,92)),k(.47,p(.10,-.24,-1.64,-42,0,55)),
                k(.67,p(.50,-.53,-1.46,-65,8,5)),k(.80,p(.51,-.48,-1.42,-56,5,12)),k(1,IDLE)},
        {k(0,IDLE),k(.18,WIPE_GUARD),k(1,WIPE_GUARD)},
        {k(0,WIPE_GUARD),k(.25,p(.28,-.32,-1.65,-15,-4,65)),k(.55,p(.55,-.35,-1.43,-35,8,35)),k(1,IDLE)},
        {k(0,LOW),k(.40,LOW),k(1,IDLE)}
    };
    private static final Key[] PALM_READY={k(0,OFF_REST),k(.30,LEFT_PALM),k(1,LEFT_PALM)};
    private static final Key[] PALM_FIRE={k(0,LEFT_PALM),k(.30,p(.18,-.12,-1.52,-6,0,32)),k(.55,p(.18,-.12,-1.52,-6,0,32)),k(1,OFF_REST)};
    private static final Key[] MUDRA={k(0,OFF_REST),k(.27,p(.105,-.26,-1.24,0,0,18)),k(.55,p(.105,-.26,-1.24,0,0,18)),
            k(.76,p(.33,-.28,-1.24,0,0,35)),k(1,OFF_REST)};
    private static final Key[] SECOND_GRIP={k(0,OFF_REST),k(.20,p(.2,-.4,-1.4,0,0,35)),k(.75,p(.2,-.4,-1.4,0,0,35)),k(1,OFF_REST)};
    private int action=-1,token=-1,ended=-1;
    private double start,duration;
    private Pose entry=IDLE,offEntry=OFF_REST;
    public void reset(){action=-1;token=ended=-1;start=duration=0;entry=IDLE;offEntry=OFF_REST;}
    public void interrupt(){ended=Math.max(ended,token);action=-1;}
    public boolean active(double tick){return action>=0&&Double.isFinite(tick)&&tick>=start&&tick<start+duration;}
    public int action(){return action;}
    public static boolean ready(int action){return action==ARC_READY||action==SLASH_READY||action==JUDGMENT_READY;}
    public boolean accept(ThunderSkillViewPayload cue,double tick,double elapsed,Pose fallback){
        if(!cue.valid()||!Double.isFinite(tick)||!Double.isFinite(elapsed)||cue.token()<token||cue.token()<=ended)return false;
        if(cue.action()==CANCEL){token=cue.token();interrupt();return true;}
        if(cue.token()==token&&ready(cue.action())&&!ready(action))return false;
        if(elapsed<0||elapsed>=cue.ticks())return false;
        Frame old=sample(tick);entry=active(tick)?old.main():fallback;
        offEntry=active(tick)&&old.showOff()?old.off():OFF_REST;
        if(cue.token()==token&&!ready(cue.action())&&ready(action)&&!active(tick)){
            entry=sample(MAIN[action],1,entry);
            if(action==ARC_READY)offEntry=LEFT_PALM;
        }
        if(cue.action()==NINE_FIRE){entry=LOW;offEntry=AIM_PALM;}
        action=cue.action();token=cue.token();duration=cue.ticks();start=tick-elapsed;return true;
    }
    public Frame sample(double tick){
        if(!active(tick))return idle();
        double t=Math.clamp((tick-start)/duration,0,1);
        var main=sample(MAIN[action],t,entry);
        var off=switch(action){case ARC_READY->sample(PALM_READY,t,offEntry);case ARC_FIRE,NINE_FIRE->sample(PALM_FIRE,t,offEntry);
            case GUARD->sample(MUDRA,t,offEntry);case GROUND,JUDGMENT_READY,JUDGMENT_FIRE->sample(SECOND_GRIP,t,offEntry);default->OFF_REST;};
        boolean two=action==GROUND||action==ARC_READY||action==ARC_FIRE||action==GUARD||action==JUDGMENT_READY||action==JUDGMENT_FIRE||action==NINE_FIRE;
        double flip=action==GROUND?180*smooth(t/.20)*(1-smooth((t-.75)/.25)):0;
        return new Frame(main,off,sample(MAIN[action],Math.max(0,t-.015),entry),sample(MAIN[action],Math.min(1,t+.015),entry),
                two,action!=GUARD,action,t,flip);
    }
    public static Frame idle(){return new Frame(IDLE,OFF_REST,IDLE,IDLE,false,true,-1,1,0);}
    /** Synced placement 106 is the authority. This pose is only a preview, never a release prediction. */
    public static Frame aim(double elapsed){
        double t=smooth(elapsed/4);var main=IDLE.blend(LOW,t);var off=OFF_REST.blend(AIM_PALM,t);
        return new Frame(main,off,main,main,true,true,AIM,t,0);
    }
    private static Pose sample(Key[] keys,double t,Pose entry){
        for(int i=1;i<keys.length;i++)if(t<=keys[i].t){
            double u=smooth((t-keys[i-1].t)/(keys[i].t-keys[i-1].t));
            return (i==1?entry:keys[i-1].p).blend(keys[i].p,u);
        }return keys[keys.length-1].p;
    }
    private static double smooth(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
}
