package com.myriad.character.skills.rivet;

import com.myriad.network.AbilityVfxPayload;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Cosmetic palm-space poses. Preparation is sampled from authoritative progress, never a local timer. */
public final class RivetWeaponMotion {
    public record Pose(double x,double y,double z,double pitch,double yaw,double roll) {
        private Pose blend(Pose to,double value){double t=Math.clamp(value,0,1);return new Pose(
                x+(to.x-x)*t,y+(to.y-y)*t,z+(to.z-z)*t,pitch+(to.pitch-pitch)*t,
                yaw+(to.yaw-yaw)*t,roll+(to.roll-roll)*t);}
    }
    public static final Pose IDLE=new Pose(.56,-.56,-1.28,-16,-84,-28);
    private static final Pose LIFT=new Pose(.64,-.42,-1.35,-2,-92,-43);
    private static final Pose BASIC_HIT=new Pose(.33,-.53,-1.50,-64,-64,-11);
    private static final Pose BASIC_FOLLOW=new Pose(.29,-.65,-1.58,-78,-60,0);
    private static final Pose BASIC_LOW=new Pose(.40,-.72,-1.40,-62,-69,0);
    private static final Pose STRIKE=new Pose(.53,-.56,-1.15,-65,-55,-5);
    private static final Pose FOLLOW=new Pose(.51,-.61,-1.17,-78,-50,8);
    private static final int NONE=0,HAMMER_READY=1,HAMMER_SWING=2,ANCHOR=3,BRACE=4,TOW=5,
            TOW_RELEASE=6,PROTOCOL=7,CHARGE_READY=8,CHARGE_RUN=9,RHYTHM=10;
    private int action,variant=-1;private long newest=Long.MIN_VALUE;private double start=-100,received=-100,progress;
    private double impact=-100;
    private double basicStart=Double.NEGATIVE_INFINITY,basicDuration=10;
    private Pose basicEntry=IDLE;
    public void basic(double now,double duration) {
        basicEntry=stroke(now); basicDuration=duration; basicStart=now;
    }
    private Pose stroke(double now) {
        double age=now-basicStart;
        if (age<0 || age>=basicDuration) return IDLE;
        if (age<.38) return basicEntry.blend(LIFT,smooth(age/.38));
        double hit=com.myriad.client.firstperson.MeleeStrokeTiming.HIT;
        if (age<hit) return LIFT.blend(BASIC_HIT,falling((age-.38)/(hit-.38)));
        if (age<2.9) return BASIC_HIT.blend(BASIC_FOLLOW,smooth((age-hit)/(2.9-hit)));
        double t=(age-2.9)/(basicDuration-2.9);
        return t<.45 ? BASIC_FOLLOW.blend(BASIC_LOW,smooth(t/.45)) : BASIC_LOW.blend(IDLE,smooth((t-.45)/.55));
    }
    private static double smooth(double value){double t=Math.clamp(value,0,1);return t*t*(3-2*t);}
    private static double falling(double value){double t=Math.clamp(value,0,1);return t*t;}
    private static Pose basicStroke(double progress){
        if(progress<.24)return IDLE.blend(LIFT,smooth(progress/.24));
        if(progress<.50)return LIFT.blend(STRIKE,falling((progress-.24)/.26));
        if(progress<.65)return STRIKE.blend(FOLLOW,smooth((progress-.50)/.15));
        return FOLLOW.blend(IDLE,smooth((progress-.65)/.35));
    }
    private static Pose heavyStroke(double age){
        if(age<2.6)return LIFT.blend(STRIKE,falling(age/2.6));
        if(age<4)return STRIKE.blend(FOLLOW,smooth((age-2.6)/1.4));
        return FOLLOW.blend(IDLE,smooth((age-4)/6.5));
    }
    public void reset(){basicStart=Double.NEGATIVE_INFINITY;action=NONE;variant=-1;newest=Long.MIN_VALUE;start=received=impact=-100;progress=0;}
    /** True only when the cue takes control of the grip. Impact accents and world cues can coexist with a basic. */
    public boolean accept(AbilityVfxPayload p,double now){
        if(!p.valid())return false;
        if(p.kind()==CANCEL){
            if(p.angle()==0||p.theme()==RivetVfx.THEME&&p.variant()==variant)reset();
            return false;
        }
        if(p.theme()!=RivetVfx.THEME||p.started()<newest||now-p.started()>p.life())return false;
        int next=NONE;
        if(p.kind()==CHARGE&&p.variant()==2)next=HAMMER_READY;
        else if(p.kind()==CHARGE&&p.variant()==7)next=p.angle()==2?CHARGE_RUN:CHARGE_READY;
        else if(p.kind()==ARC&&p.variant()==2)next=HAMMER_SWING;
        else if(p.kind()==BEAM&&p.variant()==5)next=p.angle()>0?TOW_RELEASE:TOW;
        else if(p.kind()==TRAIL&&p.variant()==7)next=CHARGE_RUN;
        else if(p.kind()==SHIELD&&p.anchor()==p.owner())next=p.variant()==6?PROTOCOL:BRACE;
        else if(p.kind()==BURST){
            if(p.variant()==3&&p.angle()==RivetVfx.ANCHOR_SET)next=ANCHOR;
            else if(p.variant()==1&&p.angle()==RivetVfx.RHYTHM_HIT)next=RHYTHM;
            else if((p.variant()==2||p.variant()==7)&&(p.angle()==RivetVfx.WALL_HIT||p.angle()==RivetVfx.ANCHOR_HIT)
                    ||p.variant()==7&&p.angle()==RivetVfx.CHARGE_HIT)impact=p.started();
        }
        if(next==NONE)return false;
        if(next!=action||!continuous(next))start=p.started();
        action=next;variant=p.variant();newest=p.started();received=p.started();progress=p.angle();
        return true;
    }
    private static boolean continuous(int action){return action==HAMMER_READY||action==CHARGE_READY||action==TOW||action==CHARGE_RUN;}
    public Pose sample(double now,double basic,boolean rhythmReady){
        Pose idle=IDLE;
        if (now>=basicStart && now<basicStart+basicDuration) idle=stroke(now);
        else if(basic>0&&basic<1){
            // The vanilla attack still owns its attack/cooldown. Only its visible palm is reshaped.
            idle=basicStroke(basic);
        }
        if(rhythmReady)idle=idle.blend(new Pose(.56,-.54,-1.28,-14,-72,-30),.35);
        double age=Math.max(0,now-start),fresh=now-received;Pose pose=idle;double blend=0;
        switch(action){
            case HAMMER_READY->{pose=IDLE.blend(LIFT,smooth(progress));blend=smooth(age/2);}
            case HAMMER_SWING->{
                pose=heavyStroke(age);blend=age<10.5?1:0;
            }
            case ANCHOR->{pose=new Pose(.52,-.55,-1.16,-28,-75,-22);blend=Math.sin(Math.PI*Math.clamp(age/9,0,1));}
            case BRACE->{pose=new Pose(.52,-.49,-1.25,-20,-60,-22);blend=smooth(age/2)*(1-smooth((age-6)/6));}
            case TOW->{pose=new Pose(.55,-.57,-1.20,-24,-72,-28);blend=smooth(age/2);}
            case TOW_RELEASE->{pose=new Pose(.55,-.58,-1.24,-20,-72,-28);blend=1-smooth(age/6);}
            case PROTOCOL->{pose=new Pose(.51,-.60,-1.18,-60,-60,-2);blend=smooth(age/2)*(1-smooth((age-5)/7));}
            case CHARGE_READY->{pose=new Pose(.63,-.48,-1.27,-18,-80,-38);blend=(.45+.55*progress)*smooth(age/2);}
            case CHARGE_RUN->{pose=new Pose(.62,-.56,-1.23,-15,-75,-35);blend=1;}
            case RHYTHM->{pose=STRIKE.blend(FOLLOW,smooth(age/2));blend=1-smooth((age-2)/6);}
            default->{}
        }
        // Missed end packets and interrupted preparation return to rest without playing a release.
        if(continuous(action))blend*=1-smooth((fresh-4)/4);
        Pose result=idle.blend(pose,blend);
        double kick=now-impact;double bump=kick>=0&&kick<4?Math.sin(Math.PI*kick/4):0;
        return new Pose(result.x,result.y-.018*bump,result.z+.045*bump,result.pitch+3*bump,
                result.yaw,result.roll);
    }
}
