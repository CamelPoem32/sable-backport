package dev.ryanhcode.sable.forge.network;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.forge.SableForgeRuntimeSmoke;
import dev.ryanhcode.sable.forge.network.client.ForgeSableClientPacketHandler;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;
import dev.ryanhcode.sable.network.tcp.SablePacketDirection;
import dev.ryanhcode.sable.network.tcp.SablePacketRegistration;
import dev.ryanhcode.sable.network.tcp.SablePacketTransport;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import java.util.function.Supplier;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ForgeSablePacketTransport implements SablePacketTransport {

    private final SimpleChannel channel = NetworkRegistry.newSimpleChannel(
            Sable.sablePath("main"),
            () -> SableTCPPackets.PROTOCOL_VERSION,
            SableTCPPackets.PROTOCOL_VERSION::equals,
            SableTCPPackets.PROTOCOL_VERSION::equals
    );

    @Override
    public <T extends SableTCPPacket> void register(final SablePacketRegistration<T> registration) {
        this.channel.messageBuilder(
                        registration.packetType(),
                        registration.id(),
                        toForgeDirection(registration.direction())
                )
                .encoder((packet, buffer) -> registration.codec().encode(buffer, packet))
                .decoder(registration.codec()::decode)
                .consumerNetworkThread((java.util.function.BiConsumer<T, java.util.function.Supplier<NetworkEvent.Context>>)
                        (packet, contextSupplier) -> this.receive(registration, packet, contextSupplier))
                .add();
    }

    @Override
    public void sendToServer(final SableTCPPacket packet) {
        this.channel.sendToServer(packet);
    }

    @Override
    public void sendToPlayer(final ServerPlayer player, final SableTCPPacket packet) {
        this.channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Packet<ClientGamePacketListener> toClientboundVanillaPacket(final SableTCPPacket packet) {
        return (Packet<ClientGamePacketListener>) this.channel.toVanillaPacket(packet, NetworkDirection.PLAY_TO_CLIENT);
    }

    private <T extends SableTCPPacket> void receive(
            final SablePacketRegistration<T> registration,
            final T packet,
            final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context forgeContext = contextSupplier.get();
        final NetworkDirection expectedForgeDirection = toForgeDirection(registration.direction());
        final boolean directionMatches = forgeContext.getDirection() == expectedForgeDirection;
        if (!directionMatches) {
            Sable.LOGGER.error(
                    "Rejected Sable packet {} on {}, expected {}",
                    registration.packetType().getSimpleName(),
                    forgeContext.getDirection(),
                    expectedForgeDirection
            );
        }
        final SablePacketDirection actualDirection = directionMatches
                ? registration.direction()
                : opposite(registration.direction());

        ForgeSablePacketDispatcher.dispatch(
                registration.direction(),
                actualDirection,
                forgeContext::enqueueWork,
                () -> {
                    if (registration.direction() == SablePacketDirection.CLIENTBOUND) {
                        return () -> DistExecutor.unsafeRunWhenOn(
                                Dist.CLIENT,
                                () -> () -> ForgeSableClientPacketHandler.handle(registration, packet)
                        );
                    }

                    final ServerPlayer sender = forgeContext.getSender();
                    if (sender == null) {
                        Sable.LOGGER.error(
                                "Rejected serverbound Sable packet {} without a sender",
                                registration.packetType().getSimpleName()
                        );
                        return null;
                    }

                    return () -> {
                        SableForgeRuntimeSmoke.packetThread(
                                "server",
                                registration,
                                sender.getServer() != null && sender.getServer().isSameThread()
                        );
                        registration.handler().accept(
                                packet,
                                SablePacketContext.of(sender.level(), sender, SablePacketDirection.SERVERBOUND)
                        );
                    };
                },
                () -> forgeContext.setPacketHandled(true));
    }

    private static NetworkDirection toForgeDirection(final SablePacketDirection direction) {
        return direction == SablePacketDirection.CLIENTBOUND
                ? NetworkDirection.PLAY_TO_CLIENT
                : NetworkDirection.PLAY_TO_SERVER;
    }

    private static SablePacketDirection opposite(final SablePacketDirection direction) {
        return direction == SablePacketDirection.CLIENTBOUND
                ? SablePacketDirection.SERVERBOUND
                : SablePacketDirection.CLIENTBOUND;
    }
}
