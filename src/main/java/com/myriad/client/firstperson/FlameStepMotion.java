package com.myriad.client.firstperson;

import com.myriad.character.skills.fenjue.rework.FlameStepVfx;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.client.firstperson.FlameDaoMotion.Frame;
import com.myriad.client.firstperson.FlameDaoMotion.Pose;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Hold until the actual server dash end, then release one quick cut; no movement or damage. */
public final class FlameStepMotion {
    public static final Pose READY=new Pose(.55,-.62,-1.65,-25,-12,132);
    private static final double HOLD_LIMIT=12,SLASH_TICKS=5;
    private record Key(double t,Pose p){}
    private static Key k(double t,double x,double y,double z,double pitch,double yaw,double roll){return new Key(t,new Pose(x,y,z,pitch,yaw,roll));}
    private static final Key[] CUT={new Key(0,READY),k(.20,.26,-.44,-1.82,-40,-6,100),
        k(.42,.08,-.30,-1.92,-50,0,64),k(.65,.60,-.52,-1.62,-76,10,-5),new Key(1,FlameDaoMotion.IDLE)};
    private static final Frame IDLE=new FlameDaoMotion().frame(0);
    private long latest=Long.MIN_VALUE,latestFinish=Long.MIN_VALUE;
    private double start=Double.NEGATIVE_INFINITY,end=Double.NEGATIVE_INFINITY;
    private Pose entry=FlameDaoMotion.IDLE,cutEntry=READY;
    private double right,left;
    public boolean accept(AbilityVfxPayload p,double now,double elapsed,Frame fallback){
        if(!p.valid()||!Double.isFinite(now)||!Double.isFinite(elapsed)||elapsed<0)return false;
        if(p.kind()==CANCEL&&(p.angle()==0||p.angle()==1&&p.theme()==2&&p.variant()==3)){
            if(p.started()<latest)return false;latest=p.started();latestFinish=p.started();interrupt();return true;
        }
        if(FlameStepVfx.sides(p)&&p.angle()==0){
            if(p.started()<=latest||elapsed>=HOLD_LIMIT)return false;
            entry=fallback.pose();start=now-elapsed;end=Double.NEGATIVE_INFINITY;latest=p.started();return true;
        }
        if(FlameStepVfx.finish(p)){
            if(p.started()<latest||p.started()<=latestFinish||elapsed>=SLASH_TICKS)return false;
            cutEntry=active(now)?frame(now).pose():READY;
            var first=new Frame(cutEntry,cutEntry,cutEntry,-1,-1,0,0,0,0);
            right=FlameDaoRig.turn(first,false);left=FlameDaoRig.turn(first,true);
            latest=p.started();latestFinish=p.started();end=now-elapsed;return true;
        }
        return false;
    }
    public boolean active(double now){return Double.isFinite(now)&&(Double.isFinite(end)?now>=end&&now<end+SLASH_TICKS:now>=start&&now<start+HOLD_LIMIT);}
    public boolean slashing(double now){return Double.isFinite(end)&&active(now);}
    public double slashStarted(){return end;}
    public double slashDuration(){return SLASH_TICKS;}
    public void interrupt(){start=end=Double.NEGATIVE_INFINITY;}
    public void reset(){interrupt();latest=latestFinish=Long.MIN_VALUE;entry=FlameDaoMotion.IDLE;cutEntry=READY;right=left=0;}
    private static Pose mix(Pose a,Pose b,double t){t=Math.clamp(t,0,1);t=t*t*(3-2*t);
        return new Pose(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t,
            a.pitch()+(b.pitch()-a.pitch())*t,a.yaw()+(b.yaw()-a.yaw())*t,a.roll()+(b.roll()-a.roll())*t);}
    private Pose cut(double t){
        for(int i=1;i<CUT.length;i++)if(t<=CUT[i].t)return mix(i==1?cutEntry:CUT[i-1].p,CUT[i].p,(t-CUT[i-1].t)/(CUT[i].t-CUT[i-1].t));
        return FlameDaoMotion.IDLE;
    }
    private Frame raw(double t,double r,double l){double at=Math.clamp(t,.205,.645);
        return new Frame(cut(t),cut(at-.002),cut(at+.002),t,2,r,l,0,0);}
    public Frame frame(double now){
        if(!active(now))return IDLE;
        if(!Double.isFinite(end)){
            var pose=mix(entry,READY,(now-start)/1.5);return new Frame(pose,pose,pose,-1,-1,0,0,0,0);
        }
        double t=(now-end)/SLASH_TICKS;var anchor=raw(.42,0,0);var f=raw(t,right,left);
        return new Frame(f.pose(),f.before(),f.after(),t,2,right,left,FlameDaoRig.target(anchor,false),FlameDaoRig.target(anchor,true));
    }
}
