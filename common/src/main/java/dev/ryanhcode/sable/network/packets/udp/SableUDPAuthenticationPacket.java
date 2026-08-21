package dev.ryanhcode.sable.network.packets.udp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import java.util.UUID;

public record SableUDPAuthenticationPacket(String token) implements SableUDPPacket {
    public static final SablePacketCodec<SableUDPAuthenticationPacket> CODEC = SablePacketCodec.of(
            (buffer, packet) -> buffer.writeUtf(packet.token),
            buffer -> {
                final String token = buffer.readUtf();
                UUID.fromString(token);
                return new SableUDPAuthenticationPacket(token);
            }
    );

    @Override
    public SableUDPPacketType getType() {
        return SableUDPPacketType.AUTH;
    }
}
