package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundFinalizeSubLevelPacket(long plotCoordinate) implements SableTCPPacket {

    public static final SablePacketCodec<ClientboundFinalizeSubLevelPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.write(buf), ClientboundFinalizeSubLevelPacket::read);

    private void write(final FriendlyByteBuf buf) {
        buf.writeLong(this.plotCoordinate);
    }

    private static ClientboundFinalizeSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ClientboundFinalizeSubLevelPacket(buf.readLong());
    }

}
