package com.myriad.client.firstperson;

/** Client cosmetic timeline only. No entity, combat, animation packet or game-state dependencies. */
public final class NightDaggerMotion {
    public record Pose(double x, double y, double z, double pitch, double yaw, double roll, double wrist, double grip) {
        Pose blend(Pose to, double t) {
            return new Pose(mix(x,to.x,t), mix(y,to.y,t), mix(z,to.z,t),
                    mix(pitch,to.pitch,t), mix(yaw,to.yaw,t), mix(roll,to.roll,t), mix(wrist,to.wrist,t), mix(grip,to.grip,t));
        }
    }
    public static final Pose IDLE = new Pose(.55,-.26,-1.03,0,-15,72,0,0);
    private record Key(double time, Pose pose) { }
    private static Key k(double t, double x,double y,double z,double pitch,double yaw,double roll,double wrist) {
        return k(t,x,y,z,pitch,yaw,roll,wrist,0);
    }
    private static Key k(double t, double x,double y,double z,double pitch,double yaw,double roll,double wrist,double grip) {
        return new Key(t,new Pose(x,y,z,pitch,yaw,roll,wrist,grip));
    }
    private static final Key[][] ATTACKS = {
        {new Key(0,IDLE), k(.16,.58,-.27,-.94,0,-22,72,0),
            k(.40,.27,-.13,-1.22,62,5,82,0), k(.55,.26,-.15,-1.20,60,5,82,0), new Key(1,IDLE)},
        // Reverse-grip overhead hammer: lift at the right edge, then drive down rather than across.
        {new Key(0,IDLE), k(.18,.57,.10,-1.04,0,-5,78,0),
            k(.30,.40,.32,-1.00,-8,-2,84,0), k(.56,.32,-.31,-1.10,-10,5,80,0),
            k(.70,.35,-.37,-1.05,-5,2,74,0), new Key(1,IDLE)},
        // Rotate the KNIFE around the palm into a forward grip, slash, then restore reverse idle.
        {new Key(0,IDLE), k(.18,.57,-.23,-1.05,0,-12,70,20),
            k(.34,.48,-.20,-1.02,8,-12,72,-20,180),
            k(.62,-.12,-.22,-1.12,8,-5,92,-20,180),
            k(.76,.08,-.31,-1.10,0,-10,84,-10,180), new Key(1,IDLE)}
    };
    private int index = -1;
    private double start = Double.NEGATIVE_INFINITY;
    private double duration;
    private Pose entry = IDLE;

    /** Call only for an unconsumed, fully recovered vanilla left-click. */
    public void attack(double tick, double recoveryTicks) {
        if(!Double.isFinite(tick)||tick==start)return;
        Pose previous = sample(tick);
        boolean restart = index < 0 || tick < start || tick - start > duration + 28;
        index = restart ? 0 : (index + 1) % 3;
        entry = previous; // Fast follow-ups never snap the hand back to its idle position.
        start = tick;
        duration = durationFor(recoveryTicks);
    }
    public static double durationFor(double recoveryTicks) {
        if (!Double.isFinite(recoveryTicks)) return 5.4;
        return Math.clamp(recoveryTicks*.27,3.5,6);
    }
    public Pose sample(double tick) {
        if (index < 0 || tick < start || tick >= start + duration) return IDLE;
        double progress = (tick-start)/duration;
        Key[] keys = ATTACKS[index];
        for (int i=1;i<keys.length;i++) {
            if (progress <= keys[i].time) {
                Key a = keys[i-1], b=keys[i];
                double u = (progress-a.time)/(b.time-a.time);
                u = u*u*(3-2*u);
                return (i==1 ? entry : a.pose).blend(b.pose,u);
            }
        }
        return IDLE;
    }
    public int index() { return index; }
    public double duration() { return duration; }
    public double started() { return start; }
    public double progress(double tick) { return Double.isFinite(tick)&&index>=0&&tick>=start&&tick<start+duration?(tick-start)/duration:-1; }
    public void reset() { index=-1; start=Double.NEGATIVE_INFINITY; duration=0; entry=IDLE; }
    private static double mix(double a,double b,double t) { return a+(b-a)*t; }
}
