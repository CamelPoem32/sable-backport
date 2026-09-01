package dev.ryanhcode.sable.mixin.m20.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Lets Create's own BeltRenderer draw Sable-contained belts when Flywheel visuals are unavailable there. */
@Mixin(targets = "com.simibubi.create.content.kinetics.belt.BeltRenderer", remap = false)
public class BeltRendererMixin {

    private static final Set<String> LOGGED_BELT_BER = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> LOGGED_BELT_ITEM_DISTANCE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @WrapOperation(
            method = "renderSafe(Lcom/simibubi/create/content/kinetics/belt/BeltBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visualization/VisualizationManager;supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
    private boolean sable$allowBeltVanillaBer(final LevelAccessor level, final Operation<Boolean> original,
                                              final BeltBlockEntity blockEntity, final float partialTick,
                                              final PoseStack poseStack, final MultiBufferSource bufferSource,
                                              final int packedLight, final int packedOverlay) {
        final boolean originalVisualizationSupported = original.call(level);
        final boolean sableSubLevel = Sable.HELPER.getContainingClient(blockEntity) != null;
        final boolean returnedVisualizationSupported = sableSubLevel ? false : originalVisualizationSupported;
        final String key = blockEntity.getBlockPos().asLong() + ":" + sableSubLevel + ":"
                + originalVisualizationSupported + ":" + returnedVisualizationSupported;
        if (LOGGED_BELT_BER.add(key)) {
            Sable.LOGGER.info("SABLE_M20_SPECIALIZED_BER renderer={} blockEntityClass={} blockId={} pos={} sableSubLevel={} originalVisualizationSupported={} returnedVisualizationSupported={} kineticSpeed={} hiddenPlotPoseTranslation=false renderPath=Create_BeltRenderer",
                    "BeltRenderer", blockEntity.getClass().getName(),
                    BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()),
                    blockEntity.getBlockPos(), sableSubLevel, originalVisualizationSupported,
                    returnedVisualizationSupported, blockEntity.getSpeed());
        }
        return returnedVisualizationSupported;
    }

    @Redirect(
            method = "renderItem(Lcom/simibubi/create/content/kinetics/belt/BeltBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/core/Direction;Lnet/minecraft/core/Vec3i;Lcom/simibubi/create/content/kinetics/belt/BeltSlope;IZZLcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;m_82554_(Lnet/minecraft/world/phys/Vec3;)D"))
    private double sable$projectBeltItemDistance(final Vec3 eyePos, final Vec3 itemPos) {
        final double distance = Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(
                Minecraft.getInstance().level, eyePos, itemPos));
        final String key = itemPos.toString();
        if (LOGGED_BELT_ITEM_DISTANCE.add(key)) {
            Sable.LOGGER.info("SABLE_M20_BELT_ITEM_RENDER clientItemExists=true renderMethodReached=true "
                            + "rawItemPosition={} distancePath=Sable_distanceSquaredWithSubLevels "
                            + "distance={} hiddenPlotPoseTranslation=false rendererPath=Create_BeltRenderer_renderItem",
                    itemPos, distance);
        }
        return distance;
    }
}
