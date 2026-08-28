package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Shared camera-relative transform helpers for the retained vanilla sublevel renderer.
 */
public final class VanillaSubLevelRenderTransforms {

    private VanillaSubLevelRenderTransforms() {
    }

    public static void applyBlockTransform(final PoseStack stack, final Matrix4f modelView, final Pose3dc renderPose,
                                           final BlockPos blockPos, final double camX, final double camY,
                                           final double camZ) {
        final Matrix4f transform = blockTransform(renderPose, blockPos, camX, camY, camZ, new Matrix4f());

        stack.last().pose().mul(modelView).mul(transform);
        transform.normal(stack.last().normal());
    }

    public static Matrix4f blockTransform(final Pose3dc renderPose, final BlockPos blockPos,
                                          final double camX, final double camY, final double camZ,
                                          final Matrix4f dest) {
        final Vector3d worldPosition = blockWorldPosition(renderPose, blockPos, new Vector3d());
        final Quaterniondc renderRot = renderPose.orientation();

        dest.identity();
        dest.translate((float) (worldPosition.x() - camX), (float) (worldPosition.y() - camY),
                (float) (worldPosition.z() - camZ));
        dest.rotate(new Quaternionf(renderRot));
        return dest;
    }

    public static Vector3d blockWorldPosition(final Pose3dc renderPose, final BlockPos blockPos,
                                              final Vector3d dest) {
        final Vector3dc renderPos = renderPose.position();
        final Quaterniondc renderRot = renderPose.orientation();
        renderRot.transform(dest.set(renderPose.rotationPoint())
                .sub(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
        return dest.negate().add(renderPos);
    }

    public static Vector3d cameraInSubLevelCoordinates(final Pose3dc renderPose, final double camX,
                                                       final double camY, final double camZ,
                                                       final Vector3d dest) {
        dest.set(camX, camY, camZ);
        renderPose.transformPositionInverse(dest);
        return dest;
    }
}
