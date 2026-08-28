package dev.ryanhcode.sable.network.client;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixin.m11.create.BlockEntityConfigurationPacketAccessor;
import dev.ryanhcode.sable.mixin.m11.create.ValueSettingsPacketAccessor;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundCreateValueSettingsSubLevelPacket;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Client-side bridge for Create value-setting packets targeting Sable client sub-level BlockEntities. */
public final class ClientSubLevelCreateValueSettingsHelper {
    private ClientSubLevelCreateValueSettingsHelper() {
    }

    public static boolean trySendValueSettingsPacket(final Object packet) {
        if (!(packet instanceof final ValueSettingsPacket valueSettingsPacket)) {
            return false;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }

        final BlockPos plotBlockPos = ((BlockEntityConfigurationPacketAccessor) valueSettingsPacket).sable$getPos();
        final SubLevel containing = Sable.HELPER.getContaining(minecraft.level, plotBlockPos);
        if (!(containing instanceof final ClientSubLevel subLevel)) {
            return false;
        }

        final ValueSettingsPacketAccessor accessor = (ValueSettingsPacketAccessor) valueSettingsPacket;
        final BlockPos localBlockPos = plotBlockPos.subtract(subLevel.getPlot().getCenterBlock());
        final BlockEntity blockEntity = minecraft.level.getBlockEntity(plotBlockPos);
        final String behaviorClass = behaviorClass(blockEntity, accessor.sable$getBehaviourIndex());

        SableTCPPackets.sendToServer(new ServerboundCreateValueSettingsSubLevelPacket(
                subLevel.getUniqueId(),
                localBlockPos.immutable(),
                accessor.sable$getRow(),
                accessor.sable$getValue(),
                accessor.sable$getInteractHand(),
                accessor.sable$getHitResult(),
                accessor.sable$getSide(),
                accessor.sable$getCtrlDown(),
                accessor.sable$getBehaviourIndex()));

        Sable.LOGGER.info("SABLE_M11_VALUE_CLIENT sublevel={} localBlockPos={} clientBEClass={} behaviorClass={} requestedValue={} createPacketPath=ValueSettingsPacket sableBridge=true",
                subLevel.getUniqueId(),
                localBlockPos,
                blockEntity == null ? "none" : blockEntity.getClass().getName(),
                behaviorClass,
                accessor.sable$getValue());
        return true;
    }

    private static String behaviorClass(final BlockEntity blockEntity, final int behaviorIndex) {
        if (!(blockEntity instanceof final SmartBlockEntity smartBlockEntity)) {
            return "none";
        }
        for (final BlockEntityBehaviour behaviour : smartBlockEntity.getAllBehaviours()) {
            if (behaviour instanceof final ValueSettingsBehaviour valueSettingsBehaviour
                    && valueSettingsBehaviour.netId() == behaviorIndex) {
                return behaviour.getClass().getName();
            }
        }
        return "none";
    }
}
