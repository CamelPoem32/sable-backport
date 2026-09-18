package dev.ryanhcode.sable.mixin.m28;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.forge.SableForgeCreateContraptionRenderBridge;
import dev.ryanhcode.sable.forge.SableM28VisualOwnershipTrace;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Provides a property-gated comparison against the real vanilla/Oculus entity-buffer phase. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererEntityPhaseProbeMixin {
    @Unique
    private final ThreadLocal<SableM28VisualOwnershipTrace.Scope> sable$m28VanillaEntityScope = new ThreadLocal<>();

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void sable$beginM28RenderFrame(final PoseStack poseStack, final float partialTick,
                                           final long finishTimeNano, final boolean renderBlockOutline,
                                           final Camera camera, final GameRenderer gameRenderer,
                                           final LightTexture lightTexture, final Matrix4f projection,
                                           final CallbackInfo ci) {
        SableForgeCreateContraptionRenderBridge.beginRenderFrame();
    }

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void sable$renderContainedContraptionsInEntityPhase(final Entity entity,
                                                                final double cameraX,
                                                                final double cameraY,
                                                                final double cameraZ,
                                                                final float partialTick,
                                                                final PoseStack poseStack,
                                                                final MultiBufferSource bufferSource,
                                                                final CallbackInfo ci) {
        this.sable$m28VanillaEntityScope.set(SableM28VisualOwnershipTrace.enter(
                SableM28VisualOwnershipTrace.Owner.VANILLA_LEVEL_ENTITY_PASS));
        try {
            SableForgeCreateContraptionRenderBridge.renderEntityPhase(
                    cameraX, cameraY, cameraZ, partialTick, poseStack, bufferSource);
        } catch (final RuntimeException | Error failure) {
            this.sable$closeM28VanillaEntityScope();
            throw failure;
        }
    }

    @Inject(method = "renderEntity", at = @At("RETURN"))
    private void sable$endVanillaEntityRenderContext(final Entity entity,
                                                      final double cameraX,
                                                      final double cameraY,
                                                      final double cameraZ,
                                                      final float partialTick,
                                                      final PoseStack poseStack,
                                                      final MultiBufferSource bufferSource,
                                                      final CallbackInfo ci) {
        this.sable$closeM28VanillaEntityScope();
    }

    @Unique
    private void sable$closeM28VanillaEntityScope() {
        final SableM28VisualOwnershipTrace.Scope scope = this.sable$m28VanillaEntityScope.get();
        if (scope != null) {
            scope.close();
            this.sable$m28VanillaEntityScope.remove();
        }
    }
}
