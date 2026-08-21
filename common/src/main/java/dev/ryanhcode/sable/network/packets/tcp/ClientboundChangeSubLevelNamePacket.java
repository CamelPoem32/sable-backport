package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ClientboundChangeSubLevelNamePacket(UUID subLevelID, @Nullable String name) implements SableTCPPacket {
    public static final SablePacketCodec<ClientboundChangeSubLevelNamePacket> CODEC =
            SablePacketCodec.of(ClientboundChangeSubLevelNamePacket::write, ClientboundChangeSubLevelNamePacket::read);

    private static void write(final FriendlyByteBuf buffer, final ClientboundChangeSubLevelNamePacket packet) {
        buffer.writeUUID(packet.subLevelID);
        buffer.writeBoolean(packet.name != null);
        if (packet.name != null) {
            buffer.writeUtf(packet.name);
        }
    }

    private static ClientboundChangeSubLevelNamePacket read(final FriendlyByteBuf buffer) {
        return new ClientboundChangeSubLevelNamePacket(
                buffer.readUUID(),
                buffer.readBoolean() ? buffer.readUtf() : null
        );
    }

}
