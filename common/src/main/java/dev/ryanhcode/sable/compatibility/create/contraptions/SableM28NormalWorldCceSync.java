package dev.ryanhcode.sable.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded provenance for normal-world CCEs reconstructed by outer Sable disassembly. */
public final class SableM28NormalWorldCceSync {
    public static final String TRACE_PROPERTY = "sable.m28.traceNormalWorldCceSync";
    private static final String RESTORED_TAG = "SableM28RestoredAfterOuterDisassembly";
    private static final String EXPECTED_BLOCKS_TAG = "SableM28ExpectedCapturedBlocks";
    private static final String BEARING_POS_TAG = "SableM28BearingPos";
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> CLIENT_TARGETS = new ConcurrentHashMap<>();

    private SableM28NormalWorldCceSync() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(TRACE_PROPERTY);
    }

    public static void markBeforeAdd(final ControlledContraptionEntity entity,
                                     final BlockPos bearingPos,
                                     final int expectedCapturedBlocks) {
        final CompoundTag data = entity.getPersistentData();
        data.putBoolean(RESTORED_TAG, true);
        data.putInt(EXPECTED_BLOCKS_TAG, expectedCapturedBlocks);
        data.putLong(BEARING_POS_TAG, bearingPos.asLong());
        logOnce("SERVER_PRE_ADD", entity, "actualPosition=" + entity.position()
                + " actualBlockPos=" + entity.blockPosition()
                + " actualAabb=" + entity.getBoundingBox()
                + " trackingChunk=" + entity.chunkPosition()
                + " bearingPos=" + bearingPos);
    }

    public static void serverPostAdd(final ControlledContraptionEntity entity, final boolean accepted) {
        logOnce("SERVER_POST_ADD", entity, "addFreshEntityReturn=" + accepted
                + " authoritativeIdLookup=" + (entity.level() instanceof ServerLevel server
                && server.getEntity(entity.getId()) == entity)
                + " authoritativeUuidLookup=" + (entity.level() instanceof ServerLevel server
                && server.getEntity(entity.getUUID()) == entity));
    }

    public static void startTracking(final ServerPlayer player, final Entity target) {
        if (target instanceof final ControlledContraptionEntity controlled && isTarget(controlled)) {
            logOnce("SERVER_START_SEEN_BY_PLAYER", controlled,
                    "player=" + player.getGameProfile().getName()
                            + " playerPos=" + player.position()
                            + " trackingChunk=" + controlled.chunkPosition());
        }
    }

    public static void entityJoined(final Entity entity, final Level level) {
        if (entity instanceof final ControlledContraptionEntity controlled && isTarget(controlled)) {
            logOnce(level.isClientSide ? "CLIENT_ENTITY_ADDED" : "SERVER_TRACKING_BEGIN", controlled,
                    "levelClass=" + level.getClass().getName()
                            + " levelIdentity=" + System.identityHashCode(level));
        }
    }

    public static void beforeWriteSpawnData(final AbstractContraptionEntity entity) {
        if (isTarget(entity)) {
            logOnce("SERVER_SPAWN_PACKET_CREATED", entity, "spawnPayloadCapturedBlockCount=" + capturedCount(entity));
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
        CLIENT_TARGETS.put(entity.getUUID(), entity.getId());
        logOnce("CLIENT_SPAWN_PACKET_RECEIVED", entity,
                "clientLookupById=" + (entity.level().getEntity(entity.getId()) == entity));
        logOnce("CLIENT_ENTITY_CREATED", entity, "entityClass=" + entity.getClass().getName());
        logOnce("CLIENT_CONTRAPTION_PRESENT", entity,
                "clientContraptionNull=" + (entity.getContraption() == null)
                        + " expectedCapturedBlockCount=" + expectedCapturedBlocks(entity)
                        + " clientCapturedBlockCount=" + capturedCount(entity)
                        + " capturedBlockStates=" + capturedStates(entity));
    }

    public static void firstTick(final AbstractContraptionEntity entity) {
        if (isTarget(entity)) {
            logOnce(entity.level().isClientSide ? "CLIENT_FIRST_TICK" : "SERVER_FIRST_TICK", entity,
                    "entityAlive=" + entity.isAlive() + " entityRemoved=" + entity.isRemoved());
        }
    }

    public static void firstRender(final AbstractContraptionEntity entity) {
        if (isTarget(entity)) {
            logOnce("CLIENT_FIRST_RENDER", entity, "renderEligible="
                    + (entity.isAliveOrStale() && entity.getContraption() != null));
        }
    }

    public static void firstCollisionQuery(final AbstractContraptionEntity entity) {
        if (isTarget(entity)) {
            logOnce(entity.level().isClientSide ? "CLIENT_FIRST_COLLISION_QUERY" : "SERVER_FIRST_COLLISION_QUERY",
                    entity, "entityAabb=" + entity.getBoundingBox());
        }
    }

    public static void visualCreated(final AbstractContraptionEntity entity, final Object visual) {
        if (isTarget(entity)) {
            logOnce("CLIENT_VISUAL_CREATED", entity,
                    "visualClass=" + visual.getClass().getName()
                            + " visualIdentity=" + System.identityHashCode(visual));
        }
    }

    public static void afterSourceSubLevelRemoval(final Level level) {
        if (!level.isClientSide || CLIENT_TARGETS.isEmpty()) {
            return;
        }
        for (final Map.Entry<UUID, Integer> target : CLIENT_TARGETS.entrySet()) {
            final UUID uuid = target.getKey();
            final Entity entity = level.getEntity(target.getValue());
            if (entity instanceof final AbstractContraptionEntity contraption
                    && uuid.equals(contraption.getUUID())) {
                logOnce("CLIENT_AFTER_SOURCE_SUBLEVEL_REMOVAL", contraption,
                        "clientLookupByUuid=true entityAlive=" + contraption.isAlive()
                                + " entityRemoved=" + contraption.isRemoved());
            } else if (enabled() && LOGGED.add("CLIENT_AFTER_SOURCE_SUBLEVEL_REMOVAL_MISSING:" + uuid)) {
                Sable.LOGGER.error("SABLE_M36_NORMAL_WORLD_CCE_SYNC stage=CLIENT_AFTER_SOURCE_SUBLEVEL_REMOVAL "
                        + "side=CLIENT entityUuid={} clientLookupByUuid=false", uuid);
            }
        }
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

    private static int capturedCount(final AbstractContraptionEntity entity) {
        return entity.getContraption() == null ? 0 : entity.getContraption().getBlocks().size();
    }

    private static List<String> capturedStates(final AbstractContraptionEntity entity) {
        if (entity.getContraption() == null) {
            return List.of();
        }
        return entity.getContraption().getBlocks().values().stream()
                .map(StructureTemplate.StructureBlockInfo::state)
                .map(Object::toString)
                .limit(8)
                .toList();
    }

    private static void logOnce(final String stage, final AbstractContraptionEntity entity, final String details) {
        if (!enabled() || !LOGGED.add(stage + ':' + entity.getUUID())) {
            return;
        }
        final BlockPos controller = entity instanceof final ControlledContraptionEntity controlled
                ? SableCreateContraptionContext.getControllerPos(controlled) : null;
        Sable.LOGGER.info("SABLE_M36_NORMAL_WORLD_CCE_SYNC stage={} side={} entityId={} entityUuid={} "
                        + "entityClass={} levelClass={} levelIdentity={} entityPos={} entityBlockPos={} entityAabb={} "
                        + "controllerPos={} contraptionAnchor={} capturedBlockCount={} bearingPos={} {}",
                stage, entity.level().isClientSide ? "CLIENT" : "SERVER", entity.getId(), entity.getUUID(),
                entity.getClass().getName(), entity.level().getClass().getName(),
                System.identityHashCode(entity.level()), entity.position(), entity.blockPosition(),
                entity.getBoundingBox(), controller,
                entity.getContraption() == null ? "none" : entity.getContraption().anchor,
                capturedCount(entity), bearingPos(entity), details);
    }
}
