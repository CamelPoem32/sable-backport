package dev.ryanhcode.sable.network.tcp;

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
import dev.ryanhcode.sable.platform.SablePlatformUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;

public final class SableTCPPackets {

    public static final String PROTOCOL_VERSION = SableTCPPacketCatalog.PROTOCOL_VERSION;

    private static final List<SablePacketRegistration<?>> REGISTRATIONS = createRegistrations();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private SableTCPPackets() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        for (final SablePacketRegistration<?> registration : REGISTRATIONS) {
            register(registration);
        }
    }

    public static List<SablePacketRegistration<?>> registrations() {
        return REGISTRATIONS;
    }

    public static void sendToServer(final SableTCPPacket packet) {
        TransportHolder.INSTANCE.sendToServer(packet);
    }

    public static void sendToPlayer(final ServerPlayer player, final SableTCPPacket packet) {
        TransportHolder.INSTANCE.sendToPlayer(player, packet);
    }

    public static SablePacketSink player(final ServerPlayer player) {
        return packet -> sendToPlayer(player, packet);
    }

    public static Packet<ClientGamePacketListener> toClientboundVanillaPacket(final SableTCPPacket packet) {
        return TransportHolder.INSTANCE.toClientboundVanillaPacket(packet);
    }

    private static List<SablePacketRegistration<?>> createRegistrations() {
        final List<SablePacketRegistration<?>> registrations = new ArrayList<>();
        for (final SablePacketDefinition<?> definition : SableTCPPacketCatalog.definitions()) {
            registrations.add(bind(definition));
        }
        return List.copyOf(registrations);
    }

    private static <T extends SableTCPPacket> SablePacketRegistration<T> bind(
            final SablePacketDefinition<T> definition) {
        return new SablePacketRegistration<>(definition.id(), definition.packetType(), definition.direction(),
                definition.codec(), handler(definition.id()));
    }

    @SuppressWarnings("unchecked")
    private static <T extends SableTCPPacket> BiConsumer<T, SablePacketContext> handler(final int id) {
        return (BiConsumer<T, SablePacketContext>) switch (id) {
            case 0 -> (BiConsumer<ClientboundSableSnapshotDualPacket, SablePacketContext>) SableClientPacketHandlers::handleSnapshot;
            case 1 -> (BiConsumer<ClientboundSableSnapshotInfoDualPacket, SablePacketContext>) SableClientPacketHandlers::handleSnapshotInfo;
            case 2 -> (BiConsumer<ClientboundStopMovingSubLevelPacket, SablePacketContext>) SableClientPacketHandlers::handleStopMoving;
            case 3 -> (BiConsumer<ClientboundChangeSubLevelNamePacket, SablePacketContext>) SableClientPacketHandlers::handleChangeName;
            case 4 -> (BiConsumer<ClientboundStartTrackingSubLevelPacket, SablePacketContext>) SableClientPacketHandlers::handleStartTracking;
            case 5 -> (BiConsumer<ClientboundFinalizeSubLevelPacket, SablePacketContext>) SableClientPacketHandlers::handleFinalize;
            case 6 -> (BiConsumer<ClientboundStopTrackingSubLevelPacket, SablePacketContext>) SableClientPacketHandlers::handleStopTracking;
            case 7 -> (BiConsumer<ClientboundChangeBoundsSubLevelPacket, SablePacketContext>) SableClientPacketHandlers::handleChangeBounds;
            case 8 -> (BiConsumer<ClientboundFreezePlayerPacket, SablePacketContext>) SableClientPacketHandlers::handleFreezePlayer;
            case 9 -> (BiConsumer<ClientboundPhysicsPropertyPacket, SablePacketContext>) SableClientPacketHandlers::handlePhysicsProperty;
            case 10 -> (BiConsumer<ClientboundFloatingBlockMaterialPacket, SablePacketContext>) SableClientPacketHandlers::handleFloatingMaterial;
            case 11 -> (BiConsumer<ClientboundRecentlySplitSubLevelPacket, SablePacketContext>) SableClientPacketHandlers::handleRecentlySplit;
            case 12 -> (BiConsumer<ClientboundSableUDPActivationPacket, SablePacketContext>) SableClientPacketHandlers::handleUdpActivation;
            case 13 -> (BiConsumer<ServerboundPunchSubLevelPacket, SablePacketContext>) SableServerPacketHandlers::handlePunch;
            default -> throw new IllegalArgumentException("Unknown Sable TCP packet id: " + id);
        };
    }

    private static <T extends SableTCPPacket> void register(final SablePacketRegistration<T> registration) {
        TransportHolder.INSTANCE.register(registration);
    }

    private static final class TransportHolder {
        private static final SablePacketTransport INSTANCE = SablePlatformUtil.load(SablePacketTransport.class);
    }
}
