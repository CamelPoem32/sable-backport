package dev.ryanhcode.sable.network.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import java.util.Objects;
import java.util.function.BiConsumer;

public record SablePacketRegistration<T extends SableTCPPacket>(
        int id,
        Class<T> packetType,
        SablePacketDirection direction,
        SablePacketCodec<T> codec,
        BiConsumer<T, SablePacketContext> handler) {

    public SablePacketRegistration {
        if (id < 0) {
            throw new IllegalArgumentException("Packet id must be non-negative");
        }
        Objects.requireNonNull(packetType, "packetType");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(handler, "handler");
    }
}
