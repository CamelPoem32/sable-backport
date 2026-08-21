package dev.ryanhcode.sable.network.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import java.util.Objects;

public record SablePacketDefinition<T extends SableTCPPacket>(
        int id,
        Class<T> packetType,
        SablePacketDirection direction,
        SablePacketCodec<T> codec) {

    public SablePacketDefinition {
        if (id < 0) {
            throw new IllegalArgumentException("Packet id must be non-negative");
        }
        Objects.requireNonNull(packetType, "packetType");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(codec, "codec");
    }
}
