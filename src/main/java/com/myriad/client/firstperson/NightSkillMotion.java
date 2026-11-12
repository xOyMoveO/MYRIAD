package com.myriad.client.firstperson;

import com.myriad.network.NightSkillViewPayload;
import static com.myriad.network.NightSkillViewPayload.*;
import com.myriad.client.firstperson.NightDaggerMotion.Pose;

/** Pure local cosmetic poses. Every skill has a distinct track; no damage, movement or third-person model. */
public final class NightSkillMotion {
    public record Frame(Pose main,Pose off,boolean showOff,boolean showBlade,int action,double progress){}
    private record Key(double time,Pose pose){}
    public static final Pose OFF_REST=new Pose(.9,-.7,-1.06,0,0,-25,0,0);
    public static final Pose FORWARD=new Pose(.46,-.27,-1.06,24,110,0,0,180);
    private static Pose p(double x,double y,double z,double pitch,double yaw,double roll,double grip){return new Pose(x,y,z,pitch,yaw,roll,0,grip);}
    private static Key k(double t,Pose p){return new Key(t,p);}
    private static final Key[][] MAIN={
        {k(0,NightDaggerMotion.IDLE),k(.18,p(.64,-.18,-.91,0,-28,45,0)),
            k(.38,p(.27,-.12,-1.28,28,75,-15,0)),k(.58,p(.22,-.20,-1.25,15,70,-8,0)),k(1,NightDaggerMotion.IDLE)},
        {k(0,NightDaggerMotion.IDLE),k(.26,p(.095,-.28,-1.06,0,0,25,0)),
            k(.65,p(.095,-.28,-1.06,0,0,25,0)),k(1,NightDaggerMotion.IDLE)},
        {k(0,NightDaggerMotion.IDLE),k(.18,FORWARD),k(.62,p(.42,-.28,-1.13,24,110,0,180)),k(1,NightDaggerMotion.IDLE)},
        {k(0,NightDaggerMotion.IDLE),k(.16,FORWARD),k(.55,p(.39,-.29,-1.15,24,110,0,180)),k(1,NightDaggerMotion.IDLE)},
        {k(0,NightDaggerMotion.IDLE),k(.22,p(.46,-.24,-1.05,0,180,0,180)),
            k(.40,p(.29,-.26,-1.05,0,180,0,180)),k(.51,p(.29,-.26,-1.05,0,180,0,180)),
            k(.70,p(.49,-.27,-1.04,0,150,20,160)),k(1,NightDaggerMotion.IDLE)},
        {k(0,NightDaggerMotion.IDLE),k(.35,FORWARD),k(1,FORWARD)},
        {k(0,FORWARD),k(.28,p(.22,-.18,-1.43,24,110,0,180)),
            k(.48,p(.20,-.18,-1.44,24,110,0,180)),k(.70,p(.38,-.26,-1.15,24,110,0,180)),k(1,NightDaggerMotion.IDLE)}
    };
    private static final Key[] MERGE_OFF={k(0,OFF_REST),k(.26,p(.095,-.28,-1.06,0,0,25,0)),
            k(.65,p(.095,-.28,-1.06,0,0,25,0)),k(1,OFF_REST)};
    private static final Key[] SHIELD_OFF={k(0,OFF_REST),k(.22,p(.06,-.35,-1.05,0,0,0,0)),
            k(.60,p(.06,-.35,-1.05,0,0,0,0)),k(1,OFF_REST)};
    private int action=-1,token=-1,ended=-1;
    private double start,duration;
    private Pose entry=NightDaggerMotion.IDLE,offEntry=OFF_REST;
    public void reset(){action=-1;token=-1;ended=-1;start=duration=0;entry=NightDaggerMotion.IDLE;offEntry=OFF_REST;}
    public boolean active(double tick){return action>=0&&tick>=start&&tick<start+duration;}
    public int action(){return action;}
    public int token(){return token;}
    public boolean accept(NightSkillViewPayload cue,double localTick,double elapsed,Pose fallback){
        if(!cue.valid()||cue.token()<token||cue.token()<=ended)return false;
        if(cue.action()==CANCEL){ended=cue.token();token=cue.token();action=-1;return true;}
        if(cue.action()==READY&&token==cue.token()&&action==STRIKE)return false;
        if(elapsed>=cue.ticks()||elapsed<0)return false;
        Frame previous=sample(localTick);
        entry=active(localTick)?previous.main():fallback;
        if(cue.action()==STRIKE&&cue.token()==token&&action==READY&&!active(localTick))entry=FORWARD;
        offEntry=active(localTick)&&previous.showOff()?previous.off():OFF_REST;
        action=cue.action();token=cue.token();duration=cue.ticks();start=localTick-elapsed;
        return true;
    }
    public Frame sample(double tick){
        if(!active(tick))return new Frame(NightDaggerMotion.IDLE,OFF_REST,false,true,-1,1);
        double t=Math.clamp((tick-start)/duration,0,1);
        Pose main=sample(MAIN[action],t,entry);
        boolean two=action==MERGE||action==SHIELD;
        Pose off=two?sample(action==MERGE?MERGE_OFF:SHIELD_OFF,t,offEntry):OFF_REST;
        boolean blade=action!=MERGE&&(action!=THROW||t>.80);
        return new Frame(main,off,two,blade,action,t);
    }
    private static Pose sample(Key[] keys,double t,Pose entry){
        for(int i=1;i<keys.length;i++)if(t<=keys[i].time){
            var a=keys[i-1];var b=keys[i];double u=(t-a.time)/(b.time-a.time);u=u*u*(3-2*u);
            return (i==1?entry:a.pose).blend(b.pose,u);
        }
        return keys[keys.length-1].pose;
    }
}
