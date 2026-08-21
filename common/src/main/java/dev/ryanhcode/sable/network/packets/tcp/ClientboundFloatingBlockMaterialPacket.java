package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
import net.minecraft.resources.ResourceLocation;

public record ClientboundFloatingBlockMaterialPacket(ResourceLocation name, FloatingBlockMaterial material) implements SableTCPPacket {
    private static final SablePacketCodec<FloatingBlockMaterial> MATERIAL_CODEC =
            SablePacketCodec.fromCodec(FloatingBlockMaterial.CODEC);
    public static final SablePacketCodec<ClientboundFloatingBlockMaterialPacket> CODEC = SablePacketCodec.of(
            (buffer, packet) -> {
                buffer.writeResourceLocation(packet.name);
                MATERIAL_CODEC.encode(buffer, packet.material);
            },
            buffer -> new ClientboundFloatingBlockMaterialPacket(
                    buffer.readResourceLocation(),
                    MATERIAL_CODEC.decode(buffer)
            )
    );
}
