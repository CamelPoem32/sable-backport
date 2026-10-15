package dev.ryanhcode.sable.command;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.UUID;

/** Test-only nonce-scoped client observation transport, never part of native actor execution. */
final class M43ResidualFixtureNetwork {
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("sable", "m43_fixture"),
            () -> "1", "1"::equals, "1"::equals);
    private M43ResidualFixtureNetwork() { }

    static void install() {
        CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p, b) -> { b.writeUUID(p.marker); b.writeUUID(p.nonce); b.writeUUID(p.vehicle); b.writeUUID(p.owner); })
                .decoder(b -> new Request(b.readUUID(), b.readUUID(), b.readUUID(), b.readUUID()))
                .consumerMainThread((p, c) -> { M43ResidualFixtureClient.request(p); c.get().setPacketHandled(true); }).add();
        CHANNEL.messageBuilder(Report.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((p, b) -> { b.writeUUID(p.marker); b.writeUUID(p.nonce); b.writeUUID(p.vehicle); b.writeUUID(p.owner);
                    vector(b, p.visible); vector(b, p.camera); vector(b, p.local); b.writeFloat(p.angle); b.writeDouble(p.dot); })
                .decoder(b -> new Report(b.readUUID(), b.readUUID(), b.readUUID(), b.readUUID(), vector(b), vector(b), vector(b), b.readFloat(), b.readDouble()))
                .consumerMainThread((p, c) -> {
                    final ServerPlayer sender = c.get().getSender();
                    if (sender != null) M43ResidualFixtureCommands.clientGaze(sender, p.marker, p.nonce, p.vehicle, p.owner,
                            p.visible, p.camera, p.local, p.angle, p.dot);
                    c.get().setPacketHandled(true);
                }).add();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> M43ResidualFixtureClient::install);
    }
    static void request(final ServerPlayer player, final UUID marker, final UUID nonce, final UUID vehicle, final UUID owner) {
        if (player != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Request(marker, nonce, vehicle, owner));
    }
    private static void vector(final FriendlyByteBuf b, final Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vector(final FriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    record Request(UUID marker, UUID nonce, UUID vehicle, UUID owner) { }
    record Report(UUID marker, UUID nonce, UUID vehicle, UUID owner, Vec3 visible, Vec3 camera, Vec3 local, float angle, double dot) { }
}
