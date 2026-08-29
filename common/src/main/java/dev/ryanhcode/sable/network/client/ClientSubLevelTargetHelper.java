package dev.ryanhcode.sable.network.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixin.punching.ItemInvoker;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/** Canonical client-side targeting view for visible static blocks stored inside Sable sub-level plots. */
public final class ClientSubLevelTargetHelper {
    private static String lastLoggedTarget = "";

    private ClientSubLevelTargetHelper() {
    }

    public static @Nullable Target resolveFromHit(final Level level, @Nullable final HitResult hitResult) {
        if (!(hitResult instanceof final BlockHitResult blockHitResult)
                || blockHitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(level, blockHitResult.getBlockPos());
        if (!(subLevel instanceof final ClientSubLevel clientSubLevel)) {
            return null;
        }

        final BlockPos plotBlockPos = blockHitResult.getBlockPos().immutable();
        final BlockPos center = clientSubLevel.getPlot().getCenterBlock();
        final BlockPos localBlockPos = plotBlockPos.subtract(center);
        final Vector3d localHit = JOMLConversion.toJOML(blockHitResult.getLocation())
                .sub(center.getX(), center.getY(), center.getZ());
        final Vec3 visibleWorldHit = clientSubLevel.renderPose().transformPosition(blockHitResult.getLocation());
        final BlockState state = level.getBlockState(plotBlockPos);
        final BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(plotBlockPos) : null;
        return new Target(clientSubLevel, blockHitResult, plotBlockPos, localBlockPos.immutable(), localHit,
                blockHitResult.getDirection(), visibleWorldHit, state, blockEntity);
    }

    public static boolean refreshMinecraftHitResult(final LocalPlayer player,
                                                    final Level level,
                                                    @Nullable final HitResult vanillaHit) {
        if (vanillaHit != null && vanillaHit.getType() == HitResult.Type.ENTITY) {
            Minecraft.getInstance().hitResult = vanillaHit;
            logTargetTransition(null, vanillaHit);
            return false;
        }

        Target target = resolveFromHit(level, vanillaHit);
        if (target == null) {
            final BlockHitResult povHitResult =
                    ItemInvoker.sable$getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            target = resolveFromHit(level, povHitResult);
        }

        if (target == null) {
            // Restore the fresh vanilla result when the cursor leaves Sable. This keeps passive
            // consumers such as Jade on the ordinary world target instead of a stale plot hit.
            Minecraft.getInstance().hitResult = vanillaHit;
            logTargetTransition(null, vanillaHit);
            return false;
        }

        Minecraft.getInstance().hitResult = target.hitResult();
        logTargetTransition(target, vanillaHit);
        return true;
    }

    private static void logTargetTransition(@Nullable final Target target, @Nullable final HitResult vanillaHit) {
        final String key;
        if (target == null) {
            key = "NONE:" + (vanillaHit == null ? "null" : vanillaHit.getType());
        } else {
            key = "STATIC_SABLE_BLOCK:" + target.subLevel().getUniqueId() + ":" + target.localBlockPos() + ":"
                    + target.state();
        }
        if (key.equals(lastLoggedTarget)) {
            return;
        }
        lastLoggedTarget = key;

        if (target == null) {
            Sable.LOGGER.info("SABLE_M13_TARGET targetType=NONE sublevel=none localPos=none contraptionEntityId=none "
                            + "blockState=none visibleHit=none face=none vanillaHitResultType={}",
                    vanillaHit == null ? "null" : vanillaHit.getType());
            return;
        }

        Sable.LOGGER.info("SABLE_M13_TARGET targetType=STATIC_SABLE_BLOCK sublevel={} localPos={} "
                        + "contraptionEntityId=none blockState={} visibleHit={} face={} vanillaHitResultType={}",
                target.subLevel().getUniqueId(),
                target.localBlockPos(),
                target.state(),
                target.visibleWorldHit(),
                target.localFace(),
                vanillaHit == null ? "null" : vanillaHit.getType());
    }

    public record Target(ClientSubLevel subLevel,
                         BlockHitResult hitResult,
                         BlockPos plotBlockPos,
                         BlockPos localBlockPos,
                         Vector3d localHit,
                         Direction localFace,
                         Vec3 visibleWorldHit,
                         BlockState state,
                         @Nullable BlockEntity blockEntity) {
    }
}
