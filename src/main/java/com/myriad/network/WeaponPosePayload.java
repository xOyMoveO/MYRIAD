package com.myriad.network;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.ActionTime;
import com.myriad.game.VisionSystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** A visible wielder's keyframe. No target, movement or damage is read back from the renderer. */
public record WeaponPosePayload(int owner, Move move, long started, int duration, float progress,
                                float rate, float yaw) implements CustomPacketPayload {
    public enum Move {
        MARK_TICK, CALIBRATE, BARRIER, CANNON_BASIC, CANNON_SHOT, FIXED_SHOT, MOBILE_SHOT, DENIAL, HEAVY_FIRE,
        RAPIER, ECHO, FIELD_SHIELD, NEEDLE_READY, NEEDLE, STEP, BOUNDARY,
        FOLLOW_READY, FOLLOW, CLOSE_FIELD, DROP_FIELD, DOME, LINE_READY, LINE, RELAX,
        MOON_READY, PURSUE, FIRST, SECOND, THIRD, FINAL, SHEATHE;
        public boolean accent() { return this==MARK_TICK || this==CALIBRATE || this==BARRIER || this==ECHO || this==FIELD_SHIELD; }
    }

    public static final Type<WeaponPosePayload> TYPE = new Type<>(Identifier.parse("myriad:weapon_pose"));
    public static final StreamCodec<FriendlyByteBuf,WeaponPosePayload> CODEC = StreamCodec.of(
            (b,p) -> { b.writeVarInt(p.owner); b.writeEnum(p.move); b.writeVarLong(p.started); b.writeVarInt(p.duration);
                b.writeFloat(p.progress); b.writeFloat(p.rate); b.writeFloat(p.yaw); },
            b -> new WeaponPosePayload(b.readVarInt(),b.readEnum(Move.class),b.readVarLong(),b.readVarInt(),
                    b.readFloat(),b.readFloat(),b.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}

    public static void play(ServerPlayer player, Move move, int duration) {
        send(player, move, duration, 0, 1, player.getYRot());
    }
    public static void aimed(ServerPlayer player, Move move, int duration, Vec3 direction) {
        send(player, move, duration, 0, 1, (float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
    }
    public static void windup(Cast cast, Move move, int duration, boolean active) {
        if (!active) { play(cast.player, Move.RELAX, 3); return; }
        var sample = cast.memory("body_keyframe");
        double rate = ActionTime.multiplier(cast.player.getUUID(), cast.now());
        if (cast.windupProgress()==0) sample.point=cast.forward(1);
        else if (sample.distance==rate) return;
        sample.distance=rate;
        Vec3 aim=sample.point;
        float yaw=aim==null?cast.player.getYRot():(float)Math.toDegrees(Math.atan2(-aim.x,aim.z));
        send(cast.player,move,duration,cast.windupProgress(),(float)rate,yaw);
    }
    public static void travel(Cast cast, Move move, int duration, float progress, Vec3 direction) {
        var previous=cast.memory("body_travel_rate");
        double rate=ActionTime.multiplier(cast.player.getUUID(),cast.now());
        if(progress>0 && previous.distance==rate)return;
        previous.distance=rate;
        send(cast.player,move,duration,progress,(float)rate,(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
    }
    private static void send(ServerPlayer player, Move move, int duration, float progress, float rate, float yaw) {
        var pose=new WeaponPosePayload(player.getId(),move,player.level().getGameTime(),duration,progress,rate,yaw);
        for(var viewer:player.level().players())
            if(viewer==player || viewer.distanceToSqr(player)<64*64 && VisionSystem.canSee(viewer,player))
                Packets.sendToPlayer(viewer,pose);
    }
}
