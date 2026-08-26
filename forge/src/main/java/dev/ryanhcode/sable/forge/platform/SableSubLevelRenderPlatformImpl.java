package dev.ryanhcode.sable.forge.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.platform.SableSubLevelRenderPlatform;
import dev.ryanhcode.sable.sublevel.render.vanilla.SingleBlockSubLevelWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SableSubLevelRenderPlatformImpl implements SableSubLevelRenderPlatform {
    @Override
    public void tesselateBlock(final SingleBlockSubLevelWrapper blockAndTintGetter, final BakedModel bakedModel,
                               final BlockState blockState, final BlockPos pos, final PoseStack poseStack,
                               final VertexConsumer vertexConsumer, final RandomSource randomSource, final long seed,
                               final int packedOverlay, @Nullable final RenderType renderType) {
        final ModelData modelData = bakedModel.getModelData(
                blockAndTintGetter, pos, blockState, ModelData.EMPTY);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().tesselateWithoutAO(
                blockAndTintGetter, bakedModel, blockState, pos, poseStack, vertexConsumer, true, randomSource, seed,
                packedOverlay, modelData, renderType);
    }

    @Override
    public List<RenderType> getRenderLayers(final SingleBlockSubLevelWrapper blockAndTintGetter,
                                            final BakedModel bakedModel, final BlockState blockState,
                                            final BlockPos pos, final RandomSource randomSource) {
        final ModelData modelData = bakedModel.getModelData(
                blockAndTintGetter, pos, blockState, ModelData.EMPTY);
        return bakedModel.getRenderTypes(blockState, randomSource, modelData).asList();
    }

    @Override
    public void tryAddFlywheelVisual(final BlockEntity blockEntity) {
        // Flywheel 0.6 visual registration is deferred; vanilla single-block rendering remains available.
    }
}
