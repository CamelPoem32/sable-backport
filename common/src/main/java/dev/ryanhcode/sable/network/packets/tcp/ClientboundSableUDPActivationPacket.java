package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record ClientboundSableUDPActivationPacket(UUID uuid) implements SableTCPPacket {

    public static final SablePacketCodec<ClientboundSableUDPActivationPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.write(buf), ClientboundSableUDPActivationPacket::read);

    private void write(final FriendlyByteBuf buf) {
        buf.writeUUID(this.uuid);
    }

    private static ClientboundSableUDPActivationPacket read(final FriendlyByteBuf buf) {
        return new ClientboundSableUDPActivationPacket(buf.readUUID());
    }

}
