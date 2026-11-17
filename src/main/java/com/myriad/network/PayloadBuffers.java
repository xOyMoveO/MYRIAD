package com.myriad.network;

import java.util.ArrayList;
import java.util.Collection;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;

/** The existing VarInt-sized collection wire format, whose buffer helpers were removed in 26.3. */
final class PayloadBuffers {
    private PayloadBuffers() {}

    static <T> void writeCollection(FriendlyByteBuf buf, Collection<T> values,
            StreamEncoder<? super FriendlyByteBuf, T> encoder) {
        buf.writeVarInt(values.size());
        for (T value : values) encoder.encode(buf, value);
    }

    static <T> List<T> readList(FriendlyByteBuf buf,
            StreamDecoder<? super FriendlyByteBuf, T> decoder) {
        int size = buf.readVarInt();
        List<T> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(decoder.decode(buf));
        return values;
    }

    static <K, V> void writeMap(FriendlyByteBuf buf, Map<K, V> values,
            StreamEncoder<? super FriendlyByteBuf, K> keyEncoder,
            StreamEncoder<? super FriendlyByteBuf, V> valueEncoder) {
        buf.writeVarInt(values.size());
        for (var entry : values.entrySet()) {
            keyEncoder.encode(buf, entry.getKey());
            valueEncoder.encode(buf, entry.getValue());
        }
    }

    static <K, V> Map<K, V> readMap(FriendlyByteBuf buf,
            StreamDecoder<? super FriendlyByteBuf, K> keyDecoder,
            StreamDecoder<? super FriendlyByteBuf, V> valueDecoder) {
        int size = buf.readVarInt();
        Map<K, V> values = Maps.newHashMapWithExpectedSize(size);
        for (int i = 0; i < size; i++) values.put(keyDecoder.decode(buf), valueDecoder.decode(buf));
        return values;
    }
}
