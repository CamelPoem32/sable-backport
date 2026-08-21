package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.util.SableBufferUtils;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundChangeBoundsSubLevelPacket(long plotCoordinate,
                                                    BoundingBox3ic bounds) implements SableTCPPacket {

    public static final SablePacketCodec<ClientboundChangeBoundsSubLevelPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.write(buf), ClientboundChangeBoundsSubLevelPacket::read);

    private static ClientboundChangeBoundsSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ClientboundChangeBoundsSubLevelPacket(buf.readLong(), SableBufferUtils.read(buf, new BoundingBox3i()));
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeLong(this.plotCoordinate);
        SableBufferUtils.write(buf, this.bounds);
    }

}
