package dev.ryanhcode.sable.forge;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28NormalWorldCceSync;
import dev.ryanhcode.sable.mixin.m28.ClientLevelEntityGetterAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/** Bounded packet/local-removal provenance for reverse-restored normal-world CCEs. */
public final class SableM29RestoredCceRemovalTrace {
    private static final ThreadLocal<int[]> REMOVE_PACKET_IDS = new ThreadLocal<>();
    private static final ThreadLocal<String> SABLE_TEARDOWN = new ThreadLocal<>();
    private static final ThreadLocal<ClientRemoval> ACTIVE_REMOVAL = new ThreadLocal<>();

    private SableM29RestoredCceRemovalTrace() {
    }

    public static void beginRemovePacket(final ClientLevel level, final IntList entityIds) {
        if (!enabled() || !Minecraft.getInstance().isSameThread()) {
            return;
        }
        final int[] ids = entityIds.toIntArray();
        REMOVE_PACKET_IDS.set(ids);
        for (final int id : ids) {
            final Entity entity = level.getEntity(id);
            if (isTarget(entity)) {
                trace(entity, "CLIENT_REMOVE_PACKET_RECEIVED",
                        "packetDriven=true localCodeDriven=false packetEntityIds=" + Arrays.toString(ids)
                                + ' ' + lookups(level, entity));
            }
        }
    }

    public static void endRemovePacket() {
        REMOVE_PACKET_IDS.remove();
    }

    public static void beginSableTeardown(final long plotCoordinate) {
        if (enabled()) {
            SABLE_TEARDOWN.set("STOP_TRACKING_SUBLEVEL plotCoordinate=" + plotCoordinate);
        }
    }

    public static void endSableTeardown() {
        SABLE_TEARDOWN.remove();
    }

    public static void clientLevelRemoveEnter(final ClientLevel level, final int entityId,
                                              final Entity.RemovalReason reason) {
        if (!enabled()) {
            return;
        }
        final Entity entity = level.getEntity(entityId);
        if (!isTarget(entity)) {
            return;
        }
        final boolean packetDriven = packetContains(entityId);
        final String teardown = SABLE_TEARDOWN.get();
        ACTIVE_REMOVAL.set(new ClientRemoval(level, entity, packetDriven, teardown));
        trace(entity, "CLIENT_LEVEL_REMOVE_ENTER",
                "removalReason=" + reason + " callerKind=" + callerKind(packetDriven, teardown)
                        + " packetDriven=" + packetDriven + " localCodeDriven=" + !packetDriven
                        + ' ' + lookups(level, entity)
                        + " callerFingerprint=" + callerFingerprint());
    }

    public static void clientLevelRemoveReturn(final ClientLevel level, final int entityId,
                                               final Entity.RemovalReason reason) {
        final ClientRemoval removal = ACTIVE_REMOVAL.get();
        ACTIVE_REMOVAL.remove();
        if (removal == null || removal.entity().getId() != entityId) {
            return;
        }
        trace(removal.entity(), "CLIENT_LEVEL_REMOVE_RETURN",
                "removalReason=" + reason + " callerKind="
                        + callerKind(removal.packetDriven(), removal.sableTeardown())
                        + " packetDriven=" + removal.packetDriven()
                        + " localCodeDriven=" + !removal.packetDriven()
                        + ' ' + lookups(level, removal.entity()));
    }

    public static void entitySetRemoved(final Entity entity, final Entity.RemovalReason reason) {
        if (!isTarget(entity) || !entity.level().isClientSide) {
            return;
        }
        final ClientRemoval removal = ACTIVE_REMOVAL.get();
        final boolean packetDriven = removal != null && removal.entity() == entity && removal.packetDriven();
        final String teardown = SABLE_TEARDOWN.get();
        trace(entity, "CLIENT_ENTITY_SET_REMOVED",
                "removalReason=" + reason + " callerKind=" + callerKind(packetDriven, teardown)
                        + " packetDriven=" + packetDriven + " localCodeDriven=" + !packetDriven
                        + " callerFingerprint=" + callerFingerprint());
    }

