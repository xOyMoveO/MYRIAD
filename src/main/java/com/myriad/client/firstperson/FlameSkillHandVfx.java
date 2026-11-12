package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.fenjue.rework.FlameMajorVfx;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;
import static com.myriad.network.FlameSkillViewPayload.*;

/** Small palm-local native-HD flames, not a full-screen orange overlay. */
public final class FlameSkillHandVfx {
    private FlameSkillHandVfx(){}
    public static List<Quad> build(FlameSkillMotion.Frame f,double tick,boolean left){
        var out=new ArrayList<Quad>();int action=f.action();double t=f.progress();
        boolean palms=action==FlameSkillMotion.AIM||action==CAGE_FIRE||action==BURST_READY||action==BURST_FIRE||action==ARMOR;
        if(!palms)return List.of();
        double alpha=action==CAGE_FIRE||action==BURST_FIRE?1-t:action==ARMOR?Math.sin(t*Math.PI):.75;
        var hand=FlameSkillRig.off(f,left).transformPosition(new Vector3f(0,.02f,.16f));
        FlameMajorVfx.fire(out,new Vec3(hand.x,hand.y-.05,hand.z),new Vec3(1,0,0),.115,.27,tick,alpha*.90,32);
        FlameMajorVfx.fire(out,new Vec3(hand.x+.03,hand.y-.04,hand.z+.018),new Vec3(1,0,0),.065,.19,tick+5,alpha*.65,32);
        if(action==BURST_READY||action==BURST_FIRE||action==ARMOR){
            var main=FlameDaoRig.hand(f.main(),0,left).transformPosition(new Vector3f(0,.02f,.16f));
            FlameMajorVfx.fire(out,new Vec3(main.x,main.y-.05,main.z),new Vec3(1,0,0),.115,.27,tick+8,alpha*.90,32);
        }
        return List.copyOf(out);
    }
}
