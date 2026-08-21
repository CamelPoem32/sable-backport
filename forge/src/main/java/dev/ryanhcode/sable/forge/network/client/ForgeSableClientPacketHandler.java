package dev.ryanhcode.sable.forge.network.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;
import dev.ryanhcode.sable.network.tcp.SablePacketDirection;
import dev.ryanhcode.sable.network.tcp.SablePacketRegistration;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ForgeSableClientPacketHandler {

    private ForgeSableClientPacketHandler() {
    }

    public static <T extends SableTCPPacket> void handle(
            final SablePacketRegistration<T> registration,
            final T packet) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        final LocalPlayer player = minecraft.player;

        if (level == null || player == null) {
            Sable.LOGGER.error("Rejected clientbound Sable packet {} without an active client level", registration.packetType().getSimpleName());
            return;
        }

        registration.handler().accept(
                packet,
                SablePacketContext.of(level, player, SablePacketDirection.CLIENTBOUND)
        );
    }
}
