package com.myriad.game;

import com.myriad.network.*;
import com.myriad.player.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** All admission, position, audience and lifecycle decisions are server-owned. */
public final class TeamSignals {
    private record Active(TeamSignalsPayload.Entry entry,Faction team,String role,String dimension,boolean personalTraining,long until) {}
    private record Rate(long last,ArrayDeque<Long> times) {}
    private static final List<Active> ACTIVE=new ArrayList<>();
    private static final Map<UUID,Rate> RATES=new HashMap<>();
    private static final Map<UUID,ArrayDeque<Long>> ATTEMPTS=new HashMap<>();
    private static final Set<UUID> SENT=new HashSet<>();
    private static final Map<UUID,Long> FEEDBACK=new HashMap<>();
    private static GameManager match; private static boolean trainingSession; private static long serial, matchStart;
    private TeamSignals(){}
    public static void clear(){ACTIVE.clear();RATES.clear();ATTEMPTS.clear();SENT.clear();FEEDBACK.clear();match=null;trainingSession=false;}
    public static void rejected(ServerPlayer p){long now=p.level().getGameTime();Long previous=FEEDBACK.get(p.getUUID());
        if(previous==null||now<previous||now-previous>=20){FEEDBACK.put(p.getUUID(),now);
            p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("signal.myriad.rejected"),true);}}
    private static boolean playing(ServerPlayer player){
        return ModState.isEnabled() && TrainingGround.canFight(player);
    }
    private static Faction teamOf(ServerPlayer p){return TrainingMode.isEnabled(p)?PlayerDataProvider.get(p).getFaction()
            :GameManager.get()==null?null:GameManager.get().factionOf(p);}
    private static long start(){var active=GameManager.get();return active==null?-1:active.getMatchStartTime();}
    private static boolean contextChanged(){return match!=GameManager.get()||trainingSession!=TrainingMode.isEnabled()||matchStart!=start();}
    private static void bindContext(){match=GameManager.get();trainingSession=TrainingMode.isEnabled();matchStart=start();}
    private static void resetContext(MinecraftServer server){
        ACTIVE.removeIf(signal -> !signal.personalTraining);
        for (var player : server.getPlayerList().getPlayers()) {
            if (TrainingGround.isTraining(player)) continue;
            UUID id = player.getUUID();
            RATES.remove(id);
            ATTEMPTS.remove(id);
            FEEDBACK.remove(id);
            if (SENT.contains(id)) send(player, List.of());
        }
    }
    public static Entity viewpoint(ServerPlayer p){
        if(!p.isSpectator())return p.isAlive()?p:null;
        if(!HeroRespawnSystem.isWaiting(p))return null;
        Entity camera=p.getCamera();
        return camera instanceof ServerPlayer ally&&ally!=p&&ally.isAlive()&&!ally.isSpectator()&&ally.level()==p.level()
                &&teamOf(p)!=null&&teamOf(p)==teamOf(ally)?ally:null;
    }
    public static boolean request(ServerPlayer p,TeamSignalRequest request){
        if(!playing(p)||!request.valid())return false;
        if(contextChanged()){resetContext(p.level().getServer());bindContext();}
        Faction team=teamOf(p); Entity eye=viewpoint(p);
        if(team==null||eye==null||PlayerDataProvider.get(p).getFaction()!=team)return false;
        if(!com.myriad.character.rework.FirstGroupSkills.active(PlayerDataProvider.get(p).getCharacterId()))return false;
        long now=p.level().getGameTime();
        var attempts=ATTEMPTS.computeIfAbsent(p.getUUID(),id->new ArrayDeque<>());
        attempts.removeIf(t->t<=now-20||t>now);if(attempts.size()>=8)return false;attempts.add(now);
        Rate rate=RATES.get(p.getUUID());
        if(rate!=null){rate.times.removeIf(t->t<=now-100||t>now);
            if(now<rate.last||now-rate.last<20||rate.times.size()>=3)return false;}
        Vec3 point=request.position(),origin=eye.getEyePosition();
        if(!TeamSignalRules.inRange(origin,point)||!p.level().hasChunkAt(net.minecraft.core.BlockPos.containing(point))
                ||!p.level().getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(point))
                ||!VisionSystem.canSeePosition((ServerPlayer)eye,point))return false;
        int steps=Math.max(1,(int)Math.ceil(origin.distanceTo(point)/4));
        for(int i=0;i<=steps;i++)if(!p.level().hasChunkAt(net.minecraft.core.BlockPos.containing(origin.lerp(point,i/(double)steps))))return false;
        var hit=p.level().clip(new ClipContext(origin,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,eye));
        if(hit.getType()!=HitResult.Type.MISS&&hit.getLocation().distanceToSqr(point)>.36)return false;
        if(rate==null)rate=new Rate(now-20,new ArrayDeque<>());
        rate.times.add(now);RATES.put(p.getUUID(),new Rate(now,rate.times));
        while(ACTIVE.stream().filter(a->a.entry.sender().equals(p.getUUID())).count()>=2)
            ACTIVE.remove(ACTIVE.stream().filter(a->a.entry.sender().equals(p.getUUID())).findFirst().orElseThrow());
        String dimension = p.level().dimension().identifier().toString();
        while(ACTIVE.stream().filter(a->a.team==team && a.dimension.equals(dimension)).count()>=TeamSignalsPayload.LIMIT){
            ACTIVE.remove(ACTIVE.stream().filter(a->a.team==team && a.dimension.equals(dimension)).findFirst().orElseThrow()); }
        String name=p.getName().getString();if(name.length()>64)name=name.substring(0,64);
        ACTIVE.add(new Active(new TeamSignalsPayload.Entry(++serial,p.getUUID(),name,request.kind(),point,100),team,
                PlayerDataProvider.get(p).getCharacterId(),dimension,TrainingGround.isTraining(p),now+100));
        publish(p.level().getServer());return true;
    }
    public static void tick(MinecraftServer server){
        if(contextChanged()){
            resetContext(server); bindContext();
        }
        boolean changed=ACTIVE.removeIf(a->{var p=server.getPlayerList().getPlayer(a.entry.sender());
            return p==null||!playing(p)||teamOf(p)!=a.team||PlayerDataProvider.get(p).getFaction()!=a.team
                    ||!Objects.equals(PlayerDataProvider.get(p).getCharacterId(),a.role)||!p.level().dimension().identifier().toString().equals(a.dimension)
                    ||p.level().getGameTime()>=a.until;});
        RATES.keySet().removeIf(id->server.getPlayerList().getPlayer(id)==null);
        ATTEMPTS.keySet().removeIf(id->server.getPlayerList().getPlayer(id)==null);
        FEEDBACK.keySet().removeIf(id->server.getPlayerList().getPlayer(id)==null);
        SENT.removeIf(id->server.getPlayerList().getPlayer(id)==null);
        if(changed||server.getTickCount()%5==0)publish(server);
    }
    private static void publish(MinecraftServer server){for(var p:server.getPlayerList().getPlayers()){
        Faction team=teamOf(p);var list=new ArrayList<TeamSignalsPayload.Entry>();
        if(playing(p)&&team!=null&&team==PlayerDataProvider.get(p).getFaction()&&(p.isAlive()&&!p.isSpectator()||HeroRespawnSystem.isWaiting(p)))
            for(var a:ACTIVE)if(a.team==team&&a.dimension.equals(p.level().dimension().identifier().toString())&&a.until>p.level().getGameTime()){
                var e=a.entry;list.add(new TeamSignalsPayload.Entry(e.serial(),e.sender(),e.name(),e.kind(),e.position(),(int)(a.until-p.level().getGameTime())));}
        if(!list.isEmpty()||SENT.contains(p.getUUID()))send(p,list);
    }}
    private static void send(ServerPlayer p,List<TeamSignalsPayload.Entry> list){
        var team=PlayerDataProvider.get(p).getFaction();int faction=team==null?0:team==Faction.GUARDIAN?1:2;
        if (p.connection != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p,TeamSignalsPayload.TYPE))
            Packets.sendToPlayer(p,new TeamSignalsPayload(p.level().dimension().identifier().toString(),faction,list));
        if(list.isEmpty())SENT.remove(p.getUUID());else SENT.add(p.getUUID());
    }
}
