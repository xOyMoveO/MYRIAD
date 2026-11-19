package com.myriad.network;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Owner-only, immutable death snapshot. No locations, live enemy state or client write endpoint. */
public record DeathRecapPayload(boolean present, boolean limited, List<Hit> hits, List<Control> controls)
        implements CustomPacketPayload {
    public static final int WINDOW = 200, MAX_HITS = 256, MAX_CONTROLS = 128;
    public static final UUID ENVIRONMENT = new UUID(0, 0);
    public static final Type<DeathRecapPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("myriad", "death_recap"));
    public record Source(UUID id, String name, String role, String skill, int category, boolean basic) {}
    /** Kind: 0 physical, 1 magic, 2 true, 3 unclassified environmental damage. */
    public record Hit(int before, Source source, int kind, float health, float shield, boolean fatal) {}
    /** Kind: 1 slow, 2 local action slow, 3 forced movement, 4 root, 5 stun. */
    public record Control(int before, int ticks, Source source, int kind) {}

    public DeathRecapPayload {
        hits = List.copyOf(hits); controls = List.copyOf(controls);
        if (hits.size() > MAX_HITS || controls.size() > MAX_CONTROLS || !present && (!hits.isEmpty() || !controls.isEmpty()))
            throw new IllegalArgumentException("Invalid recap size");
    }
    public static DeathRecapPayload empty() { return new DeathRecapPayload(false, false, List.of(), List.of()); }
    public static final StreamCodec<FriendlyByteBuf, DeathRecapPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeBoolean(p.present); b.writeBoolean(p.limited); b.writeVarInt(p.hits.size());
        for (var h : p.hits) {
            b.writeVarInt(h.before); source(b, h.source); b.writeVarInt(h.kind);
            b.writeFloat(h.health); b.writeFloat(h.shield); b.writeBoolean(h.fatal);
        }
        b.writeVarInt(p.controls.size());
        for (var c : p.controls) { b.writeVarInt(c.before); b.writeVarInt(c.ticks); source(b, c.source); b.writeVarInt(c.kind); }
    }, b -> {
        boolean present = b.readBoolean(), limited = b.readBoolean();
        int n = count(b, MAX_HITS); var hits = new java.util.ArrayList<Hit>(n);
        for (int i = 0; i < n; i++) {
            int before = range(b.readVarInt(), 0, WINDOW); var source = source(b);
            int kind = range(b.readVarInt(), 0, 3); float health = amount(b), shield = amount(b);
            hits.add(new Hit(before, source, kind, health, shield, b.readBoolean()));
        }
        n = count(b, MAX_CONTROLS); var controls = new java.util.ArrayList<Control>(n);
        for (int i = 0; i < n; i++) {
            int before = range(b.readVarInt(), 0, WINDOW), ticks = range(b.readVarInt(), 1, WINDOW + 1);
            var source = source(b); int kind = range(b.readVarInt(), 1, 6);
            controls.add(new Control(before, ticks, source, kind));
        }
        return new DeathRecapPayload(present, limited, hits, controls);
    });
    private static int count(FriendlyByteBuf b, int max) { return range(b.readVarInt(), 0, max); }
    private static int range(int n, int min, int max) {
        if (n < min || n > max) throw new io.netty.handler.codec.DecoderException("Invalid death recap range");
        return n;
    }
    private static float amount(FriendlyByteBuf b) {
        float n = b.readFloat();
        if (!Float.isFinite(n) || n < 0) throw new io.netty.handler.codec.DecoderException("Invalid death recap amount");
        return n;
    }
    private static void source(FriendlyByteBuf b, Source s) {
        b.writeUUID(s.id); b.writeUtf(s.name, 96); b.writeUtf(s.role, 64); b.writeUtf(s.skill, 128);
        b.writeVarInt(s.category); b.writeBoolean(s.basic);
    }
    private static Source source(FriendlyByteBuf b) {
        return new Source(b.readUUID(), b.readUtf(96), b.readUtf(64), b.readUtf(128), range(b.readVarInt(), 0, 5), b.readBoolean());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
