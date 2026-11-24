package com.myriad.combat;

import com.myriad.game.VisionSystem;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.network.Packets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.atomic.AtomicInteger;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Visual companion to MagicBullet; never changes collision, damage, or action time. */
final class MagicBulletVisuals {
    private static final AtomicInteger SEQUENCE=new AtomicInteger();
    private MagicBulletVisuals(){}

    static int theme(String role){
        if(role==null)return 0;
        return switch(role){case "xuanchen"->3;case "hanze"->6;case "qingluo"->7;default->0;};
    }

    public static int start(){return SEQUENCE.updateAndGet(n->n==Integer.MAX_VALUE?1:n+1);}

    public static void sample(ServerPlayer player,int theme,int id,Vec3 from,Vec3 to){
        if(from.distanceToSqr(to)>1E-8)nearby(player,new AbilityVfxPayload(player.getId(),-1,theme,0,FLIGHT,
                from,to,.20f,0,3,player.level().getGameTime(),false,id));
    }

    public static void impact(ServerPlayer player,int theme,Vec3 at,Vec3 direction){
        nearby(player,new AbilityVfxPayload(player.getId(),-1,theme,0,BURST,
                at,at.add(direction),.42f,41,7,player.level().getGameTime(),false));
    }

    public static void end(ServerPlayer player,int theme,int id){
        var end=new AbilityVfxPayload(player.getId(),-1,theme,0,CANCEL,Vec3.ZERO,Vec3.ZERO,0,2,1,
                player.level().getGameTime(),false,id);
        for(var viewer:player.level().players())Packets.sendToPlayer(viewer,end);
    }

    private static void nearby(ServerPlayer player,AbilityVfxPayload p){
        for(var viewer:player.level().players()){
            if(viewer.position().distanceToSqr(p.from())>4096&&viewer.position().distanceToSqr(p.to())>4096)continue;
            if(viewer==player||VisionSystem.canSee(viewer,player))Packets.sendToPlayer(viewer,p);
        }
    }
}
