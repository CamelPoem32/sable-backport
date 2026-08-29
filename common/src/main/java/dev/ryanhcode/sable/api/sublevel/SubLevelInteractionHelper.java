package dev.ryanhcode.sable.api.sublevel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundUseItemOnSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server-side bridge for ordinary block/item interactions targeting sub-level plot blocks. */
public final class SubLevelInteractionHelper {
    private static final double SERVER_REACH_SLOP = 3.0;

    private SubLevelInteractionHelper() {
    }

    public static @Nullable ServerSubLevel findServerSubLevel(final Level level, final BlockPos plotBlockPos) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, plotBlockPos);
        if (subLevel instanceof final ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            return serverSubLevel;
        }
        return null;
    }

    /** Handles a C2S sub-level item-use intent on the server thread. */
    public static void handleUseItemOnSubLevel(final ServerLevel level,
                                               final ServerPlayer player,
                                               final ServerboundUseItemOnSubLevelPacket packet) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            Sable.LOGGER.error("SABLE_M11_INTERACT_SERVER rejected=no_container sublevel={}", packet.subLevelId());
            return;
        }

        final SubLevel resolved = container.getSubLevel(packet.subLevelId());
        if (!(resolved instanceof final ServerSubLevel subLevel) || subLevel.isRemoved()) {
            Sable.LOGGER.warn("SABLE_M11_INTERACT_SERVER rejected=missing_sublevel sublevel={}",
                    packet.subLevelId());
            return;
        }

        final BlockPos plotBlockPos = subLevel.getPlot().getCenterBlock().offset(packet.localBlockPos());
        if (findServerSubLevel(level, plotBlockPos) != subLevel) {
            Sable.LOGGER.warn("SABLE_M11_INTERACT_SERVER rejected=plot_mismatch sublevel={} localBlockPos={} plotBlockPos={}",
                    subLevel.getUniqueId(), packet.localBlockPos(), plotBlockPos);
            return;
        }

        final BlockPos center = subLevel.getPlot().getCenterBlock();
        final Vec3 plotHit = new Vec3(
                center.getX() + packet.localHit().x(),
                center.getY() + packet.localHit().y(),
                center.getZ() + packet.localHit().z());
        final Vec3 visibleWorldHit = subLevel.logicalPose().transformPosition(plotHit);
        final double reachDistance = player.getEyePosition().distanceTo(visibleWorldHit);
        final double allowedReach = player.getBlockReach() + SERVER_REACH_SLOP;
        if (reachDistance > allowedReach) {
            logServerInteraction(subLevel, player, packet, plotBlockPos, visibleWorldHit, reachDistance,
                    InteractionResult.FAIL, "rejected_reach", null, null);
            return;
        }

        final BlockPos visibleWorldBlock = BlockPos.containing(
                subLevel.logicalPose().transformPosition(Vec3.atCenterOf(plotBlockPos)));
        if (!level.mayInteract(player, visibleWorldBlock)) {
            logServerInteraction(subLevel, player, packet, plotBlockPos, visibleWorldHit, reachDistance,
                    InteractionResult.FAIL, "rejected_mayInteract", null, null);
            return;
        }

        final BlockState oldState = SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, plotBlockPos);
        final ItemStack stack = player.getItemInHand(packet.hand());
        final BlockHitResult plotHitResult = new BlockHitResult(plotHit, packet.localFace(), plotBlockPos, false);
        final InteractionResult result = player.gameMode.useItemOn(player, level, stack, packet.hand(), plotHitResult);
        final BlockState newState = SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, plotBlockPos);

        logServerInteraction(subLevel, player, packet, plotBlockPos, visibleWorldHit, reachDistance,
                result, "server_game_mode_useItemOn", oldState, newState);
        logServerEdit(subLevel, player, packet, oldState, newState, result);
    }

    private static void logServerInteraction(final ServerSubLevel subLevel,
                                             final ServerPlayer player,
                                             final ServerboundUseItemOnSubLevelPacket packet,
                                             final BlockPos plotBlockPos,
                                             final Vec3 visibleWorldHit,
                                             final double reachDistance,
                                             final InteractionResult result,
                                             final String delegatedPath,
                                             @Nullable final BlockState stateBefore,
                                             @Nullable final BlockState stateAfter) {
        final ItemStack stack = player.getItemInHand(packet.hand());
        final Direction localFace = packet.localFace();
        Sable.LOGGER.info("SABLE_M11_INTERACT_SERVER sublevel={} localBlockPos={} plotBlockPos={} visibleWorldHit={} playerPos={} reachDistance={} localFace={} item={} hand={} delegatedPath={} result={} stateBefore={} stateAfter={} stateChanged={}",
                subLevel.getUniqueId(),
                packet.localBlockPos(),
                plotBlockPos,
                visibleWorldHit,
                player.position(),
                String.format(java.util.Locale.ROOT, "%.6f", reachDistance),
                localFace,
                stack.getItem(),
                packet.hand(),
                delegatedPath,
                result,
                stateBefore,
                stateAfter,
                stateBefore != null && stateAfter != null && !stateBefore.equals(stateAfter));
    }

    private static void logServerEdit(final ServerSubLevel subLevel,
                                      final ServerPlayer player,
                                      final ServerboundUseItemOnSubLevelPacket packet,
                                      @Nullable final BlockState stateBefore,
                                      @Nullable final BlockState stateAfter,
                                      final InteractionResult result) {
        final ItemStack stack = player.getItemInHand(packet.hand());
        final boolean blockItem = stack.getItem() instanceof BlockItem;
        final boolean stateChanged = stateBefore != null && stateAfter != null && !stateBefore.equals(stateAfter);
        if (!blockItem && !stateChanged) {
            return;
        }
        Sable.LOGGER.info("SABLE_M13_EDIT action=place sublevel={} localPos={} state={} "
                        + "serverResolved=true result={} stateAfter={} stateChanged={}",
                subLevel.getUniqueId(),
                packet.localBlockPos(),
                stateBefore,
                result,
                stateAfter,
                stateChanged);
    }
}
