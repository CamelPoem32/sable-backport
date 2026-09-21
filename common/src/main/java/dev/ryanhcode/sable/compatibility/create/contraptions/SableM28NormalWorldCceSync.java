package dev.ryanhcode.sable.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded provenance for normal-world CCEs reconstructed by outer Sable disassembly. */
public final class SableM28NormalWorldCceSync {
    public static final String M29_TRACE_PROPERTY = "sable.m29.traceSailVisualLifecycle";
    private static final String RESTORED_TAG = "SableM28RestoredAfterOuterDisassembly";
    private static final String EXPECTED_BLOCKS_TAG = "SableM28ExpectedCapturedBlocks";
    private static final String BEARING_POS_TAG = "SableM28BearingPos";
    private static final String M29_TRANSFER_ID_TAG = "SableM29ReverseTransferId";
    private static final String M29_SNAPSHOT_INDEX_TAG = "SableM29ReverseSnapshotIndex";
    private static final String M29_EXPECTED_SAILS_TAG = "SableM29ExpectedSymmetricSails";
    private static final String M29_EXPECTED_ANGLE_TAG = "SableM29ExpectedAngle";
    private static final String M29_SOURCE_UUID_TAG = "SableM29SourceEntityUuid";
    private static final Map<UUID, Set<String>> M29_ENTITY_EVENTS = new ConcurrentHashMap<>();

    private SableM28NormalWorldCceSync() {
    }

    public static void markBeforeAdd(final ControlledContraptionEntity entity,
                                     final BlockPos bearingPos,
                                     final int expectedCapturedBlocks) {
        markBeforeAdd(entity, bearingPos, expectedCapturedBlocks, 0L, -1, 0, 0.0F, null);
    }

    public static void markBeforeAdd(final ControlledContraptionEntity entity,
                                     final BlockPos bearingPos,
                                     final int expectedCapturedBlocks,
                                     final long transferId,
                                     final int snapshotIndex,
                                     final int expectedSymmetricSails,
                                     final float expectedAngle,
                                     final @Nullable UUID sourceEntityUuid) {
        final CompoundTag data = entity.getPersistentData();
        data.putBoolean(RESTORED_TAG, true);
        data.putInt(EXPECTED_BLOCKS_TAG, expectedCapturedBlocks);
        data.putLong(BEARING_POS_TAG, bearingPos.asLong());
        if (transferId > 0L && m29Enabled()) {
            data.putLong(M29_TRANSFER_ID_TAG, transferId);
            data.putInt(M29_SNAPSHOT_INDEX_TAG, snapshotIndex);
            data.putInt(M29_EXPECTED_SAILS_TAG, expectedSymmetricSails);
            data.putFloat(M29_EXPECTED_ANGLE_TAG, expectedAngle);
            if (sourceEntityUuid != null) {
                data.putUUID(M29_SOURCE_UUID_TAG, sourceEntityUuid);
            }
        }
        traceM29Entity("SERVER_ENTITY_PRE_ADD", entity,
                "actualPosition=" + entity.position()
                        + " expectedCapturedBlockCount=" + expectedCapturedBlocks
                        + " expectedSymmetricSailCount=" + expectedSymmetricSails);
    }

    public static void serverPostAdd(final ControlledContraptionEntity entity, final boolean accepted) {
        traceM29Entity("SERVER_ENTITY_POST_ADD", entity,
                "addFreshEntityReturn=" + accepted
                        + " authoritativeIdLookup=" + (entity.level() instanceof ServerLevel server
                        && server.getEntity(entity.getId()) == entity)
                        + " authoritativeUuidLookup=" + (entity.level() instanceof ServerLevel server
                        && server.getEntity(entity.getUUID()) == entity));
    }

    public static void startTracking(final ServerPlayer player, final Entity target) {
        if (target instanceof final ControlledContraptionEntity controlled && isTarget(controlled)) {
            traceM29Entity("SERVER_TRACKING_BEGIN", controlled,
                    "player=" + player.getGameProfile().getName()
                            + " trackingChunk=" + controlled.chunkPosition());
        }
    }

    public static void entityJoined(final Entity entity, final Level level) {
        if (entity instanceof final ControlledContraptionEntity controlled && isTarget(controlled)) {
            if (level.isClientSide) {
                traceM29Entity("CLIENT_ENTITY_ADDED", controlled,
                        "levelIdentity=" + System.identityHashCode(level));
            }
        }
    }

