package dev.ryanhcode.sable.network.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundChangeBoundsSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundChangeSubLevelNamePacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFinalizeSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFloatingBlockMaterialPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundPhysicsPropertyPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundRecentlySplitSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundSableUDPActivationPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopMovingSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopTrackingSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundPunchSubLevelPacket;
import java.util.List;

public final class SableTCPPacketCatalog {

    public static final String PROTOCOL_VERSION = "1";

    private static final List<SablePacketDefinition<?>> DEFINITIONS = List.of(
            clientbound(0, ClientboundSableSnapshotDualPacket.class, ClientboundSableSnapshotDualPacket.CODEC),
            clientbound(1, ClientboundSableSnapshotInfoDualPacket.class, ClientboundSableSnapshotInfoDualPacket.CODEC),
            clientbound(2, ClientboundStopMovingSubLevelPacket.class, ClientboundStopMovingSubLevelPacket.CODEC),
            clientbound(3, ClientboundChangeSubLevelNamePacket.class, ClientboundChangeSubLevelNamePacket.CODEC),
            clientbound(4, ClientboundStartTrackingSubLevelPacket.class, ClientboundStartTrackingSubLevelPacket.CODEC),
            clientbound(5, ClientboundFinalizeSubLevelPacket.class, ClientboundFinalizeSubLevelPacket.CODEC),
            clientbound(6, ClientboundStopTrackingSubLevelPacket.class, ClientboundStopTrackingSubLevelPacket.CODEC),
            clientbound(7, ClientboundChangeBoundsSubLevelPacket.class, ClientboundChangeBoundsSubLevelPacket.CODEC),
            clientbound(8, ClientboundFreezePlayerPacket.class, ClientboundFreezePlayerPacket.CODEC),
            clientbound(9, ClientboundPhysicsPropertyPacket.class, ClientboundPhysicsPropertyPacket.CODEC),
            clientbound(10, ClientboundFloatingBlockMaterialPacket.class, ClientboundFloatingBlockMaterialPacket.CODEC),
            clientbound(11, ClientboundRecentlySplitSubLevelPacket.class, ClientboundRecentlySplitSubLevelPacket.CODEC),
            clientbound(12, ClientboundSableUDPActivationPacket.class, ClientboundSableUDPActivationPacket.CODEC),
            serverbound(13, ServerboundPunchSubLevelPacket.class, ServerboundPunchSubLevelPacket.CODEC)
    );

    private SableTCPPacketCatalog() {
    }

    public static List<SablePacketDefinition<?>> definitions() {
        return DEFINITIONS;
    }

    private static <T extends SableTCPPacket> SablePacketDefinition<T> clientbound(
            final int id, final Class<T> packetType, final SablePacketCodec<T> codec) {
        return new SablePacketDefinition<>(id, packetType, SablePacketDirection.CLIENTBOUND, codec);
    }

    private static <T extends SableTCPPacket> SablePacketDefinition<T> serverbound(
            final int id, final Class<T> packetType, final SablePacketCodec<T> codec) {
        return new SablePacketDefinition<>(id, packetType, SablePacketDirection.SERVERBOUND, codec);
    }
}
