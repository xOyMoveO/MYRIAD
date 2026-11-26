package com.myriad.client.firstperson;

import net.minecraft.world.phys.Vec3;

import static com.myriad.character.ability.WarSpiritHandCues.*;

/** Camera-space keyframes; a pose has exactly one grip shared by weapon and hand. */
public final class WarSpiritHandMotion {
    private static final Vec3 HIDDEN = new Vec3(-.78, -1.16, -.70);
    public record Pose(Vec3 grip, Vec3 axis, double roll, Vec3 palm, double palmRoll, double edge) {
        Pose toward(Pose to, double u) {
            return new Pose(grip.lerp(to.grip,u), axis.lerp(to.axis,u).normalize(), mix(roll,to.roll,u),
                    palm.lerp(to.palm,u), mix(palmRoll,to.palmRoll,u), mix(edge,to.edge,u));
        }
    }
    private record Clip(Pose contact, Pose follow, double hit, double sweep, double duration) {}

    private static final Pose WAR_IDLE = p(.49,-.39,-.93, .26,.87,-.42, -.38);
    private static final Pose SPIRIT_IDLE = p(.58,-.66,-.86, .12,.97,-.22, .06);
    private static final Pose WAR_BRACE = two(p(.50,-.57,-.99, -.95,.16,-.25, -.92), -.22,-.63,-1.02, -.52);
    private static final Pose WAR_DASH = p(.58,-.77,-1.03, .29,.18,-.94, -.60);
    private static final Pose WAR_RETURN = p(.67,-.75,-.71, .60,.50,-.55, -1.30);
    private static final Pose WAR_ASCEND = two(p(.68,-.42,-.82, .35,.90,-.24,-.93), -.44,-.53,-.91,-.34);
    private static final Pose WAR_DESCEND = two(p(.69,-.47,-1.01, .29,.55,-.79,-.58), -.36,-.54,-1.03,-.42);
    private static final Pose SPIRIT_GATHER = two(p(.30,-.65,-.95, .10,.98,-.19,-.23), -.24,-.66,-.98, .63);
    private static final Pose SPIRIT_DASH = p(.60,-.72,-.85, .37,.82,.44, .41);
    private static final Pose SPIRIT_GUIDE = two(p(.62,-.70,-.82, .26,.94,-.21,.15), -.45,-.54,-1.14,.30);

    private final boolean war;
    private final Pose idle;
    private Pose start, target;
    private Clip clip;
    private int skill = -1, phase = STOP, combo = -1;
    private double at, expires, lastBasic = Double.NEGATIVE_INFINITY;

    public WarSpiritHandMotion(boolean war) {
        this.war=war;
        idle=war?WAR_IDLE:SPIRIT_IDLE;
        start=target=idle;
    }

    public void reset() {
        start=target=idle;clip=null;skill=-1;phase=STOP;combo=-1;at=expires=0;
        lastBasic=Double.NEGATIVE_INFINITY;
    }

    public void abort(double now) {
        if (skill!=-1) stop(now);
        combo=-1;lastBasic=Double.NEGATIVE_INFINITY;
    }

    public void cancel(int variant, double now) { if(skill==variant)abort(now); }

    Vec3 strikeTravel(double tipDistance) {
        if(clip==null || clip.contact.edge()==0)return Vec3.ZERO;
        return clip.follow.grip().subtract(clip.contact.grip())
                .add(clip.follow.axis().subtract(clip.contact.axis()).scale(tipDistance));
    }

    public Pose pose(double now) {
        double time=Math.max(0,now-at);
        if (clip!=null) {
            if (time<clip.hit) return start.toward(clip.contact,smooth(time/clip.hit));
            if (time<clip.sweep) return clip.contact.toward(clip.follow,smooth((time-clip.hit)/(clip.sweep-clip.hit)));
            return clip.follow.toward(idle,smooth((time-clip.sweep)/(clip.duration-clip.sweep)));
        }
        if (now>=expires) return target.toward(idle,smooth((now-expires)/3));
        return start.toward(target,smooth(time/(phase==STOP?2.7:1.6)));
    }

