package com.myriad.character.skills.shuo;
import com.myriad.network.AbilityVfxPayload;
import java.util.*;
/** Bounded visual-only leases sourced from already authorized skill packets, never a damage instruction. */
public final class ShuoBladeActivity {
    private final LinkedHashMap<Integer,long[]> owners=new LinkedHashMap<>();
    public void clear(){owners.clear();}
    public void receive(AbilityVfxPayload p,long now){
        if(!p.valid())return;
        owners.entrySet().removeIf(e->Arrays.stream(e.getValue()).allMatch(t->t<=now));
        if(p.kind()==12){
            if(p.angle()==0)owners.remove(p.owner());
            else if(p.theme()==10&&p.angle()==1){var times=owners.get(p.owner());if(times!=null)times[p.variant()]=0;}
            return;
        }
        if(!ShuoSkillVfx.owns(p)||p.started()+p.life()<=now)return;
        if(!owners.containsKey(p.owner())&&owners.size()>=128)owners.remove(owners.keySet().iterator().next());
        var times=owners.computeIfAbsent(p.owner(),id->new long[8]);times[p.variant()]=Math.max(times[p.variant()],p.started()+p.life());
    }
    public float opacity(int owner,long now){
        var times=owners.get(owner);if(times==null)return 0;
        long end=Arrays.stream(times).max().orElse(0);return (float)Math.clamp((end-now)/2.0,0,1);
    }
}
