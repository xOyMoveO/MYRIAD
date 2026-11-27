package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.ShuoViewPayload;
import java.util.List;
import org.joml.Vector3f;

public final class ShuoTrail {
    private ShuoTrail() {}
    public static List<Quad> build(ShuoMotion motion,double now,boolean drawn,boolean mirror,boolean occupied) {
        int action=motion.action(now);
        if (!ShuoViewPayload.basicAction(action)) return List.of();
        double from=action==ShuoViewPayload.DRAW ? (motion.fromDrawn()?.03:.32) : .18;
        float currentOffset=occupied?ShuoRig.occupiedOffset(motion.frame(now,drawn),mirror):0;
        return MeleeBladeLight.swept(now,motion.started(),motion.strokeAge(from),MeleeStrokeTiming.END_OF_CUT,
                MeleeBladeLight.MOON,new Vector3f(0,ShuoRig.MODEL_GRIP_TO_MOUTH,0),
                new Vector3f(0,ShuoRig.MODEL_GRIP_TO_TIP,0),t->{
                    var frame=motion.frame(t,drawn);
                    var blade=ShuoRig.blade(frame,mirror);
                    return occupied?blade.translateLocal(ShuoRig.occupiedOffset(frame,mirror)-currentOffset,0,0):blade;
                });
    }
}
