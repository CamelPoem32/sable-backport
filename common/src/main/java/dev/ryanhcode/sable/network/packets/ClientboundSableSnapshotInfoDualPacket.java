package dev.ryanhcode.sable.network.packets;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import net.minecraft.network.FriendlyByteBuf;

public final class ClientboundSableSnapshotInfoDualPacket implements SableUDPPacket, SableTCPPacket {
    public static final SablePacketCodec<ClientboundSableSnapshotInfoDualPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.encode(buf), ClientboundSableSnapshotInfoDualPacket::new);
    private final int msSinceLast;
    private final int gameTick;
    private final boolean stopped;

    public ClientboundSableSnapshotInfoDualPacket(final int msSinceLast, final int gameTick, final boolean stopped) {
        this.msSinceLast = msSinceLast;
        this.gameTick = gameTick;
        this.stopped = stopped;
    }

    public ClientboundSableSnapshotInfoDualPacket(final FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    public void encode(final FriendlyByteBuf buf) {
        buf.writeInt(this.msSinceLast);
        buf.writeInt(this.gameTick);
        buf.writeBoolean(this.stopped);
    }

    @Override
    public SableUDPPacketType getType() {
        return SableUDPPacketType.SNAPSHOT_INFO;
    }

    public int msSinceLast() {
        return this.msSinceLast;
    }

    public int gameTick() {
        return this.gameTick;
    }

    public boolean stopped() {
        return this.stopped;
    }
}