    /** Expires restore diagnostics when the tracked client entity completes its normal ownership lifecycle. */
    public static void entityLeft(final Entity entity, final Level level) {
        if (!(entity instanceof final ControlledContraptionEntity controlled) || !isTarget(controlled)) {
            return;
        }
        if (!level.isClientSide) {
            traceM29Entity("SERVER_ENTITY_REMOVED", controlled,
                    "removalReason=" + controlled.getRemovalReason()
                            + " entityAlive=" + controlled.isAlive());
        }
        M29_ENTITY_EVENTS.remove(controlled.getUUID());
    }

    public static void beforeWriteSpawnData(final AbstractContraptionEntity entity) {
        if (isTarget(entity)) {
            traceM29Entity("SERVER_SPAWN_SENT", entity,
                    "spawnPayloadCapturedBlockCount=" + capturedCount(entity));
        }
    }

    public static void writeMarker(final AbstractContraptionEntity entity,
                                   final CompoundTag tag,
                                   final boolean spawnPacket) {
        if (!spawnPacket || !isTarget(entity)) {
            return;
        }
        final CompoundTag data = entity.getPersistentData();
        tag.putBoolean(RESTORED_TAG, true);
        tag.putInt(EXPECTED_BLOCKS_TAG, data.getInt(EXPECTED_BLOCKS_TAG));
        tag.putLong(BEARING_POS_TAG, data.getLong(BEARING_POS_TAG));
        copyM29Marker(data, tag);
    }

    public static void readMarker(final AbstractContraptionEntity entity,
                                  final CompoundTag tag,
                                  final boolean spawnPacket) {
        if (!spawnPacket || !tag.getBoolean(RESTORED_TAG)) {
            return;
        }
        final CompoundTag data = entity.getPersistentData();
        data.putBoolean(RESTORED_TAG, true);
        data.putInt(EXPECTED_BLOCKS_TAG, tag.getInt(EXPECTED_BLOCKS_TAG));
        data.putLong(BEARING_POS_TAG, tag.getLong(BEARING_POS_TAG));
        copyM29Marker(tag, data);
        traceM29Entity("CLIENT_SPAWN_RECEIVED", entity,
                "clientLookupById=" + (entity.level().getEntity(entity.getId()) == entity));
        traceM29Entity("CLIENT_PAYLOAD_DECODED", entity,
                "clientContraptionNull=" + (entity.getContraption() == null)
                        + " expectedCapturedBlockCount=" + expectedCapturedBlocks(entity)
                        + " clientCapturedBlockCount=" + capturedCount(entity)
                        + " clientSymmetricSailCount=" + symmetricSailCount(entity));
    }

    public static void traceM29Entity(final String event, final AbstractContraptionEntity entity,
                                      final String details) {
        traceM29Entity(event, event, entity, details);
    }

    public static void traceM29Entity(final String event, final String dedupeKey,
                                      final AbstractContraptionEntity entity,
                                      final String details) {
        if (!m29Enabled() || reverseTransferId(entity) <= 0L) {
            return;
        }
        final long transferId = reverseTransferId(entity);
        final int snapshotIndex = reverseSnapshotIndex(entity);
        final String side = entity.level().isClientSide ? "CLIENT" : "SERVER";
        if (!M29_ENTITY_EVENTS.computeIfAbsent(entity.getUUID(), ignored -> ConcurrentHashMap.newKeySet())
                .add(dedupeKey + ':' + side)) {
            return;
        }
        final BlockPos controller = entity instanceof final ControlledContraptionEntity controlled
                ? SableCreateContraptionContext.getControllerPos(controlled) : null;
        Sable.LOGGER.info("SABLE_M29_SAIL_VISUAL event={} transferId={} snapshotIndex={} side={} "
                        + "entityId={} entityUuid={} sourceEntityUuid={} levelClass={} entityPos={} controllerPos={} "
                        + "bearingPos={} capturedBlockCount={} symmetricSailCount={} expectedCapturedBlockCount={} "
                        + "expectedSymmetricSailCount={} expectedAngle={} entityAlive={} entityRemoved={} details={}",
                event, transferId, snapshotIndex, side, entity.getId(), entity.getUUID(),
                sourceEntityUuid(entity), entity.level().getClass().getName(), entity.position(), controller,
                bearingPos(entity), capturedCount(entity), symmetricSailCount(entity),
                expectedCapturedBlocks(entity), expectedSymmetricSails(entity), expectedAngle(entity),
                entity.isAlive(), entity.isRemoved(), details);
    }

