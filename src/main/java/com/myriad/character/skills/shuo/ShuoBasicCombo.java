package com.myriad.character.skills.shuo;
import static com.myriad.network.ShuoViewPayload.*;
/** Cosmetic sequence only. Neither selects targets nor changes attack timing/damage. */
public final class ShuoBasicCombo {
    private int next;private double last=Double.NEGATIVE_INFINITY;
    public void reset(){next=0;last=Double.NEGATIVE_INFINITY;}
    public int select(double now,boolean drawn){
        if(!drawn||now<last||now-last>60)next=0;
        int action=switch(next){case 1->BASIC_RISE;case 2->BASIC_SWEEP;default->DRAW;};
        confirm(action,now);return action;
    }
    public void confirm(int action,double now){
        if(!basicAction(action)){reset();return;}
        next=action==DRAW?1:action==BASIC_RISE?2:0;last=now;
    }
}
