package com.myriad.client.firstperson;

/** Tianxing's local cosmetic chain, not a combat timer or a shared animation framework. */
public final class ThunderSwordMotion {
    public record Pose(double x,double y,double z,double pitch,double yaw,double roll) {
        Pose blend(Pose b,double t) {
            return new Pose(mix(x,b.x,t),mix(y,b.y,t),mix(z,b.z,t),
                    mix(pitch,b.pitch,t),mix(yaw,b.yaw,t),mix(roll,b.roll,t));
        }
    }
    public static final Pose IDLE=new Pose(.48,-.475,-1.08,-18,-6,56);
    /** Neighbouring centre-line poses define actual blade travel, not a guessed wrist roll. */
    public record Frame(Pose pose,Pose before,Pose after,double cuttingWeight,int cut){}
    private record Key(double t,Pose pose){}
    private static Key k(double t,double x,double y,double z,double pitch,double yaw,double roll){
        return new Key(t,new Pose(x,y,z,pitch,yaw,roll));
    }
    private static final Key[][] CUTS={
        // The sword tip travels from upper left to lower right, with a forward follow-through.
        {new Key(0,IDLE),k(.22,-.28,.02,-1.48,-10,-8,110),
                k(.44,.10,-.24,-1.64,-42,0,55),k(.62,.48,-.53,-1.44,-65,8,5),
                k(.70,.55,-.60,-1.51,-69,10,1),k(.85,.30,-.70,-1.33,-53,5,19),new Key(1,IDLE)},
        // Opposite diagonal. The blade stays on the far side of the palm, never through the eye.
        {new Key(0,IDLE),k(.22,.42,.02,-1.48,-10,8,20),
                k(.44,.08,-.24,-1.64,-42,0,65),k(.62,-.38,-.53,-1.44,-65,-8,125),
                k(.70,-.47,-.60,-1.50,-69,-10,131),k(.85,.02,-.72,-1.34,-51,-4,94),new Key(1,IDLE)},
        // Mid-height right-to-left sweep, rather than another diagonal or a thrust.
        {new Key(0,IDLE),k(.22,.48,-.26,-1.48,-70,0,-5),
                k(.44,.06,-.26,-1.64,-82,0,65),k(.62,-.38,-.26,-1.48,-70,0,135),
                k(.70,-.47,-.30,-1.51,-73,0,140),k(.85,-.02,-.68,-1.36,-57,0,102),new Key(1,IDLE)}
    };
    private int index=-1;
    private double start=Double.NEGATIVE_INFINITY,duration;
    private Pose entry=IDLE;
    public void attack(double tick,double recoveryTicks){
        if(!Double.isFinite(tick)||tick==start)return;
        Pose previous=sample(tick);
        index=index<0||tick<start||tick-start>duration+28?0:(index+1)%CUTS.length;
        entry=previous;start=tick;
        duration=MeleeStrokeTiming.recovery(recoveryTicks);
    }
    public Pose sample(double tick){
        if(!Double.isFinite(tick)||index<0||tick<start||tick>=start+duration)return IDLE;
        double t=path(tick);
        var keys=CUTS[index];
        for(int i=1;i<keys.length;i++)if(t<=keys[i].t){
            double u=(t-keys[i-1].t)/(keys[i].t-keys[i-1].t);u=u*u*(3-2*u);
            return (i==1?entry:keys[i-1].pose).blend(keys[i].pose,u);
        }
        return IDLE;
    }
    public int index(){return index;}
    public Frame frame(double tick){
        var p=sample(tick);
        if(!Double.isFinite(tick)||index<0||tick<start||tick>=start+duration)return new Frame(p,p,p,0,-1);
        double t=path(tick);
        double weight=t<.22?smooth(t/.22):t<=.62?1:smooth((1-t)/.38);
        // Prepare for the upcoming cutting direction; recovery does not suddenly reverse the blade edge.
        double directionTime=tickAt(Math.clamp(t,.225,.615));
        return new Frame(p,sample(directionTime-.012),sample(directionTime+.012),weight,index);
    }
    public double tickAt(double path) { return start + MeleeStrokeTiming.age(path, .44, .62, duration); }
    private double path(double tick) { return MeleeStrokeTiming.path(tick-start, .44, .62, duration); }
    public double duration(){return duration;}
    public double started(){return start;}
    public double progress(double tick){return Double.isFinite(tick)&&index>=0&&tick>=start&&tick<start+duration?(tick-start)/duration:-1;}
    public void reset(){index=-1;start=Double.NEGATIVE_INFINITY;duration=0;entry=IDLE;}
    private static double mix(double a,double b,double t){return a+(b-a)*t;}
    private static double smooth(double t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
}