    public static void trackingEnd(final Entity entity) {
        if (isTarget(entity)) {
            trace(entity, "CLIENT_TRACKING_END",
                    "removalReason=" + entity.getRemovalReason()
                            + " packetDriven=" + packetContains(entity.getId())
                            + " sableTeardownActive=" + (SABLE_TEARDOWN.get() != null));
        }
    }

    public static void entityAdding(final ClientLevel level, final int entityId, final Entity incoming) {
        if (!enabled()) {
            return;
        }
        final Entity existingById = level.getEntity(entityId);
        final Entity existingByUuid = lookupByUuid(level, incoming);
        final boolean relevant = isTarget(incoming) || isTarget(existingById) || isTarget(existingByUuid);
        if (!relevant) {
            return;
        }
        if (existingById != null && existingById != incoming) {
            final Entity target = isTarget(existingById) ? existingById : incoming;
            trace(target, "CLIENT_ENTITY_ID_COLLISION",
                    "incomingId=" + entityId + " incomingUuid=" + incoming.getUUID()
                            + " existingId=" + existingById.getId()
                            + " existingUuid=" + existingById.getUUID());
            trace(target, "CLIENT_ENTITY_REPLACED", "collisionKind=ENTITY_ID");
        }
        if (existingByUuid != null && existingByUuid != incoming) {
            final Entity target = isTarget(existingByUuid) ? existingByUuid : incoming;
            trace(target, "CLIENT_ENTITY_UUID_COLLISION",
                    "incomingId=" + entityId + " incomingUuid=" + incoming.getUUID()
                            + " existingId=" + existingByUuid.getId()
                            + " existingUuid=" + existingByUuid.getUUID());
            trace(target, "CLIENT_ENTITY_REPLACED", "collisionKind=ENTITY_UUID");
        }
    }

    private static boolean enabled() {
        return Boolean.getBoolean(SableM28NormalWorldCceSync.M29_TRACE_PROPERTY);
    }

    private static boolean isTarget(final @Nullable Entity entity) {
        return enabled() && entity instanceof ControlledContraptionEntity
                && SableM28NormalWorldCceSync.isTarget(entity);
    }

    private static boolean packetContains(final int entityId) {
        final int[] ids = REMOVE_PACKET_IDS.get();
        if (ids == null) {
            return false;
        }
        for (final int id : ids) {
            if (id == entityId) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Entity lookupByUuid(final ClientLevel level, final Entity entity) {
        return ((ClientLevelEntityGetterAccessor) (Object) level)
                .sable$invokeGetEntities().get(entity.getUUID());
    }

    private static String lookups(final ClientLevel level, final Entity entity) {
        return "clientLevelLookupById=" + (level.getEntity(entity.getId()) == entity)
                + " clientLevelLookupByUuid=" + (lookupByUuid(level, entity) == entity);
    }

    private static String callerKind(final boolean packetDriven, final @Nullable String teardown) {
        if (packetDriven) {
            return "SERVER_REMOVE_PACKET";
        }
        if (teardown != null) {
            return "SABLE_SUBLEVEL_TEARDOWN:" + teardown;
        }
        return "LOCAL_OTHER";
    }

    private static String callerFingerprint() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> !frame.getClassName().equals(SableM29RestoredCceRemovalTrace.class.getName()))
                .limit(10)
                .map(frame -> frame.getClassName() + '#' + frame.getMethodName())
                .reduce((left, right) -> left + " <- " + right)
                .orElse("unavailable"));
    }

    private static void trace(final Entity entity, final String event, final String details) {
        SableM28NormalWorldCceSync.traceM29Entity(event, (AbstractContraptionEntity) entity, details);
    }

    private record ClientRemoval(ClientLevel level, Entity entity, boolean packetDriven,
                                 @Nullable String sableTeardown) {
    }
}
