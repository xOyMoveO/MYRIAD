package com.myriad.character.skills.yike;

/** Pure presentation math. Windups interpolate confirmed samples, never extrapolate time. */
public final class YikeWeaponAnimation {
    public static final double CLOSE_TICKS=4, RECOIL_TICKS=4, SNAPSHOT_TTL=5;
    public record Snapshot(int mode,int windup,double progress,long tick) {}
    /** Angles are degrees; translations use model pixels, preserving the (8,8,8) grip. */
    public record Pose(double petal0,double petal1,double petal2,double ringSlide,
                       double latch,double ringTwist,double coreReady,double aimBlend,
                       double idleMobile,double recoil) {
        public double petal(int index){return index==0?petal0:index==1?petal1:petal2;}
        public double petalSway(int index,double time){return index==0?0:Math.sin(time*.5+index)*.10*idleMobile;}
        public Pose mix(Pose other,double weight){
            return new Pose(lerp(petal0,other.petal0,weight),lerp(petal1,other.petal1,weight),lerp(petal2,other.petal2,weight),
                    lerp(ringSlide,other.ringSlide,weight),lerp(latch,other.latch,weight),lerp(ringTwist,other.ringTwist,weight),
                    lerp(coreReady,other.coreReady,weight),lerp(aimBlend,other.aimBlend,weight),lerp(idleMobile,other.idleMobile,weight),0);
        }
    }
    public static final Pose CLOSED=new Pose(0,0,0,0,0,0,0,0,0,0);
    private YikeWeaponAnimation(){}
    private static double clamp(double value){return Math.max(0,Math.min(1,value));}
    private static double lerp(double a,double b,double weight){return a+(b-a)*clamp(weight);}
    private static double smooth(double value){double t=clamp(value);return t*t*(3-2*t);}
    private static double stage(double progress,double start,double end){return smooth((progress-start)/(end-start));}

    /** The current camera basis remains defined at straight-up/down pitch. */
    public static double[] screenRight(double lookX,double lookZ,double yawDegrees){
        double length=Math.hypot(lookX,lookZ);
        if(length<1E-4){double yaw=Math.toRadians(yawDegrees);return new double[]{-Math.cos(yaw),0,-Math.sin(yaw)};}
        return new double[]{-lookZ/length,0,lookX/length};
    }
    /** The grip's projected edge remains defined when the approved aim points sideways. */
    public static double[] gripRight(double x,double y,double z){
        double length=Math.sqrt(x*x+y*y+z*z);x/=length;y/=length;z/=length;
        boolean side=Math.abs(x)>.95;
        double projection=side?y:x;
        double rx=(side?0:1)-projection*x,ry=(side?1:0)-projection*y,rz=-projection*z;
        double norm=Math.sqrt(rx*rx+ry*ry+rz*rz);
        return new double[]{rx/norm,ry/norm,rz/norm};
    }

    public static Pose target(Snapshot snapshot){
        boolean mobile=snapshot.mode==2||snapshot.mode==0&&snapshot.windup==5;
        boolean active=snapshot.mode>0;
        boolean preparing=snapshot.windup==4||snapshot.windup==5||snapshot.windup==7;
        if(!active&&!preparing)return CLOSED;
        double progress=active?1:clamp(snapshot.progress);
        double unlocked=stage(progress,0,.16), petals=stage(progress,.15,.72);
        double focus=stage(progress,.58,1), core=stage(progress,.78,1);
        double angle=(mobile?16.5:30)*petals;
        return new Pose(mobile?0:angle,angle,angle,(mobile?.7425:1.35)*focus,
                .10*unlocked*(1-petals),7*unlocked*(1-stage(progress,.18,.52)),
                core,stage(progress,0,.6),snapshot.mode==2?1:0,0);
    }

    public static final class Controller {
        private Pose from=CLOSED,to=CLOSED;
        private double changed,span=1,received=Double.NEGATIVE_INFINITY,recoilStart=Double.NEGATIVE_INFINITY;
        private long lastTick=Long.MIN_VALUE,lastShot=Long.MIN_VALUE;
        private Snapshot latest;
        public void accept(Snapshot snapshot,double now){
            if(snapshot.tick<lastTick)return;
            if(snapshot.equals(latest)){received=now;return;}
            Pose target=target(snapshot);
            if(!target.equals(to)){
                from=base(now);to=target;changed=now;
                // The one-tick blend connects already acknowledged endpoints only.
                span=target.equals(CLOSED)?CLOSE_TICKS:1;
            }
            latest=snapshot;lastTick=snapshot.tick;received=now;
        }
        /** Called once for a visibility-validated, actual muzzle event; duplicate packets do not replay it. */
        public void fire(long started,double now){
            if(started<=lastShot)return;
            lastShot=started;recoilStart=now;
        }
        private Pose base(double now){return from.mix(to,smooth((now-changed)/span));}
        public Pose pose(double now){
            if(now-received>SNAPSHOT_TTL)return CLOSED;
            Pose pose=base(now);
            double age=now-recoilStart;
            double kick=age<0||age>=RECOIL_TICKS?0:smooth(age/.6)*(1-smooth((age-.6)/(RECOIL_TICKS-.6)));
            return new Pose(pose.petal0+1.8*kick*clamp(pose.petal0/10),
                    pose.petal1+1.8*kick*clamp(pose.petal1/10),pose.petal2+1.8*kick*clamp(pose.petal2/10),
                    pose.ringSlide-.20*kick,pose.latch,pose.ringTwist,pose.coreReady,pose.aimBlend,pose.idleMobile,kick==0?0:-.32*kick);
        }
        public void clear(){from=CLOSED;to=CLOSED;latest=null;lastTick=Long.MIN_VALUE;lastShot=Long.MIN_VALUE;
            received=Double.NEGATIVE_INFINITY;recoilStart=Double.NEGATIVE_INFINITY;}
    }
}
