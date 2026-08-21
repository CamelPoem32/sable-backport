package dev.ryanhcode.sable.network.packets.udp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;

public record SableUDPClientboundKeepAlivePacket() implements SableUDPPacket {
    public static final SablePacketCodec<SableUDPClientboundKeepAlivePacket> CODEC = SablePacketCodec.of(
            (buffer, packet) -> { },
            buffer -> new SableUDPClientboundKeepAlivePacket()
    );

    @Override
    public SableUDPPacketType getType() {
        return SableUDPPacketType.KEEP_ALIVE_CLIENTBOUND;
    }
}
