package com.myriad.character.ability;

import com.myriad.network.AbilityVfxPayload;
import java.util.*;

/** Client-independent rules for ended flight/field instances and queued-frame expiry. */
public final class AbilityVisualLifetime {
    private record Key(int owner,int theme,int variant,int instance){}
    private final Map<Key,Long> ended=new HashMap<>();
    private static Key key(AbilityVfxPayload p){return new Key(p.owner(),p.theme(),p.variant(),p.instance());}
    public void end(AbilityVfxPayload p,long now){
        prune(now);if(p.instance()>0)ended.put(key(p),now+32);
    }
    public boolean finished(AbilityVfxPayload p,long now){
        if(p.instance()<=0||(p.kind()!=AbilityVfxGeometry.FLIGHT
                &&!com.myriad.character.skills.keheng.KehengVfx.persistent(p)))return false;
        Long until=ended.get(key(p));return until!=null&&now<until;
    }
    public static boolean expired(AbilityVfxPayload p,long now){return now-p.started()>=p.life();}
    public void prune(long now){ended.entrySet().removeIf(e->e.getValue()<=now);}
    public void clearOwner(int owner){ended.keySet().removeIf(k->k.owner==owner);}
    public void clear(){ended.clear();}
    public int retained(){return ended.size();}
}
