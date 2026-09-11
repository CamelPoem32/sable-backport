package dev.simulated_team.simulated.content.blocks.steering_wheel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class SteeringWheelRenderer extends KineticBlockEntityRenderer<SteeringWheelBlockEntity> {

    public SteeringWheelRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(final SteeringWheelBlockEntity blockEntity, final float partialTick,
                              final PoseStack poseStack, final MultiBufferSource buffers,
                              final int light, final int overlay) {
        final BlockState state = blockEntity.getBlockState();
        final boolean floor = state.getValue(SteeringWheelBlock.ON_FLOOR);
        final Direction facing = state.getValue(SteeringWheelBlock.FACING);
        final RenderType renderType = this.getRenderType(blockEntity, this.getRenderedBlockState(blockEntity));

        renderRotatingBuffer(blockEntity, CachedBuffers.partialFacing(
                AllPartialModels.SHAFT_HALF, state, floor ? Direction.DOWN : Direction.UP),
                poseStack, buffers.getBuffer(renderType), light);

        final SuperByteBuffer wheel = CachedBuffers.partial(SteeringWheelClient.WHEEL, state);
        wheel.rotateCentered(facing.getRotation());
        wheel.translate(0.0D, 6.5D / 16.0D, floor ? -5.0D / 16.0D : 5.0D / 16.0D);
        wheel.rotateCentered(blockEntity.getRenderAngle(partialTick), Direction.UP);
        wheel.light(light).renderInto(poseStack, buffers.getBuffer(RenderType.solid()));
    }
}
