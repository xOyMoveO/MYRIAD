package com.myriad.client.firstperson;

import com.myriad.network.ShuoViewPayload;
import static com.myriad.network.ShuoViewPayload.*;

/** Cosmetic owner-only timeline. Server still decides every hit, cost and movement. */
public final class ShuoMotion {
    private final com.myriad.character.skills.shuo.ShuoBasicCombo combo=new com.myriad.character.skills.shuo.ShuoBasicCombo();
    private int action=CANCEL,token;private double start,duration;private boolean fromDrawn;private ShuoRig.Frame entry;
    public void reset(){action=CANCEL;token=0;start=duration=0;fromDrawn=false;entry=null;combo.reset();}
    public boolean accept(ShuoViewPayload p,double now,double elapsed,boolean drawn){
        if(!p.valid()||p.token()<=token)return false;token=p.token();
        combo.confirm(p.action(),now-elapsed);
        if(p.action()==CANCEL){action=CANCEL;entry=null;return true;}
        if(elapsed>=p.ticks()){action=CANCEL;return false;}
        // A confirmed vanilla hit acknowledges the observed click; it must not start a second draw.
        if(basicAction(p.action())&&basicAction(action)&&active(now)&&Math.abs(now-start-elapsed)<3){action=p.action();return true;}
        entry=frame(now,drawn);
        action=p.action();start=now-elapsed;duration=p.ticks();fromDrawn=drawn;return true;
    }
    public void basic(double now,boolean drawn){
        if(active(now)&&now-start<3)return;
        entry=frame(now,drawn);
        action=combo.select(now,drawn);start=now;duration=9;fromDrawn=drawn;
    }
    public boolean active(double now){return action!=CANCEL&&now>=start&&now<start+duration;}
    public int action(double now){return active(now)?action:CANCEL;}
    public double started(){return start;}
    public double duration(){return duration;}
    public boolean fromDrawn(){return fromDrawn;}
    public double progress(double now){return duration<=0?1:Math.clamp((now-start)/duration,0,1);}
    public ShuoRig.Frame frame(double now,boolean drawn){
        var f=ShuoRig.frame(action(now),progress(now),active(now)?fromDrawn:drawn);
        if(entry==null||!active(now))return f;
        float blend=ShuoRig.ease((now-start)/Math.min(1.4,duration*.2));if(blend>=1)return f;
        return new ShuoRig.Frame(mix(entry.palm(),f.palm(),blend),unit(entry.axis(),f.axis(),blend),unit(entry.edge(),f.edge(),blend),
            mix(entry.mouth(),f.mouth(),blend),unit(entry.sheathAxis(),f.sheathAxis(),blend),f.flash()*blend,f.action(),f.slashProgress());
    }
    private static org.joml.Vector3f mix(org.joml.Vector3f a,org.joml.Vector3f b,float t){return new org.joml.Vector3f(a).lerp(b,t);}
    private static org.joml.Vector3f unit(org.joml.Vector3f a,org.joml.Vector3f b,float t){var v=mix(a,b,t);return v.lengthSquared()<1E-6?new org.joml.Vector3f(b):v.normalize();}
}
