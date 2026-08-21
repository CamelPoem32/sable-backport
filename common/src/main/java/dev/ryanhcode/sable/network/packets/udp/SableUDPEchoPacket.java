package dev.ryanhcode.sable.network.packets.udp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;

public record SableUDPEchoPacket(String text) implements SableUDPPacket {
    public static final SablePacketCodec<SableUDPEchoPacket> CODEC = SablePacketCodec.of(
            (buffer, packet) -> buffer.writeUtf(packet.text),
            buffer -> new SableUDPEchoPacket(buffer.readUtf())
    );

    @Override
    public SableUDPPacketType getType() {
        return SableUDPPacketType.PING;
    }
}