    public static void traceM29ReverseEvent(final String event, final long transferId,
                                            final int snapshotIndex,
                                            final @Nullable UUID sourceEntityUuid,
                                            final BlockPos sourceBearingPos,
                                            final @Nullable BlockPos destinationBearingPos,
                                            final int expectedCapturedBlocks,
                                            final int expectedSymmetricSails,
                                            final float expectedAngle,
                                            final String details) {
        if (!m29Enabled()) {
            return;
        }
        Sable.LOGGER.info("SABLE_M29_SAIL_VISUAL event={} transferId={} snapshotIndex={} side=SERVER "
                        + "sourceEntityUuid={} sourceBearingPos={} destinationBearingPos={} "
                        + "expectedCapturedBlockCount={} expectedSymmetricSailCount={} expectedAngle={} details={}",
                event, transferId, snapshotIndex, sourceEntityUuid, sourceBearingPos,
                destinationBearingPos, expectedCapturedBlocks, expectedSymmetricSails,
                expectedAngle, details);
    }

    public static boolean isTarget(final Entity entity) {
        return entity.getPersistentData().getBoolean(RESTORED_TAG);
    }

    public static int expectedCapturedBlocks(final Entity entity) {
        return entity.getPersistentData().getInt(EXPECTED_BLOCKS_TAG);
    }

    public static @Nullable BlockPos bearingPos(final Entity entity) {
        final CompoundTag data = entity.getPersistentData();
        return data.contains(BEARING_POS_TAG) ? BlockPos.of(data.getLong(BEARING_POS_TAG)) : null;
    }

    public static long reverseTransferId(final Entity entity) {
        return entity.getPersistentData().getLong(M29_TRANSFER_ID_TAG);
    }

    public static int reverseSnapshotIndex(final Entity entity) {
        return entity.getPersistentData().contains(M29_SNAPSHOT_INDEX_TAG)
                ? entity.getPersistentData().getInt(M29_SNAPSHOT_INDEX_TAG) : -1;
    }

    public static int expectedSymmetricSails(final Entity entity) {
        return entity.getPersistentData().getInt(M29_EXPECTED_SAILS_TAG);
    }

    public static float expectedAngle(final Entity entity) {
        return entity.getPersistentData().getFloat(M29_EXPECTED_ANGLE_TAG);
    }

    private static @Nullable UUID sourceEntityUuid(final Entity entity) {
        final CompoundTag data = entity.getPersistentData();
        return data.hasUUID(M29_SOURCE_UUID_TAG) ? data.getUUID(M29_SOURCE_UUID_TAG) : null;
    }

    private static boolean m29Enabled() {
        return Boolean.getBoolean(M29_TRACE_PROPERTY);
    }

    private static void copyM29Marker(final CompoundTag source, final CompoundTag destination) {
        if (!source.contains(M29_TRANSFER_ID_TAG)) {
            return;
        }
        destination.putLong(M29_TRANSFER_ID_TAG, source.getLong(M29_TRANSFER_ID_TAG));
        destination.putInt(M29_SNAPSHOT_INDEX_TAG, source.getInt(M29_SNAPSHOT_INDEX_TAG));
        destination.putInt(M29_EXPECTED_SAILS_TAG, source.getInt(M29_EXPECTED_SAILS_TAG));
        destination.putFloat(M29_EXPECTED_ANGLE_TAG, source.getFloat(M29_EXPECTED_ANGLE_TAG));
        if (source.hasUUID(M29_SOURCE_UUID_TAG)) {
            destination.putUUID(M29_SOURCE_UUID_TAG, source.getUUID(M29_SOURCE_UUID_TAG));
        }
    }

    private static int capturedCount(final AbstractContraptionEntity entity) {
        return entity.getContraption() == null ? 0 : entity.getContraption().getBlocks().size();
    }

    public static int symmetricSailCount(final AbstractContraptionEntity entity) {
        if (entity.getContraption() == null) {
            return 0;
        }
        int count = 0;
        for (final StructureTemplate.StructureBlockInfo info : entity.getContraption().getBlocks().values()) {
            if ("simulated:white_symmetric_sail".equals(
                    BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).toString())) {
                count++;
            }
        }
        return count;
    }

}
