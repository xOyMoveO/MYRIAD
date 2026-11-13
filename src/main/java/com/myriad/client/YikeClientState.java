package com.myriad.client;

import com.myriad.network.YikeStatePayload;
import com.myriad.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.*;

/** Visible-actor snapshots expire promptly if updates stop or the world/role changes. */
public final class YikeClientState {
    private record Entry(YikeStatePayload state,long received){}
    private static final Map<Integer,Entry> STATES=new HashMap<>();
    private static final Map<Integer,BeamSound> LOOPS=new HashMap<>();
    private static Object world;
    private YikeClientState(){}
    public static void receive(YikeStatePayload p){
        var mc=Minecraft.getInstance();if(!p.valid()||mc.level==null)return;check();
        var old=STATES.get(p.owner());if(old!=null&&old.state.tick()>p.tick())return;
        STATES.put(p.owner(),new Entry(p,mc.level.getGameTime()));
        if(p.mode()==3){if(!LOOPS.containsKey(p.owner())){var sound=new BeamSound(p.owner());LOOPS.put(p.owner(),sound);mc.getSoundManager().play(sound);}}
        else stop(p.owner());
    }
    private static void check(){var mc=Minecraft.getInstance();if(world!=mc.level){for(var loop:LOOPS.values())loop.end();LOOPS.clear();STATES.clear();world=mc.level;}}
    public static YikeStatePayload get(int owner){check();var mc=Minecraft.getInstance();var e=STATES.get(owner);
        return e!=null&&mc.level!=null&&mc.level.getGameTime()-e.received<=5?e.state:null;}
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
