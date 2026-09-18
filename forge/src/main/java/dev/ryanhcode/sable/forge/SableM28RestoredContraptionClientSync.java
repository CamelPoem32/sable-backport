package dev.ryanhcode.sable.forge;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.RestoredContraptionClientSyncDecision;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28NormalWorldCceSync;

/** Repairs Flywheel admission after Forge has decoded Create's additional spawn payload. */
public final class SableM28RestoredContraptionClientSync {
    private SableM28RestoredContraptionClientSync() {
    }

    public static void afterSpawnData(final AbstractContraptionEntity entity) {
        final int expected = SableM28NormalWorldCceSync.expectedCapturedBlocks(entity);
        final int actual = entity.getContraption() == null ? 0 : entity.getContraption().getBlocks().size();
        final RestoredContraptionClientSyncDecision.Action action = RestoredContraptionClientSyncDecision.evaluate(
                SableM28NormalWorldCceSync.isTarget(entity), entity.level().isClientSide,
                SableCreateContraptionContext.getContainingSubLevel(entity) == null, expected, actual);
        if (action == RestoredContraptionClientSyncDecision.Action.NONE) {
            return;
        }
        if (action == RestoredContraptionClientSyncDecision.Action.FAIL_PAYLOAD_VALIDATION) {
            Sable.LOGGER.error("SABLE_M36_NORMAL_WORLD_CCE_SYNC stage=CLIENT_PAYLOAD_INVALID entityId={} "
                            + "entityUuid={} expectedCapturedBlockCount={} actualCapturedBlockCount={}",
                    entity.getId(), entity.getUUID(), expected, actual);
            return;
        }

        final VisualizationManager manager = VisualizationManager.get(entity.level());
        if (manager != null) {
            manager.entities().queueRemove(entity);
            manager.entities().queueAdd(entity);
        }
        Sable.LOGGER.info("SABLE_M36_NORMAL_WORLD_CCE_SYNC stage=CLIENT_VISUAL_REQUEUED entityId={} "
                        + "entityUuid={} expectedCapturedBlockCount={} actualCapturedBlockCount={} managerPresent={}",
                entity.getId(), entity.getUUID(), expected, actual, manager != null);
    }
}
