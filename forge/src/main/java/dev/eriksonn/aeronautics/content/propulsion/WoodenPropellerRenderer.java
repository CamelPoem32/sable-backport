package dev.eriksonn.aeronautics.content.propulsion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.eriksonn.aeronautics.client.AeronauticsPropulsionClient;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public final class WoodenPropellerRenderer extends KineticBlockEntityRenderer<WoodenPropellerBlockEntity> {
    public WoodenPropellerRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(final WoodenPropellerBlockEntity blockEntity, final float partialTick,
                              final PoseStack poseStack, final MultiBufferSource buffer,
                              final int packedLight, final int packedOverlay) {
        super.renderSafe(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
        final BlockState state = blockEntity.getBlockState();
        final Direction facing = state.getValue(WoodenPropellerBlock.FACING);
        final PartialModel model = state.getValue(WoodenPropellerBlock.REVERSED)
                ? AeronauticsPropulsionClient.WOODEN_PROPELLER_REVERSED
                : AeronauticsPropulsionClient.WOODEN_PROPELLER;
        final SuperByteBuffer propeller = CachedBuffers.partialFacing(model, state);
        float angle = Mth.lerp(partialTick, blockEntity.getPreviousAngle(), blockEntity.getAngle());
        angle = Mth.DEG_TO_RAD * angle * 2.0F
                + getRotationOffsetForPosition(blockEntity, blockEntity.getBlockPos(), facing.getAxis());
        kineticRotationTransform(propeller, blockEntity, facing.getAxis(), angle, packedLight);
        if (facing.getAxis().isHorizontal()) {
            propeller.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        }
        if (facing.getAxis().isVertical()) {
            propeller.rotateCentered(AngleHelper.rad(AngleHelper.verticalAngle(facing.getOpposite())), Direction.EAST);
        }
        propeller.translate(0.0D, 0.0D, -3.0D / 16.0D)
                .rotateCentered(AngleHelper.rad(-90.0F - AngleHelper.verticalAngle(facing)), Direction.EAST);
        final VertexConsumer vertices = buffer.getBuffer(RenderType.solid());
        propeller.renderInto(poseStack, vertices);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(final WoodenPropellerBlockEntity blockEntity,
                                               final BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state,
                state.getValue(WoodenPropellerBlock.FACING).getOpposite());
    }
}