    public void cue(int variant,int signal,double now,int life,double value) {
        if (signal==STOP) {
            if (variant==skill && clip==null) stop(now);
            return;
        }
        if (signal==BASIC) {
            combo=now-lastBasic>Math.max(13,Math.min(36,value+8))?0:(combo+1)%3;
            lastBasic=now;
            Clip form=(war?WAR_BASIC:SPIRIT_BASIC)[combo];
            double speed=Math.clamp(value/8,.62,1);
            play(0,signal,now,new Clip(form.contact,form.follow,form.hit*speed,form.sweep*speed,form.duration*speed));
            return;
        }
        if (signal==FOLLOW && !war && clip!=null && skill!=variant && now<at+clip.duration) return;
        if (signal==AIM || signal==HOLD || signal==MOVE || signal==RETURN) {
            Pose next=hold(variant,signal,Math.clamp(value,0,1));
            if (next==null) return;
            if(skill!=variant || phase!=signal || !target.equals(next) || now>=expires) {
                start=pose(now);target=next;at=now;skill=variant;phase=signal;clip=null;
            }
            expires=now+life;
            return;
        }
        Clip form=switch(signal) {
            case CAST -> (war?WAR_CAST:SPIRIT_CAST)[variant];
            case FOLLOW -> war?WAR_COUNTER:SPIRIT_TUG;
            case FINISH -> war?(variant==6?WAR_LAND:WAR_BRAKE):SPIRIT_ARRIVE;
            default -> null;
        };
        if (form!=null) play(variant,signal,now,form);
    }

    private void play(int variant,int signal,double now,Clip next) {
        start=pose(now);at=now;skill=variant;phase=signal;clip=next;
    }

    private void stop(double now) {
        start=pose(now);target=idle;at=now;expires=now+3;clip=null;skill=-1;phase=STOP;
    }

    private Pose hold(int variant,int signal,double value) {
        if (war) return switch(signal) {
            case AIM -> p(.65,-.70,-.82, .48,.60,-.64,-.63);
            case HOLD -> WAR_BRACE;
            case RETURN -> WAR_RETURN;
            case MOVE -> variant==6?WAR_ASCEND.toward(WAR_DESCEND,smooth((value-.24)/.65)):WAR_DASH;
            default -> null;
        };
        return switch(signal) {
            case AIM -> SPIRIT_GUIDE;
            case HOLD -> SPIRIT_GUIDE.toward(SPIRIT_GATHER,.38+.62*smooth(value));
            case MOVE -> SPIRIT_DASH;
            default -> null;
        };
    }

    private static final Clip[] WAR_CAST = {null,null,
            cut(p(.42,-.45,-1.04, -.26,.57,-.78,-.71),p(-.28,-.83,-1.13, -.88,.02,-.47,-.70)),
            null,null,
            cut(p(.43,-.57,-1.16, -.48,.42,-.76,-.54),p(.15,-.44,-1.05, -.52,.83,-.21,-.7)),
            null,
            beat(two(p(.35,-.57,-.91,-.90,.25,-.31,-.82), -.31,-.67,-1.05,.6),
                    two(p(.52,-.64,-.93,-.56,.63,-.54,-.73),-.43,-.69,-.96,.36))};

