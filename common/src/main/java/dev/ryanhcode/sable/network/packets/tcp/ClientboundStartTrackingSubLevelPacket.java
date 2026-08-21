package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.util.SableBufferUtils;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ClientboundStartTrackingSubLevelPacket(long plotCoordinate, UUID subLevelID, Pose3dc lastPose, Pose3d pose,
                                                     BoundingBox3ic bounds, @Nullable String name, int gameTick) implements SableTCPPacket {

    public static final SablePacketCodec<ClientboundStartTrackingSubLevelPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.write(buf), ClientboundStartTrackingSubLevelPacket::read);

    private void write(final FriendlyByteBuf buf) {
        buf.writeLong(this.plotCoordinate);
        buf.writeUUID(this.subLevelID);

        SableBufferUtils.write(buf, this.lastPose);
        SableBufferUtils.write(buf, this.pose);
        SableBufferUtils.write(buf, this.bounds);

        buf.writeBoolean(this.name != null);
        if (this.name != null) {
            buf.writeUtf(this.name);
        }

        buf.writeInt(this.gameTick);
    }

    private static ClientboundStartTrackingSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ClientboundStartTrackingSubLevelPacket(buf.readLong(), buf.readUUID(), SableBufferUtils.read(buf, new Pose3d()), SableBufferUtils.read(buf, new Pose3d()), SableBufferUtils.read(buf, new BoundingBox3i()), buf.readBoolean() ? buf.readUtf() : null, buf.readInt());
    }

}
