package dev.ryanhcode.sable.mixin.m20.create;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SublevelRenderOffsetHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "com.simibubi.create.foundation.blockEntity.behaviour.ValueBox", remap = false)
public abstract class ValueBoxMixin {
    private static final Set<String> LOGGED_VALUE_BOX = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Redirect(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;F)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85837_(DDD)V", ordinal = 0))
    private void sable$translateValueBox(final PoseStack poseStack, final double x, final double y, final double z) {
        final Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        final Vec3 center = new Vec3(x + camera.x, y + camera.y, z + camera.z);
        SublevelRenderOffsetHelper.posePlotToProjected(Sable.HELPER.getContainingClient(center), poseStack);
        final Vec3 translation = SublevelRenderOffsetHelper.translation(center);
        final Vec3 adjusted = new Vec3(x - translation.x, y - translation.y, z - translation.z);
        if (LOGGED_VALUE_BOX.add(center.toString())) {
            Sable.LOGGER.info("SABLE_M20_VALUE_BOX_RENDER rawCenter={} cameraRelativeInput=({}, {}, {}) "
                            + "cameraRelativeAdjusted={} hiddenPlotPoseTranslation=false",
                    center, x, y, z, adjusted);
        }
        poseStack.translate(adjusted.x, adjusted.y, adjusted.z);
    }
}
