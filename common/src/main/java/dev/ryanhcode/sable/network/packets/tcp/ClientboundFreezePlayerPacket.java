package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.util.SableBufferUtils;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

public record ClientboundFreezePlayerPacket(UUID subLevelID, Vector3dc localPosition) implements SableTCPPacket {

    public static final SablePacketCodec<ClientboundFreezePlayerPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.write(buf), ClientboundFreezePlayerPacket::read);

    private static ClientboundFreezePlayerPacket read(final FriendlyByteBuf buf) {
        return new ClientboundFreezePlayerPacket(buf.readUUID(), SableBufferUtils.read(buf, new Vector3d()));
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeUUID(this.subLevelID);
        SableBufferUtils.write(buf, this.localPosition);
    }

}
