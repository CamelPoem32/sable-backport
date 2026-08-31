package dev.ryanhcode.sable.mixin.m19.create;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.util.SublevelRenderOffsetHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Renders Catnip placement-helper ghost blocks through Sable's visible-space transform. */
@Mixin(targets = "net.createmod.catnip.ghostblock.GhostBlockRenderer$TransparentGhostBlockRenderer", remap = false)
public abstract class GhostBlockRendererMixin {
    @Unique
    private static final Set<String> SABLE$LOGGED_GHOSTS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85837_(DDD)V", ordinal = 0,
                    remap = false))
    private void sable$translatePlacementGhost(final PoseStack poseStack,
                                               final double x,
                                               final double y,
                                               final double z) {
        final Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        final Vec3 rawGhostPosition = new Vec3(x + camera.x, y + camera.y, z + camera.z);
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(rawGhostPosition);
        if (subLevel == null) {
            poseStack.translate(x, y, z);
            return;
        }

        SublevelRenderOffsetHelper.posePlotToProjected(subLevel, poseStack);
        final Vec3 storageTranslation = SublevelRenderOffsetHelper.translation(rawGhostPosition);
        final Vec3 cameraRelativeVisible = new Vec3(x - storageTranslation.x, y - storageTranslation.y,
                z - storageTranslation.z);
        poseStack.translate(cameraRelativeVisible.x, cameraRelativeVisible.y, cameraRelativeVisible.z);

        final BlockPos rawGhostBlock = BlockPos.containing(rawGhostPosition);
        final BlockPos localGhostBlock = rawGhostBlock.subtract(subLevel.getPlot().getCenterBlock());
        final Vec3 visibleWorldPosition = subLevel.renderPose().transformPosition(rawGhostPosition);
        final String key = subLevel.getUniqueId() + ":" + rawGhostBlock;
        if (SABLE$LOGGED_GHOSTS.add(key)) {
            Sable.LOGGER.info("SABLE_M19_GHOST subLevel={} targetLocal={} targetRaw={} "
                            + "renderPositionBeforeSable={} visibleWorldPosition={} cameraRelativePosition={} "
                            + "rendererClass=net.createmod.catnip.ghostblock.GhostBlockRenderer$TransparentGhostBlockRenderer "
                            + "visibleTransformApplied=true hiddenPlotPoseTranslation=false",
                    subLevel.getUniqueId(), localGhostBlock, rawGhostBlock, rawGhostPosition, visibleWorldPosition,
                    cameraRelativeVisible);
        }
    }
}
