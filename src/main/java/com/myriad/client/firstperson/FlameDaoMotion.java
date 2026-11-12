package com.myriad.client.firstperson;

/** Fenjue's three local view-model cuts. Never schedules hits or changes attack recovery. */
public final class FlameDaoMotion {
    public record Pose(double x,double y,double z,double pitch,double yaw,double roll) {
        private double v(int i){return switch(i){case 0->x;case 1->y;case 2->z;case 3->pitch;case 4->yaw;default->roll;};}
    }
    public static final Pose IDLE=new Pose(.61,-.46,-1.26,-10,-8,54);
    public record Frame(Pose pose,Pose before,Pose after,double progress,int cut,
                        double entryRight,double entryLeft,double anchorRight,double anchorLeft){}
    private record Key(double t,Pose p){}
    private static Key k(double t,double x,double y,double z,double pitch,double yaw,double roll){
        return new Key(t,new Pose(x,y,z,pitch,yaw,roll));
    }
    private static final Key[][] CUTS={
        // Right shoulder -> lower left; the wrist leads a forward descending arc.
        {new Key(0,IDLE),k(.20,.48,.01,-1.48,-10,8,18),
                k(.42,.12,-.24,-1.68,-42,0,64),k(.65,-.40,-.56,-1.49,-67,-8,128),
                k(.79,-.28,-.51,-1.44,-58,-5,118),new Key(1,IDLE)},
        // Low left -> upper right. A rising return cut, not a second downward slap.
        {new Key(0,IDLE),k(.20,-.39,-.56,-1.48,-67,-8,128),
                k(.42,.08,-.25,-1.68,-42,0,64),k(.65,.49,.02,-1.49,-10,8,18),
                k(.79,.50,-.02,-1.43,-8,6,23),new Key(1,IDLE)},
        // Mid-height right -> left, followed by a visible controlled return to low guard.
        {new Key(0,IDLE),k(.20,.53,-.29,-1.50,-70,0,-5),
                k(.42,.08,-.29,-1.72,-82,0,64),k(.65,-.43,-.29,-1.50,-70,0,135),
                k(.79,-.30,-.33,-1.44,-62,0,125),new Key(1,IDLE)}
    };
    private int cut=-1;
    private double start=Double.NEGATIVE_INFINITY,duration,entryRight,entryLeft,anchorRight,anchorLeft;
    private Pose entry=IDLE;
    public boolean attack(double tick,double recoveryTicks){
        if(!Double.isFinite(tick)||tick==start)return false;
        var previous=frame(tick);
        double right=FlameDaoRig.turn(previous,false),left=FlameDaoRig.turn(previous,true);
        cut=cut<0||tick<start||tick-start>duration+28?0:(cut+1)%3;
        entry=previous.pose();start=tick;entryRight=right;entryLeft=left;
        duration=Double.isFinite(recoveryTicks)?Math.clamp(recoveryTicks*.32,4,7.5):6.4;
        var anchor=frame(start+duration*.30);
        anchorRight=FlameDaoRig.target(anchor,false);anchorLeft=FlameDaoRig.target(anchor,true);
        return true;
    }
    private Pose value(Key[] keys,int i){return i==0?entry:keys[i].p;}
    private double slope(Key[] keys,int i,int axis){
        if(i==0||i==keys.length-1)return 0;
        return (value(keys,i+1).v(axis)-value(keys,i-1).v(axis))/(keys[i+1].t-keys[i-1].t);
    }
    public Pose sample(double tick){
        if(!Double.isFinite(tick)||cut<0||tick<start||tick>=start+duration)return IDLE;
        double t=(tick-start)/duration;var keys=CUTS[cut];
        for(int i=1;i<keys.length;i++)if(t<=keys[i].t){
            double span=keys[i].t-keys[i-1].t,u=(t-keys[i-1].t)/span;
            double a=2*u*u*u-3*u*u+1,b=u*u*u-2*u*u+u,c=-2*u*u*u+3*u*u,d=u*u*u-u*u;
            var values=new double[6];
            for(int axis=0;axis<6;axis++)values[axis]=a*value(keys,i-1).v(axis)+b*span*slope(keys,i-1,axis)
                    +c*value(keys,i).v(axis)+d*span*slope(keys,i,axis);
            return new Pose(values[0],values[1],values[2],values[3],values[4],values[5]);
        }
        return IDLE;
    }
    public Frame frame(double tick){
        var p=sample(tick);
        if(!Double.isFinite(tick)||cut<0||tick<start||tick>=start+duration)
            return new Frame(p,p,p,-1,-1,0,0,0,0);
        double t=(tick-start)/duration,at=start+Math.clamp(t,.205,.645)*duration;
        return new Frame(p,sample(at-duration*.002),sample(at+duration*.002),t,cut,
                entryRight,entryLeft,anchorRight,anchorLeft);
    }
    public int index(){return cut;}
    public double duration(){return duration;}
    public double started(){return start;}
    public void reset(){cut=-1;start=Double.NEGATIVE_INFINITY;duration=0;entry=IDLE;entryRight=entryLeft=anchorRight=anchorLeft=0;}
}
