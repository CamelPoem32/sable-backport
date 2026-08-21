package dev.ryanhcode.sable.network.udp;

import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPClientboundKeepAlivePacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPEchoPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPServerboundAlivePacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;

public enum SableUDPPacketType {
    PING(PacketFlow.CLIENTBOUND, SableUDPEchoPacket.CODEC),
    SNAPSHOT(PacketFlow.CLIENTBOUND, ClientboundSableSnapshotDualPacket.CODEC),
    SNAPSHOT_INFO(PacketFlow.CLIENTBOUND, ClientboundSableSnapshotInfoDualPacket.CODEC),
    AUTH(PacketFlow.SERVERBOUND, SableUDPAuthenticationPacket.CODEC),
    KEEP_ALIVE_CLIENTBOUND(PacketFlow.CLIENTBOUND, SableUDPClientboundKeepAlivePacket.CODEC),
    ALIVE_SERVERBOUND(PacketFlow.SERVERBOUND, SableUDPServerboundAlivePacket.CODEC);

    public static final SableUDPPacketType[] VALUES = SableUDPPacketType.values();

    private final PacketFlow flow;
    private final SablePacketCodec<? extends SableUDPPacket> codec;

    SableUDPPacketType(final PacketFlow flow, final SablePacketCodec<? extends SableUDPPacket> codec) {
        this.flow = flow;
        this.codec = codec;
    }

    public PacketFlow flow() {
        return this.flow;
    }

    public SableUDPPacket create(final FriendlyByteBuf buf) {
        return this.codec.decode(buf);
    }

    public void write(final FriendlyByteBuf buf, final SableUDPPacket packet) {
        //noinspection unchecked,rawtypes
        ((SablePacketCodec) this.codec).encode(buf, packet);
    }
}
