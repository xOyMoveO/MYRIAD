package com.myriad.client;

import com.myriad.network.YikeStatePayload;
import com.myriad.registry.ModSounds;
import com.myriad.registry.ModItems;
import com.myriad.character.skills.yike.YikeWeaponAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.*;

/** Visible-actor snapshots expire promptly if updates stop or the world/role changes. */
public final class YikeClientState {
    private record Entry(YikeStatePayload state,long received,YikeWeaponAnimation.Controller animation,
                         net.minecraft.world.phys.Vec3 previousAim){}
    private static final Map<Integer,Entry> STATES=new HashMap<>();
    private static final Map<Integer,BeamSound> LOOPS=new HashMap<>();
    private static Object world;
    private YikeClientState(){}
    public static void receive(YikeStatePayload p){
        var mc=Minecraft.getInstance();if(!p.valid()||mc.level==null)return;check();if(!ClientDataHolder.modEnabled()||!validOwner(p.owner()))return;
        var old=STATES.get(p.owner());if(old!=null&&old.state.tick()>p.tick())return;
        var animation=old==null?new YikeWeaponAnimation.Controller():old.animation;
        animation.accept(new YikeWeaponAnimation.Snapshot(p.mode(),p.windup(),p.progress(),p.tick()),mc.level.getGameTime());
        STATES.put(p.owner(),new Entry(p,mc.level.getGameTime(),animation,old==null?p.aim():old.state.aim()));
        if(p.mode()==3){if(!LOOPS.containsKey(p.owner())){var sound=new BeamSound(p.owner());LOOPS.put(p.owner(),sound);mc.getSoundManager().play(sound);}}
        else stop(p.owner());
    }
    public static void reset(){for(var loop:LOOPS.values())loop.end();LOOPS.clear();STATES.clear();}
    private static void check(){var mc=Minecraft.getInstance();if(world!=mc.level||!ClientDataHolder.modEnabled()){reset();world=mc.level;}}
    private static boolean validOwner(int owner){var mc=Minecraft.getInstance();var actor=mc.level==null?null:mc.level.getEntity(owner);
        return actor instanceof net.minecraft.world.entity.LivingEntity living&&actor.isAlive()&&!actor.isRemoved()
                &&!actor.isSpectator()&&living.getMainHandItem().is(ModItems.HONG_LIE.get())
                &&(actor!=mc.player||"yike".equals(ClientDataHolder.characterId()));}
    public static YikeStatePayload get(int owner){check();var mc=Minecraft.getInstance();var e=STATES.get(owner);
        if(e!=null&&mc.level!=null&&validOwner(owner)&&mc.level.getGameTime()-e.received<=5)return e.state;
        STATES.remove(owner);stop(owner);return null;}
    /** Receive-side hook for the committed MUZZLE/LAST_MUZZLE events, never Q preparation. */
    public static void onMuzzle(int owner,long started){if(get(owner)==null)return;var mc=Minecraft.getInstance();
        STATES.get(owner).animation.fire(started,mc.level.getGameTime());}
    public static YikeWeaponAnimation.Pose weaponPose(int owner,float partial){
        if(get(owner)==null)return YikeWeaponAnimation.CLOSED;var mc=Minecraft.getInstance();
        return STATES.get(owner).animation.pose(mc.level.getGameTime()+Math.clamp(partial,0,1));}
    public static net.minecraft.world.phys.Vec3 weaponAim(int owner,float partial){
        var state=get(owner);if(state==null)return net.minecraft.world.phys.Vec3.ZERO;var mc=Minecraft.getInstance();var e=STATES.get(owner);
        double alpha=Math.clamp(mc.level.getGameTime()+partial-e.received,0,1);
        var aim=e.previousAim.lerp(state.aim(),alpha);return aim.lengthSqr()<1E-10?state.aim():aim.normalize();}
    public static void tick(){check();var mc=Minecraft.getInstance();if(mc.level==null)return;
        for(int id:new ArrayList<>(STATES.keySet()))if(get(id)==null){STATES.remove(id);stop(id);}
    }
    private static void stop(int owner){var old=LOOPS.remove(owner);if(old!=null)old.end();}
    private static final class BeamSound extends AbstractTickableSoundInstance {
        final int owner;
        BeamSound(int owner){super(ModSounds.yike("beam_loop"),SoundSource.PLAYERS,RandomSource.create());this.owner=owner;looping=true;delay=0;volume=.22f;pitch=1;}
        void end(){stop();}
        @Override public void tick(){var mc=Minecraft.getInstance();var s=get(owner);var e=mc.level==null?null:mc.level.getEntity(owner);
            if(s==null||s.mode()!=3||e==null||!e.isAlive()||!ClientDataHolder.modEnabled()){end();LOOPS.remove(owner);return;}
            x=e.getX();y=e.getY()+1;z=e.getZ();
        }
    }
}
