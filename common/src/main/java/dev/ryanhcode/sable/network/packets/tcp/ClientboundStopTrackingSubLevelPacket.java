package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundStopTrackingSubLevelPacket(long plotCoordinate) implements SableTCPPacket {

    public static final SablePacketCodec<ClientboundStopTrackingSubLevelPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.write(buf), ClientboundStopTrackingSubLevelPacket::read);

    private static ClientboundStopTrackingSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ClientboundStopTrackingSubLevelPacket(buf.readLong());
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeLong(this.plotCoordinate);
    }

}
