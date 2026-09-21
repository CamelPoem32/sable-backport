package dev.ryanhcode.sable.network.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.network.packets.PacketReceiveMode;
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
import dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket;
import dev.ryanhcode.sable.network.udp.AddressedSableUDPPacket;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.net.InetSocketAddress;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class SableClientPacketHandlers {

    private SableClientPacketHandlers() {
    }

    public static void handleSnapshot(final ClientboundSableSnapshotDualPacket packet,
                                      final SablePacketContext context) {
        handleSnapshot(packet, context.level(), PacketReceiveMode.TCP);
    }

    public static void handleSnapshot(final ClientboundSableSnapshotDualPacket packet,
                                      final Level level,
                                      final PacketReceiveMode receiveMode) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            Sable.LOGGER.error("Received a sub-level movement packet for a level without a sub-level container");
            return;
        }

        for (final ClientboundSableSnapshotDualPacket.Entry entry : packet.entries()) {
            final SubLevel subLevel = container.getSubLevel(ChunkPos.getX(entry.plotCoordinate()), ChunkPos.getZ(entry.plotCoordinate()));
            if (!(subLevel instanceof final ClientSubLevel clientSubLevel)) {
                Sable.LOGGER.error("Received a sub-level movement packet for a non-existent sub-level");
                continue;
            }
            ((ClientSubLevelContainer) container).getInterpolation()
                    .receiveSnapshot(clientSubLevel, packet.interpolationTick(), entry.pose(), receiveMode);
        }
    }

    public static void handleSnapshotInfo(final ClientboundSableSnapshotInfoDualPacket packet,
                                          final SablePacketContext context) {
        handleSnapshotInfo(packet, context.level());
    }

    public static void handleSnapshotInfo(final ClientboundSableSnapshotInfoDualPacket packet, final Level level) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            Sable.LOGGER.error("Received a sub-level movement packet for a level without a sub-level container");
            return;
        }
        ((ClientSubLevelContainer) container).getInterpolation()
                .receiveInfo(packet.msSinceLast(), packet.gameTick(), packet.stopped());
    }

    public static void handleStopMoving(final ClientboundStopMovingSubLevelPacket packet,
                                        final SablePacketContext context) {
        final SubLevelContainer container = SubLevelContainer.getContainer(context.level());
        if (container == null) {
            Sable.LOGGER.error("Received a sub-level movement packet for a level without a sub-level container");
            return;
        }
        final SubLevel subLevel = container.getSubLevel(ChunkPos.getX(packet.plotCoordinate()), ChunkPos.getZ(packet.plotCoordinate()));
        if (!(subLevel instanceof final ClientSubLevel clientSubLevel)) {
            Sable.LOGGER.error("Client sub-level is not a client sub-level. How?");
            return;
        }
        clientSubLevel.receiveServerMovementStop();
    }

    public static void handleChangeName(final ClientboundChangeSubLevelNamePacket packet,
                                        final SablePacketContext context) {
        final SubLevelContainer container = SubLevelContainer.getContainer(context.level());
        if (container == null) {
            return;
        }
        final SubLevel subLevel = container.getSubLevel(packet.subLevelID());
        if (subLevel != null) {
            subLevel.setName(packet.name());
        } else {
            Sable.LOGGER.error("Attempted to set name for a client sub-level that does not exist!");
        }
    }

    public static void handleStartTracking(final ClientboundStartTrackingSubLevelPacket packet,
                                           final SablePacketContext context) {
        final SubLevelContainer container = SubLevelContainer.getContainer(context.level());
        if (!(container instanceof final ClientSubLevelContainer clientContainer)) {
            Sable.LOGGER.error("Received a sub-level tracking packet for a level without a sub-level container");
            return;
        }

        Sable.LOGGER.info("SABLE_CLIENT phase=sublevel_create_received id={} plot={} name={} bounds={}",
                packet.subLevelID(), packet.plotCoordinate(), packet.name(), packet.bounds());
        final ClientSubLevel subLevel = (ClientSubLevel) clientContainer.allocateSubLevel(
                packet.subLevelID(), ChunkPos.getX(packet.plotCoordinate()), ChunkPos.getZ(packet.plotCoordinate()),
                new dev.ryanhcode.sable.companion.math.Pose3d(packet.lastPose()));
        Sable.LOGGER.info("SABLE_CLIENT phase=sublevel_registered id={} plot={} name={}",
                subLevel.getUniqueId(), packet.plotCoordinate(), packet.name());
        final SubLevelSnapshotInterpolator interpolator = subLevel.getInterpolator();
        interpolator.receiveSnapshot(packet.gameTick() - 1, packet.lastPose());
        interpolator.receiveSnapshot(packet.gameTick(), packet.pose());

        final ClientSableInterpolationState interpolationState = clientContainer.getInterpolation();
        if (!interpolationState.isStopped()) {
            subLevel.setInitialPosesFrom(interpolationState);
        }
        interpolator.setFirstPoses(packet.pose(), packet.lastPose());
        subLevel.getPlot().setBoundingBox(packet.bounds());
        subLevel.forceUpdateBounds();
        subLevel.updateRenderData();
        if (packet.name() != null) {
            subLevel.setName(packet.name());
        }
    }

    public static void handleFinalize(final ClientboundFinalizeSubLevelPacket packet,
                                      final SablePacketContext context) {
        final SubLevelContainer container = SubLevelContainer.getContainer(context.level());
        if (container == null) {
            Sable.LOGGER.error("Received a sub-level finalize packet for a level without a sub-level container");
            return;
        }
        final SubLevel subLevel = container.getSubLevel(ChunkPos.getX(packet.plotCoordinate()), ChunkPos.getZ(packet.plotCoordinate()));
        if (!(subLevel instanceof final ClientSubLevel clientSubLevel)) {
            Sable.LOGGER.error("Received a sub-level finalize packet for an unknown sub-level plot");
            return;
        }
        clientSubLevel.setFinalized();
        Sable.LOGGER.info("SABLE_CLIENT phase=sublevel_finalized id={} plot={} name={}",
                clientSubLevel.getUniqueId(), packet.plotCoordinate(), clientSubLevel.getName());
        clientSubLevel.updateRenderData();
    }

    public static void handleStopTracking(final ClientboundStopTrackingSubLevelPacket packet,
                                          final SablePacketContext context) {
        final SubLevelContainer container = SubLevelContainer.getContainer(context.level());
        if (container == null) {
            Sable.LOGGER.error("Received a sub-level tracking packet for a level without a sub-level container");
            return;
        }
        final int chunkX = ChunkPos.getX(packet.plotCoordinate());
        final int chunkZ = ChunkPos.getZ(packet.plotCoordinate());
        if (container.getSubLevel(chunkX, chunkZ) == null) {
            Sable.LOGGER.error("Received a sub-level tracking removal packet for unknown sub-level: {}, {}", chunkX, chunkZ);
            return;
        }
        container.removeSubLevel(chunkX, chunkZ, SubLevelRemovalReason.REMOVED);
    }

    public static void handleChangeBounds(final ClientboundChangeBoundsSubLevelPacket packet,
                                          final SablePacketContext context) {
        final SubLevelContainer container = SubLevelContainer.getContainer(context.level());
        if (container == null) {
            Sable.LOGGER.error("Received a sub-level tracking packet for a level without a sub-level container");
            return;
        }
        final SubLevel subLevel = container.getSubLevel(ChunkPos.getX(packet.plotCoordinate()), ChunkPos.getZ(packet.plotCoordinate()));
        if (subLevel == null) {
            Sable.LOGGER.error("Cannot change bounds of nonexistent sub-level plot");
            return;
        }
        final LevelPlot plot = subLevel.getPlot();
        final BoundingBox3i previousBounds = new BoundingBox3i(plot.getBoundingBox());
        plot.setBoundingBox(packet.bounds());
        if (!Objects.equals(previousBounds, packet.bounds())) {
            plot.getSubLevel().onPlotBoundsChanged();
        }
    }

    public static void handleFreezePlayer(final ClientboundFreezePlayerPacket packet,
                                          final SablePacketContext context) {
        final Player player = context.player();
        ((PlayerFreezeExtension) player).sable$freezeTo(packet.subLevelID(), packet.localPosition());
    }

    public static void handlePhysicsProperty(final ClientboundPhysicsPropertyPacket packet,
                                             final SablePacketContext context) {
        PhysicsBlockPropertiesDefinitionLoader.applyToBlocks(packet.definition());
    }

    public static void handleFloatingMaterial(final ClientboundFloatingBlockMaterialPacket packet,
                                              final SablePacketContext context) {
        FloatingBlockMaterialDataHandler.addMaterial(packet.name(), packet.material());
    }

    public static void handleRecentlySplit(final ClientboundRecentlySplitSubLevelPacket packet,
                                           final SablePacketContext context) {
        final SubLevelContainer container = SubLevelContainer.getContainer(context.level());
        if (container instanceof final ClientSubLevelContainer clientContainer) {
            final SubLevel subLevel = container.getSubLevel(packet.splitSubLevelID());
            final SubLevel splitFrom = container.getSubLevel(packet.splitFromID());
            if (subLevel != null && splitFrom != null) {
                ((ClientSubLevel) subLevel).wasSplitFrom(clientContainer.getInterpolation(), (ClientSubLevel) splitFrom, packet.pose());
            } else {
                Sable.LOGGER.error("Attempted to handle a recently split sub-level packet for a sub-level (or origin sub-level) that does not exist!");
            }
        }
    }

    public static void handleUdpActivation(final ClientboundSableUDPActivationPacket packet,
                                           final SablePacketContext context) {
        if (!SableClientConfig.ATTEMPT_UDP_NETWORKING.get()) {
            Sable.LOGGER.info("Received UDP authentication request, ignoring due to disabled attempt_udp_networking config");
            return;
        }

        final Connection connection = Minecraft.getInstance().getConnection().getConnection();
        final Channel channel = ((ConnectionExtension) connection).sable$getUDPChannel();
        final InetSocketAddress baseAddress = (InetSocketAddress) connection.getRemoteAddress();
        final InetSocketAddress remoteAddress = new InetSocketAddress(baseAddress.getAddress(), baseAddress.getPort());
        Sable.LOGGER.info("Received UDP authentication request, sending response over UDP to {}", remoteAddress);

        channel.eventLoop().execute(() -> {
            final AddressedSableUDPPacket envelope = new AddressedSableUDPPacket(
                    new SableUDPAuthenticationPacket(packet.uuid().toString()), remoteAddress);
            final ChannelFuture writeFuture = channel.writeAndFlush(envelope);
            writeFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        });
    }
}
