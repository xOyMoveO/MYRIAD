package com.myriad.network;

import com.myriad.Myriad;
import com.myriad.game.QueueLobby;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The server owns queue membership, proposal deadlines and private draft choices. */
public final class LobbyNetworking {
    private LobbyNetworking() {}

    public record Action(String action, String value, long token) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "lobby_action"));
        public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.ofMember(
                (action, buf) -> {
                    buf.writeUtf(action.action, 16);
                    buf.writeUtf(action.value, 64);
                    buf.writeLong(action.token);
                },
                buf -> new Action(buf.readUtf(16), buf.readUtf(64), buf.readLong()));

        @Override public Type<Action> type() { return TYPE; }
    }

    public record Seat(UUID id, String name, String faction, String role) {
        private void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, 64);
            buf.writeUtf(faction, 16);
            buf.writeUtf(role, 64);
        }

        private static Seat read(FriendlyByteBuf buf) {
            return new Seat(buf.readUUID(), buf.readUtf(64), buf.readUtf(16), buf.readUtf(64));
        }
    }

    /** Times are whole seconds. Position is one-based, and -1 means there is no proposal. */
    public record Snapshot(String arena, String queuedArena, int count, int capacity, int position,
                           int waitedSeconds, int voteSeconds, String reason, boolean canPropose,
                           boolean participant, boolean training, long queueToken, long pickToken,
                           String preselected, boolean queuedMatch, boolean sparring, long proposalToken,
                           List<Seat> seats, String picker, int pickSeconds, int preparationSeconds,
                           int remakeWaitSeconds, int remakeOfferSeconds, String remakeOfferId)
            implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "lobby_state_v2"));
        public static final StreamCodec<FriendlyByteBuf, Snapshot> CODEC =
                StreamCodec.ofMember(Snapshot::write, Snapshot::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(arena, 8);
            buf.writeUtf(queuedArena, 8);
            buf.writeVarInt(count);
            buf.writeVarInt(capacity);
            buf.writeVarInt(position);
            buf.writeVarInt(waitedSeconds);
            buf.writeVarInt(voteSeconds);
            buf.writeUtf(reason, 24);
            buf.writeBoolean(canPropose);
            buf.writeBoolean(participant);
            buf.writeBoolean(training);
            buf.writeLong(queueToken);
            buf.writeLong(pickToken);
            buf.writeUtf(preselected, 64);
            buf.writeBoolean(queuedMatch);
            buf.writeBoolean(sparring);
            buf.writeLong(proposalToken);
            PayloadBuffers.writeCollection(buf, seats, (buffer, seat) -> seat.write(buffer));
            buf.writeUtf(picker, 36);
            buf.writeVarInt(pickSeconds);
            buf.writeVarInt(preparationSeconds);
            buf.writeVarInt(remakeWaitSeconds);
            buf.writeVarInt(remakeOfferSeconds);
            buf.writeUtf(remakeOfferId, 64);
        }

        private static Snapshot read(FriendlyByteBuf buf) {
            return new Snapshot(buf.readUtf(8), buf.readUtf(8), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(24),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readLong(),
                    buf.readLong(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean(), buf.readLong(),
                    readSeats(buf), buf.readUtf(36), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(64));
        }

        private static List<Seat> readSeats(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > 10) throw new IllegalArgumentException("Invalid draft size");
            List<Seat> roster = new ArrayList<>(count);
            for (int i = 0; i < count; i++) roster.add(Seat.read(buf));
            return List.copyOf(roster);
        }

        @Override public Type<Snapshot> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE, Snapshot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RemadePayload.TYPE, RemadePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE,
                (action, context) -> QueueLobby.handle(context.player(), action));
    }
}
