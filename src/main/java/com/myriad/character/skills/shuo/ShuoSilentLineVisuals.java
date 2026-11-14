package com.myriad.character.skills.shuo;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;

/** Per-cast measured path only. No damage, movement, saved state or static owner cache. */
public final class ShuoSilentLineVisuals {
    private record Segment(Vec3 from,Vec3 to,int instance) {}
    private final AbilityRuntime.Cast cast;
    private final ArrayList<Segment> segments=new ArrayList<>();
    private Vec3 previous;
    private boolean holding,finished;
    private long lastRefresh=-1;
    public ShuoSilentLineVisuals(AbilityRuntime.Cast cast){this.cast=cast;previous=point();}
    private Vec3 point(){return cast.player.position().add(0,.9,0);}

    private void sample(){
        Vec3 next=point(),delta=next.subtract(previous);double distance=delta.length();
        if(distance<.01)return;
        // Never bridge a forced relocation or an unobserved long jump with an invented slash.
        if(distance>3){stop();return;}
        if(!segments.isEmpty()){
            var last=segments.getLast();var direction=last.to.subtract(last.from).normalize();
            if(last.to.distanceToSqr(previous)<1E-8&&direction.dot(delta.normalize())>.9999){
                segments.set(segments.size()-1,new Segment(last.from,next,last.instance));previous=next;return;
            }
        }
        if(segments.size()>=24){stop();return;}
        segments.add(new Segment(previous,next,cast.newVisualInstance()));previous=next;
    }
    public void start(){
        cast.whileActive(240,age->{
            if(finished)return false;
            if(lastRefresh==cast.now())return true;lastRefresh=cast.now();
            if(cast.data.getBuff("g1_stun")>0||cast.data.getBuff("g1_root")>0||!holding&&!AbilityRuntime.moving(cast.player)){
                stop();ShuoVisuals.cancel(cast.player,cast.skill.index());return false;
            }
            if(!holding)sample();
            if(finished)return false;
            for(var segment:segments)send(segment,ShuoSilentLineVfx.SEAM,3);
            if(age>=240){stop();ShuoVisuals.cancel(cast.player,cast.skill.index());return false;}
            return true;
        },this::stop);
    }
    public void hold(){if(!finished){sample();holding=true;}}
    public void stop(){finished=true;}
    /** Called at the existing delayed damage resolution, after its existing visual cancellation. */
    public void cut(){
        if(finished)return;
        finished=true;
        for(var segment:segments)send(segment,ShuoSilentLineVfx.CUT,10);
    }
    private void send(Segment segment,int signal,int life){
        ShuoVisuals.send(cast.player,new AbilityVfxPayload(cast.player.getId(),-1,10,6,
                com.myriad.character.ability.AbilityVfxGeometry.BEAM,segment.from,segment.to,.9f,signal,life,cast.now(),false,segment.instance));
    }
}
