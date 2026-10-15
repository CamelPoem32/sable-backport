package dev.ryanhcode.sable.compatibility.create.goggles;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import java.lang.ref.WeakReference;

/** Client-only TFMG overlay entry guard. World lookup is ownership-only, never a nearest-block search. */
public final class SableGoggleOverlayGuard {
    private static WeakReference<Level> lastLevel = new WeakReference<>(null);
    private static String lastDecision = "";
    private static int logged;
    private static long nextLog;
    private SableGoggleOverlayGuard() { }

    public static boolean allow(@Nullable final Level level, @Nullable final HitResult hit, final boolean clientReady) {
        final BlockPos block = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? blockHit.getBlockPos() : null;
        final boolean hidden = block != null && level != null
                && Sable.HELPER.isInPlotGrid(level, block.getX() >> 4, block.getZ() >> 4);
        final var decision = GoggleOverlayHitPolicy.decide(hit, clientReady, hidden);
        trace(level, hit, block, hidden, decision);
        return decision == GoggleOverlayHitPolicy.Decision.NATIVE_PARENT_BLOCK;
    }

    private static void trace(@Nullable final Level level, @Nullable final HitResult hit, @Nullable final BlockPos block,
                              final boolean hidden, final GoggleOverlayHitPolicy.Decision decision) {
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS || level == null) return;
        if (lastLevel.get() != level) { lastLevel = new WeakReference<>(level); logged = 0; nextLog = 0; lastDecision = ""; }
        final Entity entity = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        final String entityClass = entity == null ? "none" : entity.getClass().getName();
        final String key = decision + ":" + entityClass + ":" + block;
        if (logged >= 32 || key.equals(lastDecision) || level.getGameTime() < nextLog) return;
        logged++; lastDecision = key; nextLog = level.getGameTime() + 20;
        final boolean nested = entity instanceof AbstractContraptionEntity contraption
                && SableCreateContraptionContext.getContainingSubLevel(contraption) != null;
        Sable.LOGGER.info("SABLE_M44_GOGGLE hitResultType={} entityClass={} sableNestedEntity={} "
                        + "physicalBlockResolved={} physicalBlockPos={} rawBlockPos={} overlayDecision={} "
                        + "globalHitUnchanged=true owner=tfmg-1.0.2f",
                hit == null ? "null" : hit.getType(), entityClass, nested,
                decision == GoggleOverlayHitPolicy.Decision.NATIVE_PARENT_BLOCK,
                hidden ? "none" : block, hidden ? block : "none", decision);
    }
}
