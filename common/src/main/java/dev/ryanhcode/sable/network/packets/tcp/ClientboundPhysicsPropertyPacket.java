package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinition;

public record ClientboundPhysicsPropertyPacket(PhysicsBlockPropertiesDefinition definition) implements SableTCPPacket {
    private static final SablePacketCodec<PhysicsBlockPropertiesDefinition> DEFINITION_CODEC =
            SablePacketCodec.fromCodec(PhysicsBlockPropertiesDefinition.CODEC);
    public static final SablePacketCodec<ClientboundPhysicsPropertyPacket> CODEC = SablePacketCodec.of(
            (buffer, packet) -> DEFINITION_CODEC.encode(buffer, packet.definition),
            buffer -> new ClientboundPhysicsPropertyPacket(DEFINITION_CODEC.decode(buffer))
    );
}
