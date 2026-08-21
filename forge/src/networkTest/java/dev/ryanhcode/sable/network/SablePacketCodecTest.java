package dev.ryanhcode.sable.network;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
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
import dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPClientboundKeepAlivePacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPEchoPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPServerboundAlivePacket;
import dev.ryanhcode.sable.network.tcp.SablePacketDefinition;
import dev.ryanhcode.sable.network.tcp.SablePacketDirection;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.network.tcp.SableTCPPacketCatalog;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinition;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SablePacketCodecTest {

    private static final UUID FIRST_UUID = UUID.fromString("12345678-1234-5678-9abc-def012345678");
    private static final UUID SECOND_UUID = UUID.fromString("87654321-4321-8765-cba9-876543210fed");

    @Test
    void tcpCatalogHasStableIdsDirectionsAndRoundTrips() {
        final List<SablePacketDefinition<?>> definitions = SableTCPPacketCatalog.definitions();
        final List<SableTCPPacket> packets = tcpPackets();

        assertEquals("1", SableTCPPacketCatalog.PROTOCOL_VERSION);
        assertEquals(14, definitions.size());
        assertEquals(14, packets.size());
        final Set<Integer> ids = new HashSet<>();

        for (int i = 0; i < definitions.size(); i++) {
            final SablePacketDefinition<?> definition = definitions.get(i);
            final SableTCPPacket packet = packets.get(i);
            assertEquals(i, definition.id());
            assertTrue(ids.add(definition.id()));
            assertEquals(i == 13 ? SablePacketDirection.SERVERBOUND : SablePacketDirection.CLIENTBOUND,
                    definition.direction());
            assertTrue(definition.packetType().isInstance(packet));
            assertRoundTripUnchecked(definition, packet);
        }

        assertFalse(definitions.stream().anyMatch(definition -> definition.packetType().getSimpleName().contains("Gizmo")));
    }

    @Test
    void udpCatalogHasStableOrdinalsDirectionsAndRoundTrips() {
        final List<SableUDPPacket> packets = List.of(
                new SableUDPEchoPacket("echo"),
                snapshot(),
                new ClientboundSableSnapshotInfoDualPacket(21, 22, true),
                new SableUDPAuthenticationPacket(FIRST_UUID.toString()),
                new SableUDPClientboundKeepAlivePacket(),
                new SableUDPServerboundAlivePacket()
        );

        assertEquals(6, SableUDPPacketType.VALUES.length);
        for (int i = 0; i < packets.size(); i++) {
            final SableUDPPacket packet = packets.get(i);
            final SableUDPPacketType type = SableUDPPacketType.VALUES[i];
            assertEquals(i, type.ordinal());
            assertEquals(i == 3 || i == 5
                    ? net.minecraft.network.protocol.PacketFlow.SERVERBOUND
                    : net.minecraft.network.protocol.PacketFlow.CLIENTBOUND, type.flow());
            assertEquals(type, packet.getType());

            final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            type.write(buffer, packet);
            final byte[] expected = ByteBufUtil.getBytes(buffer);
            final SableUDPPacket decoded = type.create(buffer);
            assertEquals(0, buffer.readableBytes());
            final FriendlyByteBuf encodedAgain = new FriendlyByteBuf(Unpooled.buffer());
            type.write(encodedAgain, decoded);
            assertArrayEquals(expected, ByteBufUtil.getBytes(encodedAgain));
            buffer.release();
            encodedAgain.release();
        }
    }

    @Test
    void snapshotUsesDocumentedGoldenOrder() {
        final ClientboundSableSnapshotDualPacket packet = snapshot();
        final FriendlyByteBuf expected = new FriendlyByteBuf(Unpooled.buffer());
        expected.writeInt(42);
        expected.writeVarInt(1);
        expected.writeLong(0x1020304050607080L);
        writePose(expected, pose(1.0));
        expected.writeFloat(1.25F).writeFloat(2.5F).writeFloat(3.75F);
        expected.writeFloat(-1.25F).writeFloat(-2.5F).writeFloat(-3.75F);
        assertArrayEquals(ByteBufUtil.getBytes(expected), encode(ClientboundSableSnapshotDualPacket.CODEC, packet));
        expected.release();
    }

    @Test
    void startTrackingUsesDocumentedGoldenOrderAndNullableNameFlag() {
        final Pose3d lastPose = pose(1.0);
        final Pose3d currentPose = pose(10.0);
        final BoundingBox3i bounds = new BoundingBox3i(-1, -2, -3, 4, 5, 6);
        final ClientboundStartTrackingSubLevelPacket packet = new ClientboundStartTrackingSubLevelPacket(
                99L, FIRST_UUID, lastPose, currentPose, bounds, "airship", 1234);
        final FriendlyByteBuf expected = new FriendlyByteBuf(Unpooled.buffer());
        expected.writeLong(99L);
        expected.writeUUID(FIRST_UUID);
        writePose(expected, lastPose);
        writePose(expected, currentPose);
        writeBounds(expected, bounds);
        expected.writeBoolean(true);
        expected.writeUtf("airship");
        expected.writeInt(1234);
        assertArrayEquals(ByteBufUtil.getBytes(expected), encode(ClientboundStartTrackingSubLevelPacket.CODEC, packet));
        expected.release();

        final ClientboundStartTrackingSubLevelPacket unnamed = new ClientboundStartTrackingSubLevelPacket(
                99L, FIRST_UUID, lastPose, currentPose, bounds, null, 1234);
        final ClientboundStartTrackingSubLevelPacket decoded = roundTrip(
                ClientboundStartTrackingSubLevelPacket.CODEC, unnamed);
        assertEquals(null, decoded.name());
    }

    @Test
    void punchAndNamesUseDocumentedGoldenOrder() {
        final ServerboundPunchSubLevelPacket punch = new ServerboundPunchSubLevelPacket(
                new BlockPos(3, 70, -8), new Vector3d(1.0, 2.0, 3.0), new Vector3d(-4.0, -5.0, -6.0));
        final FriendlyByteBuf expectedPunch = new FriendlyByteBuf(Unpooled.buffer());
        expectedPunch.writeBlockPos(new BlockPos(3, 70, -8));
        writeVector(expectedPunch, new Vector3d(1.0, 2.0, 3.0));
        writeVector(expectedPunch, new Vector3d(-4.0, -5.0, -6.0));
        assertArrayEquals(ByteBufUtil.getBytes(expectedPunch), encode(ServerboundPunchSubLevelPacket.CODEC, punch));
        expectedPunch.release();

        final ClientboundChangeSubLevelNamePacket named = new ClientboundChangeSubLevelNamePacket(FIRST_UUID, "name");
        final FriendlyByteBuf expectedName = new FriendlyByteBuf(Unpooled.buffer());
        expectedName.writeUUID(FIRST_UUID);
        expectedName.writeBoolean(true);
        expectedName.writeUtf("name");
        assertArrayEquals(ByteBufUtil.getBytes(expectedName), encode(ClientboundChangeSubLevelNamePacket.CODEC, named));
        expectedName.release();
        assertEquals(null, roundTrip(ClientboundChangeSubLevelNamePacket.CODEC,
                new ClientboundChangeSubLevelNamePacket(FIRST_UUID, null)).name());
    }

    @Test
    void dfuBackedPayloadsUseNbtOps() {
        final PhysicsBlockPropertiesDefinition definition = physicsDefinition();
        final ClientboundPhysicsPropertyPacket propertyPacket = new ClientboundPhysicsPropertyPacket(definition);
        final FriendlyByteBuf expectedProperty = new FriendlyByteBuf(Unpooled.buffer());
        expectedProperty.writeWithCodec(NbtOps.INSTANCE, PhysicsBlockPropertiesDefinition.CODEC, definition);
        assertArrayEquals(ByteBufUtil.getBytes(expectedProperty),
                encode(ClientboundPhysicsPropertyPacket.CODEC, propertyPacket));
        expectedProperty.release();
        roundTrip(ClientboundPhysicsPropertyPacket.CODEC, propertyPacket);

        final FloatingBlockMaterial material = floatingMaterial();
        final ResourceLocation name = new ResourceLocation("sable", "helium");
        final ClientboundFloatingBlockMaterialPacket materialPacket =
                new ClientboundFloatingBlockMaterialPacket(name, material);
        final FriendlyByteBuf expectedMaterial = new FriendlyByteBuf(Unpooled.buffer());
        expectedMaterial.writeResourceLocation(name);
        expectedMaterial.writeWithCodec(NbtOps.INSTANCE, FloatingBlockMaterial.CODEC, material);
        assertArrayEquals(ByteBufUtil.getBytes(expectedMaterial),
                encode(ClientboundFloatingBlockMaterialPacket.CODEC, materialPacket));
        expectedMaterial.release();
        roundTrip(ClientboundFloatingBlockMaterialPacket.CODEC, materialPacket);
    }

    private static List<SableTCPPacket> tcpPackets() {
        final Pose3d firstPose = pose(1.0);
        final Pose3d secondPose = pose(10.0);
        final BoundingBox3i bounds = new BoundingBox3i(-1, -2, -3, 4, 5, 6);
        return List.of(
                snapshot(),
                new ClientboundSableSnapshotInfoDualPacket(21, 22, true),
                new ClientboundStopMovingSubLevelPacket(23L),
                new ClientboundChangeSubLevelNamePacket(FIRST_UUID, "vessel"),
                new ClientboundStartTrackingSubLevelPacket(24L, FIRST_UUID, firstPose, secondPose, bounds, "vessel", 25),
                new ClientboundFinalizeSubLevelPacket(26L),
                new ClientboundStopTrackingSubLevelPacket(27L),
                new ClientboundChangeBoundsSubLevelPacket(28L, bounds),
                new ClientboundFreezePlayerPacket(FIRST_UUID, new Vector3d(1.0, 2.0, 3.0)),
                new ClientboundPhysicsPropertyPacket(physicsDefinition()),
                new ClientboundFloatingBlockMaterialPacket(new ResourceLocation("sable", "helium"), floatingMaterial()),
                new ClientboundRecentlySplitSubLevelPacket(FIRST_UUID, SECOND_UUID, firstPose),
                new ClientboundSableUDPActivationPacket(FIRST_UUID),
                new ServerboundPunchSubLevelPacket(new BlockPos(1, 2, 3),
                        new Vector3d(4.0, 5.0, 6.0), new Vector3d(7.0, 8.0, 9.0))
        );
    }

    private static ClientboundSableSnapshotDualPacket snapshot() {
        return new ClientboundSableSnapshotDualPacket(42, List.of(
                new ClientboundSableSnapshotDualPacket.Entry(
                        0x1020304050607080L,
                        pose(1.0),
                        new Vector3f(1.25F, 2.5F, 3.75F),
                        new Vector3f(-1.25F, -2.5F, -3.75F))
        ));
    }

    private static Pose3d pose(final double offset) {
        return new Pose3d(
                new Vector3d(offset, offset + 1.0, offset + 2.0),
                new Quaterniond(offset / 10.0, offset / 20.0, offset / 30.0, offset / 40.0),
                new Vector3d(offset + 3.0, offset + 4.0, offset + 5.0),
                new Vector3d(1.0));
    }

    private static PhysicsBlockPropertiesDefinition physicsDefinition() {
        final var result = PhysicsBlockPropertiesDefinition.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"selector\":\"minecraft:stone\",\"priority\":5,\"properties\":{}}")
        );
        return result.result().orElseThrow(() -> new AssertionError(result.error().orElseThrow().message()));
    }

    private static FloatingBlockMaterial floatingMaterial() {
        return new FloatingBlockMaterial(true, false, true, 1.25, 0.5, 0.1, 0.2, 0.3, 0.4);
    }

    private static void writePose(final FriendlyByteBuf buffer, final Pose3d pose) {
        writeVector(buffer, pose.position());
        buffer.writeFloat((float) pose.orientation().x());
        buffer.writeFloat((float) pose.orientation().y());
        buffer.writeFloat((float) pose.orientation().z());
        buffer.writeFloat((float) pose.orientation().w());
        writeVector(buffer, pose.rotationPoint());
    }

    private static void writeVector(final FriendlyByteBuf buffer, final org.joml.Vector3dc vector) {
        buffer.writeDouble(vector.x()).writeDouble(vector.y()).writeDouble(vector.z());
    }

    private static void writeBounds(final FriendlyByteBuf buffer, final BoundingBox3i bounds) {
        buffer.writeInt(bounds.minX()).writeInt(bounds.minY()).writeInt(bounds.minZ());
        buffer.writeInt(bounds.maxX()).writeInt(bounds.maxY()).writeInt(bounds.maxZ());
    }

    private static <T> T roundTrip(final SablePacketCodec<T> codec, final T packet) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(encode(codec, packet)));
        final T decoded = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        assertArrayEquals(encode(codec, packet), encode(codec, decoded));
        buffer.release();
        return decoded;
    }

    private static <T> byte[] encode(final SablePacketCodec<T> codec, final T packet) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buffer, packet);
        final byte[] bytes = ByteBufUtil.getBytes(buffer);
        buffer.release();
        return bytes;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertRoundTripUnchecked(final SablePacketDefinition definition,
                                                 final SableTCPPacket packet) {
        roundTrip(definition.codec(), packet);
    }
}
