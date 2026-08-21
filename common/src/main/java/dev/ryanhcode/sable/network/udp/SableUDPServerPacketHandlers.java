package dev.ryanhcode.sable.network.udp;

import dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPServerboundAlivePacket;
import java.net.InetSocketAddress;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public final class SableUDPServerPacketHandlers {

    private SableUDPServerPacketHandlers() {
    }

    public static void handle(final SableUDPPacket packet,
                              final MinecraftServer server,
                              final InetSocketAddress sender) {
        final SableUDPServer udpServer = SableUDPServer.getServer(server);
        if (udpServer == null) {
            return;
        }
        if (packet instanceof final SableUDPAuthenticationPacket authentication) {
            udpServer.receiveAuthenticationPacket(UUID.fromString(authentication.token()), sender);
        } else if (packet instanceof SableUDPServerboundAlivePacket) {
            udpServer.receiveAlivePacket(sender);
        } else {
            throw new IllegalArgumentException("Unsupported serverbound Sable UDP packet: " + packet.getClass().getName());
        }
    }
}
