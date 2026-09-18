package dev.ryanhcode.sable.mixin.m28;

import dev.engine_room.flywheel.backend.engine.embed.EmbeddedEnvironment;
import dev.ryanhcode.sable.forge.SableM28ControlledSailFlywheelTrace;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads only embeddings registered by the opt-in controlled-sail trace. */
@Mixin(value = EmbeddedEnvironment.class, remap = false)
public abstract class EmbeddedEnvironmentProbeMixin {
    @Shadow @Final private Matrix4f pose;
    @Shadow @Final private Matrix4f poseComposed;
    @Shadow public int matrixIndex;

    @Inject(method = "transforms(Lorg/joml/Matrix4fc;Lorg/joml/Matrix3fc;)V", at = @At("HEAD"))
    private void sable$beforeStoredTransform(final org.joml.Matrix4fc matrix,
                                             final org.joml.Matrix3fc normal, final CallbackInfo ci) {
        if (SableM28ControlledSailFlywheelTrace.enabled()) {
            SableM28ControlledSailFlywheelTrace.storedBefore(this, this.pose);
        }
    }

    @Inject(method = "transforms(Lorg/joml/Matrix4fc;Lorg/joml/Matrix3fc;)V", at = @At("RETURN"))
    private void sable$afterStoredTransform(final org.joml.Matrix4fc matrix,
                                            final org.joml.Matrix3fc normal, final CallbackInfo ci) {
        if (SableM28ControlledSailFlywheelTrace.enabled()) {
            SableM28ControlledSailFlywheelTrace.storedAfter(this, this.pose);
        }
    }

    @Inject(method = "flush(J)V", at = @At("RETURN"))
    private void sable$embeddingStaged(final long pointer, final CallbackInfo ci) {
        if (SableM28ControlledSailFlywheelTrace.enabled()) {
            SableM28ControlledSailFlywheelTrace.staged(this, this.matrixIndex, this.poseComposed, pointer);
        }
    }

    @Inject(method = "setupDraw(Ldev/engine_room/flywheel/backend/gl/shader/GlProgram;)V", at = @At("RETURN"))
    private void sable$embeddingUniformSubmitted(final dev.engine_room.flywheel.backend.gl.shader.GlProgram program,
                                                   final CallbackInfo ci) {
        if (SableM28ControlledSailFlywheelTrace.enabled()) {
            SableM28ControlledSailFlywheelTrace.uniformSubmitted(this, this.poseComposed, program);
        }
    }
}
