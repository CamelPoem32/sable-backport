package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundStopMovingSubLevelPacket(long plotCoordinate) implements SableTCPPacket {

    public static final SablePacketCodec<ClientboundStopMovingSubLevelPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.write(buf), ClientboundStopMovingSubLevelPacket::read);

    private void write(final FriendlyByteBuf buf) {
        buf.writeLong(this.plotCoordinate);
    }

    private static ClientboundStopMovingSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ClientboundStopMovingSubLevelPacket(buf.readLong());
    }

}