    private static final Clip[] SPIRIT_CAST = {null,null,
            beat(two(p(.50,-.59,-1.19,.15,.89,-.42,.24),-.30,-.51,-1.23,.32),
                    two(p(.59,-.64,-1.02,.29,.92,.20,-.38),-.59,-.62,-1.03,-.33)),
            beat(two(p(.61,-.65,-.87,.21,.97,-.10,-.11),-.35,-.53,-1.27,-.23),
                    two(p(.62,-.68,-.85,.32,.94,-.11,.21),-.24,-.61,-.94,.83)),
            beat(two(p(.50,-.74,-1.07,.21,.74,-.64,.41),-.41,-.64,-1.25,.90),
                    two(p(.56,-.78,-.95,.30,.91,-.29,-.17),-.48,-.76,-1.11,1.17)),
            null,
            beat(two(p(.51,-.45,-1.09,.41,.89,-.14,.47),-.48,-.50,-1.15,.36),
                    two(p(.65,-.56,-1.02,.42,.85,.24,-.34),-.75,-.59,-1.00,-.52)),
            beat(two(p(.29,-.51,-.97,.19,.98,-.07,.28),-.16,-.66,-1.03,.48),
                    two(p(.69,-.63,-1.04,.41,.90,.14,-.26),-.70,-.64,-1.07,-.45))};

    private static final Clip WAR_COUNTER = cut(p(.18,-.62,-1.09,-.86,.20,-.46,-.85),p(.66,-.43,-1.06,.57,.70,-.41,-.63));
    private static final Clip WAR_BRAKE = beat(p(.58,-.71,-.97,.45,.46,-.76,-.55),WAR_IDLE);
    private static final Clip WAR_LAND = cut(two(p(.45,-.44,-1.11,.18,.55,-.82,-.62),-.27,-.49,-1.13,.18),
            two(p(.34,-.68,-1.21,.17,-.17,-.97,-.70),-.38,-.80,-1.13,.33));
    private static final Clip SPIRIT_ARRIVE = beat(two(p(.57,-.58,-1.00,.32,.94,-.12,.21),-.50,-.54,-1.12,-.30),
            two(p(.68,-.61,-.96,.27,.95,.17,-.23),-.67,-.62,-1.03,-.57));
    private static final Clip SPIRIT_TUG = beat(two(p(.58,-.65,-.92,.18,.97,-.13,-.14),-.36,-.57,-1.12,.64),
            two(p(.63,-.70,-.81,.30,.94,.12,.27),-.39,-.71,-.85,.85));

    private static final Clip[] WAR_BASIC = {
            cut(p(.45,-.48,-1.05,-.37,.53,-.77,-.64),p(-.32,-.77,-1.07,-.87,.01,-.49,-.77)),
            cut(p(.08,-.66,-1.11,-.31,.13,-.94,-.66),p(.68,-.43,-1.06,.50,.73,-.46,-.75)),
            cut(p(.36,-.65,-1.16,-.49,.06,-.87,-.60),p(-.37,-.67,-1.06,-.90,.07,-.43,-.74))};
    private static final Clip[] SPIRIT_BASIC = {
            beat(p(.54,-.59,-1.19,.07,.81,-.58,.31),p(.59,-.67,-.99,.20,.95,.23,-.30)),
            beat(p(.44,-.62,-1.06,-.31,.93,-.19,-.44),p(.30,-.71,-.93,-.44,.88,-.18,.14)),
            beat(p(.59,-.44,-1.01,.26,.95,-.18,.48),p(.68,-.57,-.92,.32,.91,.24,-.32))};

    private static Clip cut(Pose contact,Pose follow) { return new Clip(edge(contact,1),edge(follow,.78),.85,3.4,8); }
    private static Clip beat(Pose contact,Pose follow) { return new Clip(contact,follow,.82,3.1,7); }
    private static Pose edge(Pose p,double e) { return new Pose(p.grip,p.axis,p.roll,p.palm,p.palmRoll,e); }
    private static Pose p(double x,double y,double z,double dx,double dy,double dz,double roll) {
        return new Pose(new Vec3(x,y,z),new Vec3(dx,dy,dz).normalize(),roll,HIDDEN,0,0);
    }
    private static Pose two(Pose p,double x,double y,double z,double roll) {
        return new Pose(p.grip,p.axis,p.roll,new Vec3(x,y,z),roll,p.edge);
    }
    private static double mix(double a,double b,double u) { return a+(b-a)*u; }
    private static double smooth(double x) { x=Math.clamp(x,0,1);return x*x*(3-2*x); }
}
