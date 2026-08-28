package dev.ryanhcode.sable.api.sublevel;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixin.m11.create.BlockEntityConfigurationPacketAccessor;
import dev.ryanhcode.sable.mixin.m11.create.ValueSettingsPacketAccessor;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundCreateValueSettingsSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server-side bridge for Create value-setting packets targeting sub-level BlockEntities. */
public final class SubLevelCreateValueSettingsHelper {
    private static final double SERVER_REACH_SLOP = 3.0;

    private SubLevelCreateValueSettingsHelper() {
    }

    public static void handleValueSettingsOnSubLevel(final ServerLevel level,
                                                     final ServerPlayer player,
                                                     final ServerboundCreateValueSettingsSubLevelPacket packet) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            Sable.LOGGER.error("SABLE_M11_VALUE_SERVER rejected=no_container sublevel={}", packet.subLevelId());
            return;
        }

        final SubLevel resolved = container.getSubLevel(packet.subLevelId());
        if (!(resolved instanceof final ServerSubLevel subLevel) || subLevel.isRemoved()) {
            Sable.LOGGER.warn("SABLE_M11_VALUE_SERVER rejected=missing_sublevel sublevel={}", packet.subLevelId());
            return;
        }

        final BlockPos plotBlockPos = subLevel.getPlot().getCenterBlock().offset(packet.localBlockPos());
        if (SubLevelInteractionHelper.findServerSubLevel(level, plotBlockPos) != subLevel) {
            Sable.LOGGER.warn("SABLE_M11_VALUE_SERVER rejected=plot_mismatch sublevel={} localBlockPos={} plotBlockPos={}",
                    subLevel.getUniqueId(), packet.localBlockPos(), plotBlockPos);
            return;
        }

        final Vec3 visibleWorldHit = visibleWorldHit(subLevel, packet, plotBlockPos);
        final double reachDistance = player.getEyePosition().distanceTo(visibleWorldHit);
        final double allowedReach = player.getBlockReach() + SERVER_REACH_SLOP;
        if (reachDistance > allowedReach) {
            logValueServer(subLevel, player, packet, plotBlockPos, null, null, null, null,
                    "rejected_reach", reachDistance);
            return;
        }

        final BlockPos visibleWorldBlock = BlockPos.containing(
                subLevel.logicalPose().transformPosition(Vec3.atCenterOf(plotBlockPos)));
        if (!level.mayInteract(player, visibleWorldBlock)) {
            logValueServer(subLevel, player, packet, plotBlockPos, null, null, null, null,
                    "rejected_mayInteract", reachDistance);
            return;
        }

        final BlockEntity blockEntity = SubLevelBlockStateLookup.getBlockEntity(subLevel, plotBlockPos);
        if (!(blockEntity instanceof final SmartBlockEntity smartBlockEntity)) {
            logValueServer(subLevel, player, packet, plotBlockPos, blockEntity, null, null, null,
                    "rejected_not_smart_block_entity", reachDistance);
            return;
        }

        final ValueSettingsBehaviour behaviour = findBehaviour(smartBlockEntity, packet.behaviourIndex());
        final Object valueBefore = behaviour == null ? "missing" : behaviour.getValueSettings();
        final ValueSettingsPacket createPacket = new ValueSettingsPacket(
                plotBlockPos,
                packet.row(),
                packet.value(),
                packet.interactHand(),
                plotHitResult(packet, plotBlockPos),
                packet.side(),
                packet.ctrlDown(),
                packet.behaviourIndex());
        ((ValueSettingsPacketAccessor) createPacket).sable$applySettings(player, smartBlockEntity);
        final Object valueAfter = behaviour == null ? "missing" : behaviour.getValueSettings();
        if (((BlockEntityConfigurationPacketAccessor) createPacket).sable$causeUpdate()) {
            ((SyncedBlockEntity) smartBlockEntity).sendData();
            smartBlockEntity.setChanged();
        }

        logValueServer(subLevel, player, packet, plotBlockPos, smartBlockEntity, behaviour, valueBefore, valueAfter,
                "delegated_to_create", reachDistance);
    }

    private static @Nullable ValueSettingsBehaviour findBehaviour(final SmartBlockEntity smartBlockEntity,
                                                                  final int behaviourIndex) {
        for (final BlockEntityBehaviour behaviour : smartBlockEntity.getAllBehaviours()) {
            if (behaviour instanceof final ValueSettingsBehaviour valueSettingsBehaviour
                    && valueSettingsBehaviour.netId() == behaviourIndex) {
                return valueSettingsBehaviour;
            }
        }
        return null;
    }

    private static @Nullable BlockHitResult plotHitResult(final ServerboundCreateValueSettingsSubLevelPacket packet,
                                                         final BlockPos plotBlockPos) {
        if (packet.hitResult() == null) {
            return null;
        }
        return new BlockHitResult(packet.hitResult().getLocation(), packet.hitResult().getDirection(), plotBlockPos,
                packet.hitResult().isInside());
    }

    private static Vec3 visibleWorldHit(final ServerSubLevel subLevel,
                                        final ServerboundCreateValueSettingsSubLevelPacket packet,
                                        final BlockPos plotBlockPos) {
        if (packet.hitResult() != null) {
            return subLevel.logicalPose().transformPosition(packet.hitResult().getLocation());
        }
        return subLevel.logicalPose().transformPosition(Vec3.atCenterOf(plotBlockPos));
    }

    private static void logValueServer(final ServerSubLevel subLevel,
                                       final ServerPlayer player,
                                       final ServerboundCreateValueSettingsSubLevelPacket packet,
                                       final BlockPos plotBlockPos,
                                       @Nullable final BlockEntity blockEntity,
                                       @Nullable final ValueSettingsBehaviour behaviour,
                                       @Nullable final Object valueBefore,
                                       @Nullable final Object valueAfter,
                                       final String result,
                                       final double reachDistance) {
        Sable.LOGGER.info("SABLE_M11_VALUE_SERVER sublevel={} localBlockPos={} plotBlockPos={} serverBEClass={} behaviorClass={} valueBefore={} requestedValue={} valueAfter={} delegatedToCreate={} result={} playerPos={} reachDistance={}",
                subLevel.getUniqueId(),
                packet.localBlockPos(),
                plotBlockPos,
                blockEntity == null ? "none" : blockEntity.getClass().getName(),
                behaviour == null ? "none" : behaviour.getClass().getName(),
                valueBefore,
                packet.value(),
                valueAfter,
                "delegated_to_create".equals(result),
                result,
                player.position(),
                String.format(java.util.Locale.ROOT, "%.6f", reachDistance));
    }
}
