package com.myriad.character.skills.rivet;

import com.myriad.network.AbilityVfxPayload;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Cosmetic palm-space poses. Preparation is sampled from authoritative progress, never a local timer. */
public final class RivetWeaponMotion {
    public record Pose(double x,double y,double z,double pitch,double yaw,double roll,double support) {
        private Pose blend(Pose to,double value){double t=Math.clamp(value,0,1);return new Pose(
                x+(to.x-x)*t,y+(to.y-y)*t,z+(to.z-z)*t,pitch+(to.pitch-pitch)*t,
                yaw+(to.yaw-yaw)*t,roll+(to.roll-roll)*t,support+(to.support-support)*t);}
    }
    public static final Pose IDLE=new Pose(.51,-.72,-1.12,-34,-18,-12,0);
    private static final int NONE=0,HAMMER_READY=1,HAMMER_SWING=2,ANCHOR=3,BRACE=4,TOW=5,
            TOW_RELEASE=6,PROTOCOL=7,CHARGE_READY=8,CHARGE_RUN=9,RHYTHM=10;
    private int action,variant=-1;private long newest=Long.MIN_VALUE;private double start=-100,received=-100,progress;
    private double impact=-100;
    private static double smooth(double value){double t=Math.clamp(value,0,1);return t*t*(3-2*t);}
    public void reset(){action=NONE;variant=-1;newest=Long.MIN_VALUE;start=received=impact=-100;progress=0;}
    public void accept(AbilityVfxPayload p,double now){
        if(!p.valid())return;
        if(p.kind()==CANCEL){if(p.angle()==0||p.theme()==RivetVfx.THEME&&p.variant()==variant)reset();return;}
        if(p.theme()!=RivetVfx.THEME||p.started()<newest||now-p.started()>p.life())return;
        int next=NONE;
        if(p.kind()==CHARGE)next=p.variant()==2?HAMMER_READY:p.angle()==2?CHARGE_RUN:CHARGE_READY;
        else if(p.kind()==ARC&&p.variant()==2)next=HAMMER_SWING;
        else if(p.kind()==BEAM&&p.variant()==5)next=p.angle()>0?TOW_RELEASE:TOW;
        else if(p.kind()==TRAIL&&p.variant()==7)next=CHARGE_RUN;
        else if(p.kind()==SHIELD&&p.anchor()==p.owner())next=p.variant()==6?PROTOCOL:BRACE;
        else if(p.kind()==BURST){
            if(p.variant()==3&&p.angle()==RivetVfx.ANCHOR_SET)next=ANCHOR;
            else if(p.variant()==1&&p.angle()==RivetVfx.RHYTHM_HIT)next=RHYTHM;
            else if(p.angle()<=RivetVfx.CHARGE_HIT){impact=p.started();return;}
        }
        if(next==NONE)return;
        if(next!=action||!continuous(next))start=p.started();
        action=next;variant=p.variant();newest=p.started();received=p.started();progress=p.angle();
    }
    private static boolean continuous(int action){return action==HAMMER_READY||action==CHARGE_READY||action==TOW||action==CHARGE_RUN;}
    public Pose sample(double now,double basic,boolean rhythmReady){
        Pose idle=IDLE;
        if(basic>0&&basic<1){
            // The vanilla attack still owns its attack/cooldown. Only its visible palm is reshaped.
            double load=smooth(basic/.18),hit=smooth((basic-.18)/.28),recover=smooth((basic-.54)/.46);
            Pose cocked=new Pose(.66,-.57,-1.04,-17,-35,-36,0);
            Pose struck=new Pose(.26,-.76,-1.36,-68,12,42,0);
            idle=IDLE.blend(cocked,load).blend(struck,hit).blend(IDLE,recover);
        }
        if(rhythmReady)idle=idle.blend(new Pose(.48,-.68,-1.09,-31,-18,-9,.10),.35);
        double age=Math.max(0,now-start),fresh=now-received;Pose pose=idle;double blend=0;
        switch(action){
            case HAMMER_READY->{pose=new Pose(.68,-.54,-.98,-12-12*progress,-36,-35-13*progress,.75);blend=1;}
            case HAMMER_SWING->{
                Pose lifted=new Pose(.68,-.54,-.98,-24,-36,-48,.75),struck=new Pose(.12,-.78,-1.40,-66,26,48,.6);
                pose=lifted.blend(struck,smooth(age/3.5)).blend(IDLE,smooth((age-4)/5));blend=age<9?1:0;
            }
            case ANCHOR->{pose=new Pose(.40,-.61,-1.32,-54,6,13,.2);blend=Math.sin(Math.PI*Math.clamp(age/9,0,1));}
            case BRACE->{pose=new Pose(.34,-.60,-1.04,-16,-8,38,.8);blend=smooth(age/2)*(1-smooth((age-6)/6));}
            case TOW->{pose=new Pose(.62,-.68,-.98,-45,-24,-27,.5);blend=1;}
            case TOW_RELEASE->{pose=new Pose(.62,-.76,-.98,-42,-25,-22,.25);blend=1-smooth(age/6);}
            case PROTOCOL->{pose=new Pose(.30,-.64,-1.20,-51,3,33,.8);blend=smooth(age/2)*(1-smooth((age-5)/7));}
            case CHARGE_READY->{pose=new Pose(.38,-.60,-1.14,-27,-2,44,.9);blend=.45+.55*progress;}
            case CHARGE_RUN->{pose=new Pose(.35,-.65,-1.24,-42,4,46,.9);blend=1;}
            case RHYTHM->{pose=new Pose(.30,-.76,-1.34,-63,18,39,.2);blend=1-smooth(age/7);}
            default->{}
        }
        // Missed end packets and interrupted preparation return to rest without playing a release.
        if(continuous(action))blend*=1-smooth((fresh-4)/4);
        Pose result=idle.blend(pose,blend);
        double kick=now-impact;double bump=kick>=0&&kick<4?Math.sin(Math.PI*kick/4):0;
        return new Pose(result.x,result.y-.018*bump,result.z+.045*bump,result.pitch+3*bump,
                result.yaw,result.roll,result.support);
    }
}
