package dev.ryanhcode.sable.network.tcp;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;

public interface SablePacketTransport {

    <T extends SableTCPPacket> void register(SablePacketRegistration<T> registration);

    void sendToServer(SableTCPPacket packet);

    void sendToPlayer(ServerPlayer player, SableTCPPacket packet);

    Packet<ClientGamePacketListener> toClientboundVanillaPacket(SableTCPPacket packet);
}
