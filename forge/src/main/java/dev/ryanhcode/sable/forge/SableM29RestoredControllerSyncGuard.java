package dev.ryanhcode.sable.forge;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionControllerLookup;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28NormalWorldCceSync;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Prevents Create's controller-missing discard only during a verified reverse-restore sync gap. */
public final class SableM29RestoredControllerSyncGuard {
    private static final String COMPLETED_TAG = "SableM29ControllerSyncCompleted";
    private static final String EXHAUSTED_TAG = "SableM29ControllerSyncExhausted";
    private static final Map<UUID, PendingController> PENDING = new ConcurrentHashMap<>();

    private SableM29RestoredControllerSyncGuard() {
    }

    /** Called immediately before Create 6.0.8 resolves the controller and discards on null. */
    public static boolean deferMissingController(final ControlledContraptionEntity entity) {
        final BlockPos controllerPos = SableCreateContraptionContext.getControllerPos(entity);
        final BlockPos bearingPos = SableM28NormalWorldCceSync.bearingPos(entity);
        final boolean payloadValid = payloadValid(entity);
        final boolean alreadyCompleted = entity.getPersistentData().getBoolean(COMPLETED_TAG)
                || entity.getPersistentData().getBoolean(EXHAUSTED_TAG);
        final boolean eligible = RestoredContraptionControllerSyncState.eligible(
                SableM28NormalWorldCceSync.isTarget(entity), entity.level().isClientSide,
                SableCreateContraptionContext.getContainingSubLevel(entity) == null,
                entity.isAlive() && !entity.isRemoved(), payloadValid,
                controllerPos != null && controllerPos.equals(bearingPos), alreadyCompleted);
        if (!eligible) {
            terminateInvalidPending(entity, payloadValid);
            return false;
        }

        if (SableCreateContraptionControllerLookup.getControllerBlockEntity(entity.level(), controllerPos)
                instanceof IControlContraption) {
            entity.getPersistentData().putBoolean(COMPLETED_TAG, true);
            final PendingController pending = PENDING.remove(entity.getUUID());
            if (pending != null) {
                pending.state.controllerAvailable();
                trace(entity, "CLIENT_CONTROLLER_AVAILABLE",
                        "missingControllerTicks=" + pending.state.missingControllerTicks());
                trace(entity, "CLIENT_CONTROLLER_NORMAL_TICK_RESUMED",
                        "terminalReason=CONTROLLER_APPEARED");
            }
            return false;
        }

        final PendingController pending = PENDING.compute(entity.getUUID(), (uuid, existing) ->
                existing != null && existing.entity == entity ? existing
                        : new PendingController(entity, new RestoredContraptionControllerSyncState()));
        final RestoredContraptionControllerSyncState.Transition transition = pending.state.controllerMissing();
        if (transition == RestoredContraptionControllerSyncState.Transition.WAIT_BEGIN) {
            trace(entity, "CLIENT_CONTROLLER_WAIT_BEGIN", details(entity, pending));
        } else if (transition == RestoredContraptionControllerSyncState.Transition.STILL_MISSING_CHECKPOINT) {
            trace(entity, "CLIENT_CONTROLLER_STILL_MISSING", details(entity, pending));
        } else if (transition == RestoredContraptionControllerSyncState.Transition.TIMEOUT) {
            entity.getPersistentData().putBoolean(EXHAUSTED_TAG, true);
            PENDING.remove(entity.getUUID(), pending);
            trace(entity, "CLIENT_CONTROLLER_WAIT_TIMEOUT", details(entity, pending));
            return false;
        }
        if (RestoredContraptionControllerSyncState.suppressesDiscard(transition)) {
            SableM28NormalWorldCceSync.traceM29Entity(
                    "CLIENT_CONTROLLER_DISCARD_SUPPRESSED", "CLIENT_CONTROLLER_DISCARD_SUPPRESSED",
                    entity, details(entity, pending));
            return true;
        }
        return false;
    }

    public static void entityLeft(final Entity raw) {
        if (!(raw instanceof final ControlledContraptionEntity entity)) {
            return;
        }
        final PendingController pending = PENDING.remove(entity.getUUID());
        if (pending != null) {
            pending.state.entityRemoved();
        }
    }

    public static void levelUnloaded(final Level level) {
        for (final PendingController pending : PENDING.values()) {
            if (pending.entity.level() == level && PENDING.remove(pending.entity.getUUID(), pending)) {
                pending.state.levelUnloaded();
            }
        }
    }

    private static void terminateInvalidPending(final ControlledContraptionEntity entity,
                                                final boolean payloadValid) {
        final PendingController pending = PENDING.remove(entity.getUUID());
        if (pending != null && !payloadValid) {
            pending.state.payloadInvalid();
        }
    }

    private static boolean payloadValid(final ControlledContraptionEntity entity) {
        final int expectedBlocks = SableM28NormalWorldCceSync.expectedCapturedBlocks(entity);
        return expectedBlocks > 0 && entity.getContraption() != null
                && entity.getContraption().getBlocks().size() == expectedBlocks;
    }

    private static String details(final ControlledContraptionEntity entity,
                                  final PendingController pending) {
        final BlockPos bearingPos = SableM28NormalWorldCceSync.bearingPos(entity);
        return "bearingPos=" + bearingPos
                + " bearingBlockState=" + (bearingPos == null ? "none" : entity.level().getBlockState(bearingPos))
                + " bearingBlockEntityPresent=" + (bearingPos != null
                && entity.level().getBlockEntity(bearingPos) instanceof IControlContraption)
                + " missingControllerTicks=" + pending.state.missingControllerTicks()
                + " maxMissingControllerTicks="
                + RestoredContraptionControllerSyncState.MAX_MISSING_CONTROLLER_TICKS;
    }

    private static void trace(final ControlledContraptionEntity entity,
                              final String event,
                              final String details) {
        SableM28NormalWorldCceSync.traceM29Entity(event, entity, details);
    }

    private record PendingController(ControlledContraptionEntity entity,
                                     RestoredContraptionControllerSyncState state) {
    }
}
