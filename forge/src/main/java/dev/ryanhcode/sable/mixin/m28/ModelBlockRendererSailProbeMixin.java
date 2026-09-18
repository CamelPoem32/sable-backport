package dev.ryanhcode.sable.mixin.m28;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.forge.SableM28VisualOwnershipTrace;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Identifies every low-level baked-model path that tessellates the M28 symmetric sail. */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererSailProbeMixin {
    @Inject(method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "Lnet/minecraft/client/resources/model/BakedModel;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Z"
                    + "Lnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;"
                    + "Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"), remap = false)
    private void sable$traceSailTesselateBlock(final BlockAndTintGetter level, final BakedModel model,
                                               final BlockState state, final BlockPos pos,
                                               final PoseStack poseStack, final VertexConsumer consumer,
                                               final boolean checkSides, final RandomSource random,
                                               final long seed, final int overlay,
                                               final ModelData modelData, final RenderType renderType,
                                               final CallbackInfo ci) {
        SableM28VisualOwnershipTrace.logSailModelDraw(
                "ModelBlockRenderer.tesselateBlock", state, pos, level, poseStack.last(), consumer, renderType);
    }

    @Inject(method = "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;"
                    + "Lnet/minecraft/client/resources/model/BakedModel;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Z"
                    + "Lnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;"
                    + "Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"), remap = false)
    private void sable$traceSailTesselateWithoutAo(final BlockAndTintGetter level, final BakedModel model,
                                                   final BlockState state, final BlockPos pos,
                                                   final PoseStack poseStack, final VertexConsumer consumer,
                                                   final boolean checkSides, final RandomSource random,
                                                   final long seed, final int overlay,
                                                   final ModelData modelData, final RenderType renderType,
                                                   final CallbackInfo ci) {
        SableM28VisualOwnershipTrace.logSailModelDraw(
                "ModelBlockRenderer.tesselateWithoutAO", state, pos, level, poseStack.last(), consumer, renderType);
    }

    @Inject(method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/client/resources/model/BakedModel;FFFIILnet/minecraftforge/client/model/data/ModelData;"
                    + "Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"), remap = false)
    private void sable$traceSailRenderModel(final PoseStack.Pose pose, final VertexConsumer consumer,
                                            final BlockState state, final BakedModel model,
                                            final float red, final float green, final float blue,
                                            final int packedLight, final int packedOverlay,
                                            final ModelData modelData, final RenderType renderType,
                                            final CallbackInfo ci) {
        SableM28VisualOwnershipTrace.logSailModelDraw(
                "ModelBlockRenderer.renderModel", state, null, null, pose, consumer, renderType);
    }
}
