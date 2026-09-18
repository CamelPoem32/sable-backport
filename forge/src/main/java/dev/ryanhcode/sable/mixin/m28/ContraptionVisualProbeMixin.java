package dev.ryanhcode.sable.mixin.m28;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import dev.engine_room.flywheel.lib.model.baked.ForgeBlockModelBuilder;
import dev.ryanhcode.sable.forge.SableM28FlywheelVisualTrace;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28NormalWorldCceSync;
import dev.ryanhcode.sable.forge.SableM28ControlledSailFlywheelTrace;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes Create 6.0.8's independent Flywheel model and embedding transform. */
@Mixin(value = ContraptionVisual.class, remap = false)
public abstract class ContraptionVisualProbeMixin<E extends AbstractContraptionEntity> {
    @Shadow
    protected TransformedInstance structure;

    @Shadow
    @Final
    private PoseStack contraptionMatrix;

    @Shadow @Final private VisualEmbedding embedding;

    @Inject(method = "<init>(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;"
                    + "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;F)V",
            at = @At("RETURN"))
    private void sable$traceM28FlywheelCreate(final VisualizationContext context, final E entity,
                                               final float partialTick, final CallbackInfo ci) {
        SableM28FlywheelVisualTrace.logCreate(entity, this, context, partialTick, this.structure);
        SableM28NormalWorldCceSync.visualCreated(entity, this);
        SableM28ControlledSailFlywheelTrace.lifecycle(entity, this, "CREATE");
        SableM28ControlledSailFlywheelTrace.structureInstance(entity, this, this.structure);
    }

    @WrapOperation(method = "setupStructure(Lcom/simibubi/create/content/contraptions/render/ClientContraption;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/engine_room/flywheel/lib/model/baked/ForgeBlockModelBuilder;"
                            + "build()Ldev/engine_room/flywheel/lib/model/SimpleModel;"))
    private SimpleModel sable$traceM28FlywheelStructure(final ForgeBlockModelBuilder builder,
                                                        final Operation<SimpleModel> original) {
        final SimpleModel model = original.call(builder);
        SableM28FlywheelVisualTrace.logStructure(this.sable$entity(), this, builder, model);
        SableM28ControlledSailFlywheelTrace.lifecycle(this.sable$entity(), this, "SETUP_STRUCTURE");
        SableM28ControlledSailFlywheelTrace.structure(this.sable$entity(), this, builder, model);
        return model;
    }

    @Inject(method = "setEmbeddingMatrices(F)V", at = @At("RETURN"))
    private void sable$traceM28FlywheelTransform(final float partialTick, final CallbackInfo ci) {
        if (this.sable$entity() instanceof final ControlledContraptionEntity controlled) {
            final Vec3i renderOrigin = ((AbstractVisualAccessor) this).sable$invokeRenderOrigin();
            SableM28FlywheelVisualTrace.logTransform(controlled, this, partialTick, renderOrigin,
                    new org.joml.Matrix4f(this.contraptionMatrix.last().pose()), this.structure);
        }
    }

    @Inject(method = "setEmbeddingMatrices(F)V", at = @At("HEAD"))
    private void sable$beginNormalWorldEmbeddingTrace(final float partialTick, final CallbackInfo ci) {
        if (!SableM28ControlledSailFlywheelTrace.enabled()) { return; }
        SableM28ControlledSailFlywheelTrace.begin(this.sable$entity(), this, this.embedding, partialTick,
                ((AbstractVisualAccessor) this).sable$invokeRenderOrigin());
    }

    @Inject(method = "_delete()V", at = @At("HEAD"))
    private void sable$traceM28FlywheelDelete(final CallbackInfo ci) {
        SableM28FlywheelVisualTrace.logDelete(this.sable$entity(), this, this.structure);
        SableM28ControlledSailFlywheelTrace.lifecycle(this.sable$entity(), this, "DELETE");
    }

    @SuppressWarnings("unchecked")
    private E sable$entity() {
        return (E) ((AbstractEntityVisualAccessor) this).sable$getEntity();
    }
}
