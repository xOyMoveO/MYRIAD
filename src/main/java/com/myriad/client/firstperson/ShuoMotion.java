package com.myriad.client.firstperson;

import com.myriad.network.ShuoViewPayload;
import static com.myriad.network.ShuoViewPayload.*;

/** Cosmetic owner-only timeline. Server still decides every hit, cost and movement. */
public final class ShuoMotion {
    private static final double RECOVERY_TICKS=3;
    private final com.myriad.character.skills.shuo.ShuoBasicCombo combo=new com.myriad.character.skills.shuo.ShuoBasicCombo();
    private int action=CANCEL,token;private double start,duration;private boolean fromDrawn;private ShuoRig.Frame entry;
    public void reset(){action=CANCEL;token=0;start=duration=0;fromDrawn=false;entry=null;combo.reset();}
    public boolean accept(ShuoViewPayload p,double now,double elapsed,boolean drawn){
        if(!p.valid()||p.token()<=token)return false;token=p.token();
        combo.confirm(p.action(),now-elapsed);
        if(p.action()==CANCEL || elapsed>=p.ticks()){
            entry=frame(now,drawn);
            action=CANCEL;start=now;duration=0;
            return p.action()==CANCEL;
        }
        // A confirmed vanilla hit acknowledges the observed click; it must not start a second draw.
        if(basicAction(p.action())&&basicAction(action)&&active(now)&&Math.abs(now-start-elapsed)<3){action=p.action();return true;}
        // MOON_FIRST is the sheathed branch even if the newly drawn state sync reaches the client first.
        boolean startsDrawn=drawn && p.action()!=MOON_FIRST;
        entry=frame(now,startsDrawn);
        action=p.action();start=now-elapsed;duration=p.ticks();fromDrawn=startsDrawn;return true;
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
        boolean heldWindup=action==CHARGE && entry!=null && continuation(entry.action());
        if(active(now)){
            if(heldWindup)return entry;
            var pose=ShuoRig.frame(action,progress(now),fromDrawn);
            return entry==null?pose:blend(entry,pose,ShuoRig.ease((now-start)/Math.min(1.4,duration*.2)));
        }
        var idle=ShuoRig.frame(CANCEL,1,drawn);
        if(now<start)return idle;
        // Keep the cut poised throughout its recast window and while the server waits for a release/hold edge.
        double release=start+duration;
        if(drawn && continuation(action))release=start+40;
        if(heldWindup)release+=6;
        if(now>=release+RECOVERY_TICKS || action==CANCEL && entry==null)return idle;
        var end=action==CANCEL || heldWindup?entry:ShuoRig.frame(action,1,fromDrawn);
        if(now<release)return end;
        // Only the grip coasts back. The action and its attached light expire immediately.
        return blend(end,idle,ShuoRig.ease((now-release)/RECOVERY_TICKS));
    }
    private static boolean continuation(int cut){return cut==MOON_FIRST || cut==MOON_SECOND;}
    private static ShuoRig.Frame blend(ShuoRig.Frame from,ShuoRig.Frame to,float t){
        if(t>=1)return to;
        return new ShuoRig.Frame(mix(from.palm(),to.palm(),t),unit(from.axis(),to.axis(),t),unit(from.edge(),to.edge(),t),
            mix(from.mouth(),to.mouth(),t),unit(from.sheathAxis(),to.sheathAxis(),t),to.flash()*t,to.action(),to.slashProgress());
    }
    private static org.joml.Vector3f mix(org.joml.Vector3f a,org.joml.Vector3f b,float t){return new org.joml.Vector3f(a).lerp(b,t);}
    private static org.joml.Vector3f unit(org.joml.Vector3f a,org.joml.Vector3f b,float t){var v=mix(a,b,t);return v.lengthSquared()<1E-6?new org.joml.Vector3f(b):v.normalize();}
}
