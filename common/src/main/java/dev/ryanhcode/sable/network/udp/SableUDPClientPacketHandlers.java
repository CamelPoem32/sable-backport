package dev.ryanhcode.sable.network.udp;

import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.network.packets.PacketReceiveMode;
import dev.ryanhcode.sable.network.packets.udp.SableUDPClientboundKeepAlivePacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPEchoPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPServerboundAlivePacket;
import dev.ryanhcode.sable.network.tcp.SableClientPacketHandlers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.net.InetSocketAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public final class SableUDPClientPacketHandlers {

    private SableUDPClientPacketHandlers() {
    }

    public static void handle(final SableUDPPacket packet, final Level level) {
        if (packet instanceof final ClientboundSableSnapshotDualPacket snapshot) {
            SableClientPacketHandlers.handleSnapshot(snapshot, level, PacketReceiveMode.UDP);
        } else if (packet instanceof final ClientboundSableSnapshotInfoDualPacket info) {
            SableClientPacketHandlers.handleSnapshotInfo(info, level);
        } else if (packet instanceof final SableUDPEchoPacket echo) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("Received UDP Test Ping: " + echo.text()));
        } else if (packet instanceof SableUDPClientboundKeepAlivePacket) {
            handleKeepAlive();
        } else {
            throw new IllegalArgumentException("Unsupported clientbound Sable UDP packet: " + packet.getClass().getName());
        }
    }

    private static void handleKeepAlive() {
        final Connection connection = Minecraft.getInstance().getConnection().getConnection();
        final Channel channel = ((ConnectionExtension) connection).sable$getUDPChannel();
        final InetSocketAddress baseAddress = (InetSocketAddress) connection.getRemoteAddress();
        final InetSocketAddress remoteAddress = new InetSocketAddress(baseAddress.getAddress(), baseAddress.getPort());

        channel.eventLoop().execute(() -> {
            final AddressedSableUDPPacket envelope = new AddressedSableUDPPacket(
                    new SableUDPServerboundAlivePacket(), remoteAddress);
            final ChannelFuture writeFuture = channel.writeAndFlush(envelope);
            writeFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        });
    }
}
