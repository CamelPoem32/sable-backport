package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.util.SableBufferUtils;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record ClientboundRecentlySplitSubLevelPacket(UUID splitSubLevelID, UUID splitFromID, Pose3d pose) implements SableTCPPacket {
    public static final SablePacketCodec<ClientboundRecentlySplitSubLevelPacket> CODEC =
            SablePacketCodec.of(ClientboundRecentlySplitSubLevelPacket::write, ClientboundRecentlySplitSubLevelPacket::read);

    private static void write(final FriendlyByteBuf buffer, final ClientboundRecentlySplitSubLevelPacket packet) {
        buffer.writeUUID(packet.splitSubLevelID);
        buffer.writeUUID(packet.splitFromID);
        SableBufferUtils.write(buffer, packet.pose);
    }

    private static ClientboundRecentlySplitSubLevelPacket read(final FriendlyByteBuf buffer) {
        return new ClientboundRecentlySplitSubLevelPacket(
                buffer.readUUID(),
                buffer.readUUID(),
                SableBufferUtils.read(buffer, new Pose3d())
        );
    }

}
